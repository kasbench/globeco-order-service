# Design Document

## Overview

This design implements comprehensive connection pool optimization for the Order Service to eliminate circuit breaker activations caused by connection pool exhaustion. The current system experiences 92.6% connection pool utilization with threads waiting for connections during concurrent batch processing. This design addresses the root causes through configuration adjustments, transaction boundary optimization, query improvements, and enhanced monitoring.

The solution focuses on internal microservice optimizations that can be implemented without infrastructure changes, targeting 60-70% improvement in throughput through Priority 1 and Priority 2 optimizations from the performance guide.

## Architecture

### Current Architecture Issues

1. **Connection Pool Sizing**: 40 max connections with 25 concurrent DB operations semaphore
2. **Transaction Management**: Long-lived transactions holding connections during external service calls (45s timeout)
3. **Query Patterns**: N+1 queries during batch order loading
4. **External Service Calls**: No connection pooling for HTTP clients
5. **Monitoring**: Reactive circuit breaker at 90% utilization without proactive alerts

### Optimized Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Order Controller                          │
│                  (HTTP Request Entry)                        │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              Batch Processing Service                        │
│  • Reduced Semaphore (15 concurrent ops)                    │
│  • Queue Management                                          │
│  • Metrics Collection                                        │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                   Order Service                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Transaction 1: Load Orders (Short)                  │   │
│  │  • Eager fetch with JOIN FETCH                       │   │
│  │  • Read-only transaction (3s timeout)                │   │
│  │  • Release connection immediately                    │   │
│  └─────────────────────────────────────────────────────┘   │
│                       │                                      │
│                       ▼                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  External Service Call (No Connection)               │   │
│  │  • Pooled HTTP Client                                │   │
│  │  • Connection reuse                                  │   │
│  │  • Keep-alive enabled                                │   │
│  └─────────────────────────────────────────────────────┘   │
│                       │                                      │
│                       ▼                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Transaction 2: Update Orders (Short)                │   │
│  │  • JDBC batch updates                                │   │
│  │  • Write transaction (5s timeout)                    │   │
│  │  • Release connection immediately                    │   │
│  └─────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              HikariCP Connection Pool                        │
│  • Max Pool Size: 60 connections                            │
│  • Min Idle: 20 connections                                 │
│  • Connection Timeout: 5 seconds                            │
│  • Validation Timeout: 2 seconds                            │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              Connection Pool Monitor                         │
│  • Metrics every 5 seconds                                  │
│  • Alert at 75% utilization                                 │
│  • Track wait times and queue depth                        │
└─────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. Connection Pool Configuration

**File**: `src/main/resources/application.yml`

**Changes**:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 60  # Increased from 40
      minimum-idle: 20       # Increased from 10
      connection-timeout: 5000
      validation-timeout: 2000
      leak-detection-threshold: 30000
```

**Rationale**: Formula (max_concurrent_operations × 1.5) + buffer = 60 provides headroom for 15 concurrent operations with multiple batch requests.

### 2. Batch Processing Service Updates

**File**: `src/main/java/org/kasbench/globeco_order_service/service/BatchProcessingService.java`

**Interface Changes**:
```java
public class BatchProcessingService {
    private static final int MAX_CONCURRENT_DB_OPERATIONS = 15; // Reduced from 25
    private static final int BATCH_CHUNK_SIZE = 50;
    
    // New metrics
    private final Counter semaphoreWaitCounter;
    private final Timer semaphoreWaitTimer;
    private final Gauge semaphoreAvailableGauge;
    
    public int getAvailablePermits();
    public int getQueueLength();
    public long getAverageWaitTime();
}
```

**Key Changes**:
- Reduce semaphore from 25 to 15 concurrent operations
- Add metrics for semaphore wait times and queue depth
- Expose monitoring methods for health checks

### 3. Order Repository Enhancements

**File**: `src/main/java/org/kasbench/globeco_order_service/repository/OrderRepository.java`

**New Methods**:
```java
public interface OrderRepository extends JpaRepository<Order, Integer> {
    
    @Query("SELECT o FROM Order o " +
           "JOIN FETCH o.status " +
           "JOIN FETCH o.orderType " +
           "JOIN FETCH o.blotter " +
           "WHERE o.id IN :ids")
    List<Order> findAllByIdWithRelations(@Param("ids") List<Integer> ids);
    
    @Modifying
    @Query("UPDATE Order o SET o.status = :status, o.tradeOrderId = :tradeOrderId " +
           "WHERE o.id = :orderId")
    int updateOrderStatus(@Param("orderId") Integer orderId, 
                          @Param("status") Status status,
                          @Param("tradeOrderId") Integer tradeOrderId);
}
```

**Purpose**: Eliminate N+1 queries with eager fetching and enable efficient batch updates.

### 4. Order Service Transaction Restructuring

**File**: `src/main/java/org/kasbench/globeco_order_service/service/OrderService.java`

**Current Pattern** (Problematic):
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

**Optimized Pattern**:
```java
public OrderDTO submitOrder(Integer id) {
    // Transaction 1: Load order (short)
    Order order = loadOrderInReadTransaction(id);
    
    // No transaction: External call
    Integer tradeOrderId = callTradeService(order);
    
    // Transaction 2: Update order (short)
    updateOrderInWriteTransaction(id, tradeOrderId);
    return toOrderDTO(order);
}

@Transactional(readOnly = true, timeout = 3)
private Order loadOrderInReadTransaction(Integer id) {
    return orderRepository.findById(id).orElseThrow();
}

@Transactional(timeout = 5)
private void updateOrderInWriteTransaction(Integer id, Integer tradeOrderId) {
    Order order = orderRepository.findById(id).orElseThrow();
    order.setTradeOrderId(tradeOrderId);
    order.setStatus(getSentStatus());
    orderRepository.save(order);
}
```

**Key Changes**:
- Split single long transaction into two short transactions
- Use read-only transaction for loading (3s timeout)
- Use write transaction only for updates (5s timeout)
- Release connection during external service call

### 5. Batch Update Optimization

**New Service**: `src/main/java/org/kasbench/globeco_order_service/service/BatchUpdateService.java`

```java
@Service
public class BatchUpdateService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Transactional(timeout = 5)
    public int batchUpdateOrderStatuses(List<Order> orders, Status sentStatus) {
        String sql = "UPDATE orders SET status_id = ?, trade_order_id = ?, version = version + 1 " +
                     "WHERE id = ? AND version = ?";
        
        int[][] updateCounts = jdbcTemplate.batchUpdate(
            sql,
            orders,
            orders.size(),
            (PreparedStatement ps, Order order) -> {
                ps.setInt(1, sentStatus.getId());
                ps.setInt(2, order.getTradeOrderId());
                ps.setInt(3, order.getId());
                ps.setInt(4, order.getVersion());
            }
        );
        
        return Arrays.stream(updateCounts).flatMapToInt(Arrays::stream).sum();
    }
}
```

**Purpose**: Replace individual order updates with single batch operation, reducing transaction overhead by 70-80%.

### 6. HTTP Client Configuration

**File**: `src/main/java/org/kasbench/globeco_order_service/config/HttpClientConfiguration.java`

**Enhanced Configuration**:
```java
@Configuration
public class HttpClientConfiguration {
    
    @Bean
    public PoolingHttpClientConnectionManager connectionManager() {
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
        manager.setMaxTotal(50);
        manager.setDefaultMaxPerRoute(25);
        return manager;
    }
    
    @Bean
    public RestTemplate restTemplate(PoolingHttpClientConnectionManager connectionManager) {
        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setKeepAliveStrategy((response, context) -> 30000) // 30s keep-alive
            .build();
        
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(45000);
        
        return new RestTemplate(factory);
    }
}
```

**Purpose**: Enable connection pooling and reuse for external service calls, reducing connection establishment overhead.

### 7. Connection Pool Monitoring Service

**New Service**: `src/main/java/org/kasbench/globeco_order_service/service/ConnectionPoolMonitoringService.java`

```java
@Service
public class ConnectionPoolMonitoringService {
    
    @Autowired
    private HikariDataSource hikariDataSource;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    private final Gauge activeConnectionsGauge;
    private final Gauge idleConnectionsGauge;
    private final Gauge threadsAwaitingGauge;
    private final Gauge utilizationGauge;
    private final Counter highUtilizationCounter;
    
    @Scheduled(fixedRate = 5000) // Every 5 seconds
    public void recordConnectionPoolMetrics() {
        HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
        
        int active = poolBean.getActiveConnections();
        int idle = poolBean.getIdleConnections();
        int waiting = poolBean.getThreadsAwaitingConnection();
        int total = poolBean.getTotalConnections();
        double utilization = (double) active / total;
        
        // Record metrics
        meterRegistry.gauge("db.pool.active", active);
        meterRegistry.gauge("db.pool.idle", idle);
        meterRegistry.gauge("db.pool.waiting", waiting);
        meterRegistry.gauge("db.pool.utilization", utilization);
        
        // Alert on high utilization
        if (utilization > 0.75) {
            highUtilizationCounter.increment();
            logger.warn("High connection pool utilization: {:.2f}% ({}/{})", 
                       utilization * 100, active, total);
        }
    }
    
    public ConnectionPoolHealth getHealth() {
        HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
        return ConnectionPoolHealth.builder()
            .active(poolBean.getActiveConnections())
            .idle(poolBean.getIdleConnections())
            .waiting(poolBean.getThreadsAwaitingConnection())
            .total(poolBean.getTotalConnections())
            .utilization((double) poolBean.getActiveConnections() / poolBean.getTotalConnections())
            .build();
    }
}
```

**Purpose**: Proactive monitoring with alerts at 75% utilization, before circuit breaker threshold of 90%.

### 8. Circuit Breaker Configuration Updates

**File**: `src/main/java/org/kasbench/globeco_order_service/service/ConnectionPoolCircuitBreaker.java`

**Configuration Changes**:
```java
@Component
public class ConnectionPoolCircuitBreaker {
    private static final double UTILIZATION_THRESHOLD = 0.75; // Reduced from 0.90
    private static final int FAILURE_THRESHOLD = 3;
    private static final long RECOVERY_TIME_MS = 15000; // 15 seconds
    
    // Existing implementation with updated thresholds
}
```

**Purpose**: Align circuit breaker with new connection pool capacity and provide earlier protection.

### 9. Logging Configuration

**File**: `src/main/resources/application.yml`

**Changes**:
```yaml
logging:
  level:
    root: INFO
    org.kasbench.globeco_order_service.service: INFO  # Changed from DEBUG
    org.kasbench.globeco_order_service.controller: INFO  # Changed from DEBUG
```

**Code Changes**: Add conditional logging checks
```java
// Before
logger.debug("Processing {} orders", orders.size());

// After
if (logger.isDebugEnabled()) {
    logger.debug("Processing {} orders", orders.size());
}
```

**Purpose**: Reduce logging overhead in production, saving 10-15% CPU usage.

## Data Models

### Connection Pool Health Model

```java
@Data
@Builder
public class ConnectionPoolHealth {
    private int active;
    private int idle;
    private int waiting;
    private int total;
    private double utilization;
    private long timestamp;
    
    public boolean isHealthy() {
        return utilization < 0.75 && waiting < 3;
    }
    
    public String getStatus() {
        if (utilization >= 0.90) return "CRITICAL";
        if (utilization >= 0.75) return "WARNING";
        return "HEALTHY";
    }
}
```

### Batch Processing Metrics Model

```java
@Data
@Builder
public class BatchProcessingMetrics {
    private int totalOrders;
    private int successfulOrders;
    private int failedOrders;
    private long loadDurationMs;
    private long tradeServiceDurationMs;
    private long updateDurationMs;
    private long totalDurationMs;
    private double successRate;
    private double avgTimePerOrder;
    private int semaphoreWaitCount;
    private long avgSemaphoreWaitMs;
}
```

## Error Handling

### Connection Timeout Handling

```java
@Transactional(timeout = 5)
public void updateOrders(List<Order> orders) {
    try {
        batchUpdateService.batchUpdateOrderStatuses(orders, sentStatus);
    } catch (TransactionTimedOutException e) {
        logger.error("Transaction timeout during batch update: {} orders", orders.size());
        // Retry with smaller batch
        retryWithSmallerBatch(orders);
    } catch (CannotGetJdbcConnectionException e) {
        logger.error("Cannot acquire database connection");
        throw new SystemOverloadException("Database connection pool exhausted");
    }
}
```

### Semaphore Timeout Handling

```java
if (!dbOperationSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
    logger.warn("Semaphore timeout for order at index {}", orderIndex);
    semaphoreTimeoutCounter.increment();
    return OrderPostResponseDTO.failure(
        "System overloaded - please retry", orderIndex);
}
```

### Circuit Breaker Integration

```java
if (!circuitBreaker.allowOperation()) {
    logger.error("Circuit breaker OPEN - rejecting batch");
    return BatchSubmitResponseDTO.validationFailure(
        "System temporarily overloaded - please retry in a few minutes");
}
```

## Testing Strategy

### Unit Tests

1. **Connection Pool Configuration Test**
   - Verify pool size settings
   - Verify timeout configurations
   - Test connection acquisition

2. **Transaction Boundary Test**
   - Verify short transaction durations
   - Test connection release between transactions
   - Validate read-only vs write transactions

3. **Batch Update Test**
   - Test JDBC batch update execution
   - Verify update counts
   - Test rollback on failure

4. **Eager Fetching Test**
   - Verify single query execution
   - Test JOIN FETCH behavior
   - Validate no N+1 queries

### Integration Tests

1. **Connection Pool Stress Test**
   - Simulate concurrent batch requests
   - Measure connection pool utilization
   - Verify no connection exhaustion

2. **End-to-End Batch Processing Test**
   - Test complete batch submission flow
   - Measure transaction durations
   - Verify metrics collection

3. **Circuit Breaker Integration Test**
   - Test circuit breaker activation
   - Verify recovery behavior
   - Test alert generation

### Performance Tests

1. **Throughput Test**
   - Measure orders per second
   - Compare before/after optimization
   - Target: 60-70% improvement

2. **Latency Test**
   - Measure P50, P95, P99 response times
   - Test under various load levels
   - Target: < 5 seconds for 25 orders

3. **Connection Pool Utilization Test**
   - Monitor utilization under load
   - Target: < 75% average utilization
   - Target: < 3 threads waiting

## Performance Metrics

### Key Performance Indicators

1. **Connection Pool Metrics**
   - `db.pool.active`: Active connections
   - `db.pool.idle`: Idle connections
   - `db.pool.waiting`: Threads waiting
   - `db.pool.utilization`: Utilization percentage

2. **Batch Processing Metrics**
   - `bulk_submission.duration`: Total processing time
   - `bulk_submission.order_load.duration`: Order loading time
   - `bulk_submission.trade_service.duration`: External service call time
   - `bulk_submission.database_update.duration`: Database update time
   - `bulk_submission.success`: Success counter
   - `bulk_submission.failure`: Failure counter

3. **Semaphore Metrics**
   - `semaphore.available_permits`: Available permits
   - `semaphore.queue_length`: Queue depth
   - `semaphore.wait_time`: Wait time distribution

4. **Circuit Breaker Metrics**
   - `circuit_breaker.state`: Current state (OPEN/CLOSED/HALF_OPEN)
   - `circuit_breaker.failures`: Failure count
   - `circuit_breaker.rejections`: Rejection count

### Success Criteria

- Circuit breaker activations: < 1 per hour
- Connection pool utilization: < 75% average
- Threads waiting: < 3 concurrent
- Batch processing time: < 5 seconds for 25 orders
- Success rate: > 99%
- Throughput improvement: 60-70% increase

## Implementation Phases

### Phase 1: Configuration and Monitoring (Week 1)
- Update connection pool configuration
- Reduce semaphore concurrency
- Implement connection pool monitoring
- Update logging configuration
- Deploy and monitor for 48 hours

### Phase 2: Transaction Optimization (Week 2)
- Implement eager fetching in OrderRepository
- Restructure transaction boundaries in OrderService
- Add conditional logging checks
- Deploy and measure improvements

### Phase 3: Batch Operations (Week 3)
- Implement BatchUpdateService with JDBC batch updates
- Update OrderService to use batch updates
- Configure HTTP client connection pooling
- Deploy and validate performance

### Phase 4: Validation and Tuning (Week 4)
- Run performance tests
- Tune configuration based on metrics
- Document final configuration
- Create runbook for operations team

## Rollback Strategy

Each phase can be rolled back independently:

1. **Phase 1 Rollback**: Revert application.yml changes
2. **Phase 2 Rollback**: Revert OrderService and OrderRepository changes
3. **Phase 3 Rollback**: Disable batch updates via feature flag
4. **Phase 4 Rollback**: Revert to previous configuration values

Feature flags for gradual rollout:
```yaml
optimization:
  eager-fetching-enabled: true
  batch-updates-enabled: true
  split-transactions-enabled: true
```

## Security Considerations

- No security impact - internal optimizations only
- Connection pool credentials remain unchanged
- No new external endpoints exposed
- Monitoring endpoints require authentication

## Operational Considerations

### Monitoring Dashboard

Create Grafana dashboard with:
- Connection pool utilization over time
- Semaphore queue depth
- Circuit breaker state
- Batch processing latency (P50, P95, P99)
- Success rate trends

### Alerts

1. **Critical**: Connection pool utilization > 90%
2. **Warning**: Connection pool utilization > 75%
3. **Warning**: Threads waiting > 5
4. **Critical**: Circuit breaker OPEN
5. **Warning**: Success rate < 95%

### Runbook

**High Connection Pool Utilization**:
1. Check current load and batch sizes
2. Review semaphore queue depth
3. Check for slow queries or external service delays
4. Consider temporary increase in pool size
5. Review application logs for errors

**Circuit Breaker Activation**:
1. Check connection pool metrics
2. Review recent batch processing failures
3. Check external service health
4. Wait for recovery period (15 seconds)
5. Monitor for repeated activations
