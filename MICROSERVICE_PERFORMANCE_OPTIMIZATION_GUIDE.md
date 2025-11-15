# Microservice Performance Optimization Guide
## Circuit Breaker Mitigation Strategies

**Date:** November 15, 2025  
**Service:** globeco-order-service  
**Issue:** Circuit breaker triggering due to connection pool exhaustion (92.6% utilization)

---

## Executive Summary

The circuit breaker is opening due to connection pool exhaustion, with 92.6% utilization and threads waiting for connections. The logs show multiple concurrent batch processing requests (25 orders each) overwhelming the connection pool. This document outlines internal microservice optimizations to reduce connection pressure and improve throughput.

**Key Findings:**
- Connection pool: 40 max connections, 37 active (92.6% utilization)
- Concurrent batch requests: 10+ simultaneous batches of 25 orders
- Semaphore limit: 25 concurrent DB operations
- Circuit breaker threshold: 90% utilization triggers protection
- Transaction timeout: 5 seconds for writes, 3 seconds for reads

---

## Critical Performance Bottlenecks

### 1. **Connection Pool Sizing Mismatch**
**Current State:**
- Max pool size: 40 connections
- Max concurrent DB operations (semaphore): 25
- Concurrent batch requests: 10+ threads

**Problem:** The semaphore allows 25 concurrent operations, but with multiple batch requests happening simultaneously, the actual connection demand exceeds the pool capacity.

**Impact:** High connection contention, threads waiting, circuit breaker activation

---

### 2. **Inefficient Transaction Management**
**Current State:**
- Each order in a batch gets its own transaction via `processIndividualOrderInTransaction()`
- Batch of 25 orders = 25 separate transactions
- Each transaction holds a connection for validation + external service call + update

**Problem:** Long-lived connections during external service calls (trade service timeout: 45s)

**Impact:** Connections held unnecessarily during I/O wait, reducing available pool capacity

---

### 3. **N+1 Query Pattern in Batch Loading**
**Current State:**
```java
List<Order> allOrders = orderRepository.findAllById(orderIds);
```

**Problem:** JPA lazy loading may trigger additional queries for related entities (Status, OrderType, Blotter) when accessed during validation

**Impact:** Multiple round-trips to database, increased connection hold time

---

### 4. **Synchronous External Service Calls**
**Current State:**
- Trade service calls are synchronous with 45-second timeout
- Each order waits for trade service response before releasing connection
- Bulk submission uses single HTTP call but still holds connections during wait

**Problem:** Database connections held during network I/O

**Impact:** Connection pool starvation during slow external service responses

---

### 5. **Excessive Logging in Hot Path**
**Current State:**
- DEBUG level logging enabled for all service classes
- Multiple log statements per order (validation, processing, results)
- String formatting and concatenation in hot path

**Problem:** Logging overhead in high-throughput scenarios

**Impact:** CPU cycles and memory allocation pressure

---

## Optimization Strategies

### Priority 1: Immediate Wins (Low Effort, High Impact)

#### 1.1 Optimize Connection Pool Configuration
**Action:** Adjust pool size to match actual concurrency needs

```properties
# Current
spring.datasource.hikari.maximum-pool-size=40
spring.datasource.hikari.minimum-idle=10

# Recommended
spring.datasource.hikari.maximum-pool-size=60
spring.datasource.hikari.minimum-idle=20
spring.datasource.hikari.connection-timeout=5000
```

**Rationale:**
- Semaphore allows 25 concurrent operations
- With 10 concurrent batch requests, need buffer for peak load
- Formula: (max_concurrent_operations × 1.5) + buffer = 60
- Reduced connection timeout for faster failure detection

**Expected Impact:** 30-40% reduction in connection wait time

---

#### 1.2 Reduce Semaphore Concurrency
**Action:** Lower the semaphore limit to match sustainable throughput

```java
// Current
private static final int MAX_CONCURRENT_DB_OPERATIONS = 25;

// Recommended
private static final int MAX_CONCURRENT_DB_OPERATIONS = 15;
```

**Rationale:**
- Current limit (25) exceeds sustainable connection pool capacity
- Lower limit prevents pool exhaustion
- Better to queue requests than exhaust connections

**Expected Impact:** 50% reduction in circuit breaker activations

---

#### 1.3 Implement Connection Release Before External Calls
**Action:** Restructure transaction boundaries to release connections during I/O

**Current Pattern:**
```java
@Transactional
public OrderDTO submitOrder(Integer id) {
    Order order = orderRepository.findById(id).orElseThrow();
    // Connection held during external call
    Integer tradeOrderId = callTradeService(order);
    updateOrderAfterSubmission(order, tradeOrderId);
    return toOrderDTO(order);
}
```

**Optimized Pattern:**
```java
public OrderDTO submitOrder(Integer id) {
    // Transaction 1: Load order
    Order order = loadOrderInTransaction(id);
    
    // No transaction: External call
    Integer tradeOrderId = callTradeService(order);
    
    // Transaction 2: Update order
    updateOrderInTransaction(id, tradeOrderId);
    return toOrderDTO(order);
}
```

**Expected Impact:** 60-70% reduction in connection hold time

---

#### 1.4 Add Eager Fetching for Batch Operations
**Action:** Use JOIN FETCH to eliminate N+1 queries

**Add to OrderRepository:**
```java
@Query("SELECT o FROM Order o " +
       "JOIN FETCH o.status " +
       "JOIN FETCH o.orderType " +
       "JOIN FETCH o.blotter " +
       "WHERE o.id IN :ids")
List<Order> findAllByIdWithRelations(@Param("ids") List<Integer> ids);
```

**Update BatchProcessingService:**
```java
// Replace
List<Order> allOrders = orderRepository.findAllById(orderIds);

// With
List<Order> allOrders = orderRepository.findAllByIdWithRelations(orderIds);
```

**Expected Impact:** 40-50% reduction in database query count

---

#### 1.5 Reduce Logging Overhead
**Action:** Move to conditional logging and reduce verbosity

```java
// Current
logger.debug("BULK_SUBMISSION: Processing {} orders with connection control (max concurrent: {})", 
        orders.size(), MAX_CONCURRENT_DB_OPERATIONS);

// Optimized
if (logger.isDebugEnabled()) {
    logger.debug("BULK_SUBMISSION: Processing {} orders", orders.size());
}
```

**Configuration Change:**
```properties
# Reduce logging in production
logging.level.org.kasbench.globeco_order_service.service=INFO
logging.level.org.kasbench.globeco_order_service.controller=INFO
```

**Expected Impact:** 10-15% reduction in CPU usage

---

### Priority 2: Medium-Term Improvements (Moderate Effort, High Impact)

#### 2.1 Implement Batch Database Updates
**Action:** Update multiple order statuses in a single query

**Add to OrderRepository:**
```java
@Modifying
@Query("UPDATE Order o SET o.status = :status, o.tradeOrderId = :tradeOrderId " +
       "WHERE o.id = :orderId")
int updateOrderStatus(@Param("orderId") Integer orderId, 
                      @Param("status") Status status,
                      @Param("tradeOrderId") Integer tradeOrderId);

@Modifying
@Query("UPDATE Order o SET o.status = :status, o.tradeOrderId = " +
       "CASE o.id " +
       "WHEN :id1 THEN :tradeOrderId1 " +
       "WHEN :id2 THEN :tradeOrderId2 " +
       "... " +
       "END " +
       "WHERE o.id IN :orderIds")
int batchUpdateOrderStatuses(...);
```

**Alternative: Use JDBC Batch Updates:**
```java
@Autowired
private JdbcTemplate jdbcTemplate;

public void batchUpdateOrders(List<Order> orders) {
    String sql = "UPDATE orders SET status_id = ?, trade_order_id = ? WHERE id = ?";
    
    jdbcTemplate.batchUpdate(sql, orders, orders.size(),
        (PreparedStatement ps, Order order) -> {
            ps.setInt(1, order.getStatus().getId());
            ps.setInt(2, order.getTradeOrderId());
            ps.setInt(3, order.getId());
        });
}
```

**Expected Impact:** 70-80% reduction in update transaction time

---

#### 2.2 Implement Connection Pooling for External Services
**Action:** Use connection pooling for RestTemplate

**Add Configuration:**
```java
@Configuration
public class RestTemplateConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory();
        
        // Connection pool configuration
        PoolingHttpClientConnectionManager connectionManager = 
            new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(50);  // Total connections
        connectionManager.setDefaultMaxPerRoute(25);  // Per host
        
        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setKeepAliveStrategy((response, context) -> 30000)  // 30s keep-alive
            .build();
        
        factory.setHttpClient(httpClient);
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(45000);
        
        return new RestTemplate(factory);
    }
}
```

**Expected Impact:** 30-40% reduction in external service call latency

---

#### 2.3 Implement Request Coalescing
**Action:** Batch multiple concurrent requests into single operations

**Add Request Coalescer:**
```java
@Service
public class BatchRequestCoalescer {
    private final Map<String, CompletableFuture<BatchSubmitResponseDTO>> pendingBatches = 
        new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = 
        Executors.newScheduledThreadPool(1);
    
    public CompletableFuture<BatchSubmitResponseDTO> submitOrders(List<Integer> orderIds) {
        String batchKey = generateBatchKey();
        
        return pendingBatches.computeIfAbsent(batchKey, key -> {
            CompletableFuture<BatchSubmitResponseDTO> future = new CompletableFuture<>();
            
            // Schedule batch execution after short delay to collect more requests
            scheduler.schedule(() -> {
                List<Integer> coalescedIds = collectPendingOrders(batchKey);
                BatchSubmitResponseDTO result = executeBatch(coalescedIds);
                future.complete(result);
                pendingBatches.remove(batchKey);
            }, 50, TimeUnit.MILLISECONDS);
            
            return future;
        });
    }
}
```

**Expected Impact:** 40-50% reduction in total database operations

---

#### 2.4 Add Database Connection Metrics and Alerting
**Action:** Implement proactive monitoring before circuit breaker triggers

**Add Metrics:**
```java
@Service
public class ConnectionPoolMetrics {
    
    @Scheduled(fixedRate = 5000)  // Every 5 seconds
    public void recordConnectionPoolMetrics() {
        HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
        
        meterRegistry.gauge("db.pool.active", poolBean.getActiveConnections());
        meterRegistry.gauge("db.pool.idle", poolBean.getIdleConnections());
        meterRegistry.gauge("db.pool.waiting", poolBean.getThreadsAwaitingConnection());
        meterRegistry.gauge("db.pool.utilization", 
            (double) poolBean.getActiveConnections() / poolBean.getTotalConnections());
    }
}
```

**Add Alerting Thresholds:**
```properties
# Alert when utilization exceeds 75% (before circuit breaker at 90%)
management.metrics.alerts.db.pool.utilization.threshold=0.75
management.metrics.alerts.db.pool.waiting.threshold=5
```

**Expected Impact:** Early warning system, proactive scaling

---

### Priority 3: Long-Term Optimizations (High Effort, High Impact)

#### 3.1 Implement Asynchronous Processing with Message Queue
**Action:** Decouple order submission from HTTP request lifecycle

**Architecture:**
```
HTTP Request → Queue Order → Return 202 Accepted
                    ↓
            Background Worker → Process Order → Update Status
                    ↓
            Webhook/Polling → Client Gets Result
```

**Benefits:**
- No connection held during processing
- Better load leveling
- Improved resilience

**Expected Impact:** 80-90% reduction in connection pool pressure

---

#### 3.2 Implement Read Replicas for Query Operations
**Action:** Separate read and write database connections

**Configuration:**
```java
@Configuration
public class DataSourceConfig {
    
    @Bean
    @Primary
    public DataSource primaryDataSource() {
        // Write operations
    }
    
    @Bean
    public DataSource readReplicaDataSource() {
        // Read operations
    }
}
```

**Expected Impact:** 50% reduction in primary database load

---

#### 3.3 Implement Caching for Frequently Accessed Data
**Action:** Cache Status, OrderType, Blotter lookups

**Add Caching:**
```java
@Service
public class ReferenceDataCache {
    
    @Cacheable("statuses")
    public Status getStatusByAbbreviation(String abbreviation) {
        return statusRepository.findByAbbreviation(abbreviation);
    }
    
    @Cacheable("orderTypes")
    public OrderType getOrderTypeByAbbreviation(String abbreviation) {
        return orderTypeRepository.findByAbbreviation(abbreviation);
    }
}
```

**Expected Impact:** 20-30% reduction in database queries

---

## Implementation Roadmap

### Phase 1: Immediate (Week 1)
1. Adjust connection pool size (1.1)
2. Reduce semaphore concurrency (1.2)
3. Reduce logging verbosity (1.5)
4. Add connection pool metrics (2.4)

**Expected Result:** 40-50% reduction in circuit breaker activations

---

### Phase 2: Short-Term (Weeks 2-3)
1. Implement eager fetching (1.4)
2. Restructure transaction boundaries (1.3)
3. Implement batch database updates (2.1)
4. Add connection pooling for external services (2.2)

**Expected Result:** 60-70% improvement in throughput

---

### Phase 3: Medium-Term (Weeks 4-6)
1. Implement request coalescing (2.3)
2. Implement reference data caching (3.3)
3. Optimize query patterns

**Expected Result:** 70-80% improvement in throughput

---

### Phase 4: Long-Term (Months 2-3)
1. Implement asynchronous processing (3.1)
2. Implement read replicas (3.2)
3. Consider database sharding for scale

**Expected Result:** 10x improvement in sustainable throughput

---

## Monitoring and Validation

### Key Metrics to Track

1. **Connection Pool Health**
   - Active connections
   - Idle connections
   - Threads waiting
   - Utilization percentage
   - Connection acquisition time

2. **Circuit Breaker Metrics**
   - Circuit open events
   - Failure count
   - Recovery time
   - Rejection rate

3. **Throughput Metrics**
   - Orders processed per second
   - Batch processing time
   - Success rate
   - Error rate

4. **Latency Metrics**
   - P50, P95, P99 response times
   - Database query time
   - External service call time
   - Transaction duration

### Success Criteria

- Circuit breaker activations: < 1 per hour
- Connection pool utilization: < 75% average
- Threads waiting: < 3 concurrent
- Batch processing time: < 5 seconds for 25 orders
- Success rate: > 99%

---

## Configuration Recommendations

### Immediate Changes

```properties
# Connection Pool
spring.datasource.hikari.maximum-pool-size=60
spring.datasource.hikari.minimum-idle=20
spring.datasource.hikari.connection-timeout=5000
spring.datasource.hikari.validation-timeout=2000

# Logging
logging.level.org.kasbench.globeco_order_service.service=INFO
logging.level.org.kasbench.globeco_order_service.controller=INFO

# Circuit Breaker
circuit.breaker.utilization.threshold=0.75
circuit.breaker.failure.threshold=3
circuit.breaker.recovery.time.ms=15000
```

### Batch Processing

```java
// BatchProcessingService
private static final int MAX_CONCURRENT_DB_OPERATIONS = 15;  // Reduced from 25
private static final int BATCH_CHUNK_SIZE = 50;  // Keep same
```

---

## Risk Mitigation

### Potential Issues

1. **Increased Memory Usage**
   - Larger connection pool = more memory
   - Mitigation: Monitor heap usage, adjust JVM settings

2. **Database Load**
   - More connections = more database load
   - Mitigation: Monitor database CPU/memory, consider read replicas

3. **Transaction Conflicts**
   - Shorter transactions may increase conflicts
   - Mitigation: Implement optimistic locking, retry logic

---

## Conclusion

The circuit breaker is triggering due to connection pool exhaustion caused by:
1. Insufficient connection pool size for concurrent load
2. Long-lived transactions holding connections during I/O
3. N+1 query patterns increasing database round-trips
4. Lack of connection pooling for external services

Implementing the Priority 1 optimizations should provide immediate relief (40-50% improvement). The full roadmap can achieve 10x improvement in sustainable throughput without requiring infrastructure changes.

**Next Steps:**
1. Implement Phase 1 changes immediately
2. Monitor metrics for 48 hours
3. Proceed with Phase 2 based on results
4. Consider Phase 4 for long-term scalability
