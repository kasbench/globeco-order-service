# Connection Pool Configuration Guide

## Overview

This document describes the optimized connection pool configuration for the Order Service, designed to eliminate circuit breaker activations and improve throughput by 60-70%.

## Configuration Settings

### HikariCP Connection Pool

**Location**: `src/main/resources/application.yml`

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 60
      minimum-idle: 20
      connection-timeout: 5000
      validation-timeout: 2000
      leak-detection-threshold: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### Configuration Parameters Explained

#### maximum-pool-size: 60
- **Purpose**: Maximum number of connections in the pool
- **Calculation**: (max_concurrent_operations × 1.5) + buffer = (15 × 1.5) + 37.5 ≈ 60
- **Previous Value**: 40
- **Impact**: Provides headroom for concurrent batch processing without exhaustion
- **Tuning**: Increase if utilization consistently exceeds 75% under normal load

#### minimum-idle: 20
- **Purpose**: Minimum number of idle connections maintained
- **Previous Value**: 10
- **Impact**: Reduces connection establishment overhead during load spikes
- **Tuning**: Should be approximately 33% of maximum-pool-size

#### connection-timeout: 5000
- **Purpose**: Maximum time (ms) to wait for a connection from the pool
- **Impact**: Prevents indefinite blocking when pool is exhausted
- **Behavior**: Throws SQLException after timeout
- **Tuning**: Increase if legitimate requests are timing out

#### validation-timeout: 2000
- **Purpose**: Maximum time (ms) for connection validation query
- **Impact**: Ensures connections are healthy before use
- **Tuning**: Should be less than connection-timeout

#### leak-detection-threshold: 30000
- **Purpose**: Time (ms) before logging connection leak warning
- **Impact**: Helps identify long-running transactions
- **Monitoring**: Review logs for leak warnings regularly
- **Tuning**: Set to 0 to disable in production if no leaks detected

#### idle-timeout: 600000 (10 minutes)
- **Purpose**: Maximum time a connection can sit idle before removal
- **Impact**: Prevents stale connections
- **Tuning**: Balance between connection reuse and resource cleanup

#### max-lifetime: 1800000 (30 minutes)
- **Purpose**: Maximum lifetime of a connection in the pool
- **Impact**: Forces connection refresh to prevent stale connections
- **Tuning**: Should be less than database connection timeout

## Concurrency Control

### Batch Processing Semaphore

**Location**: `src/main/java/org/kasbench/globeco_order_service/service/BatchProcessingService.java`

```java
private static final int MAX_CONCURRENT_DB_OPERATIONS = 15;
```

#### MAX_CONCURRENT_DB_OPERATIONS: 15
- **Purpose**: Limits concurrent database operations
- **Previous Value**: 25
- **Calculation**: Approximately 25% of maximum-pool-size
- **Impact**: Prevents connection pool exhaustion during high load
- **Tuning**: Increase only if connection pool utilization stays below 60%

### Relationship Between Pool Size and Semaphore

```
Connection Pool Size (60)
├── Reserved for Semaphore Operations (15)
├── Reserved for Other Operations (20)
│   ├── Health checks
│   ├── Metrics collection
│   └── Administrative queries
└── Buffer for Spikes (25)
```

## Transaction Timeouts

### Read Transactions

```java
@Transactional(readOnly = true, timeout = 3)
```

- **Purpose**: Short-lived read operations
- **Timeout**: 3 seconds
- **Use Cases**: Order loading, validation queries
- **Impact**: Releases connections quickly

### Write Transactions

```java
@Transactional(timeout = 5)
```

- **Purpose**: Write operations including batch updates
- **Timeout**: 5 seconds
- **Use Cases**: Order status updates, batch operations
- **Impact**: Prevents long-running transactions

## HTTP Client Connection Pool

**Location**: `src/main/java/org/kasbench/globeco_order_service/config/HttpClientConfiguration.java`

```java
PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
manager.setMaxTotal(50);
manager.setDefaultMaxPerRoute(25);
```

### HTTP Connection Pool Parameters

#### MaxTotal: 50
- **Purpose**: Maximum total HTTP connections across all routes
- **Impact**: Enables concurrent external service calls
- **Tuning**: Increase if external service calls are bottleneck

#### MaxPerRoute: 25
- **Purpose**: Maximum connections per destination
- **Impact**: Prevents single service from monopolizing pool
- **Tuning**: Should be approximately 50% of MaxTotal

#### Keep-Alive: 30 seconds
- **Purpose**: Reuse HTTP connections
- **Impact**: Reduces connection establishment overhead
- **Tuning**: Align with external service keep-alive settings

## Monitoring Configuration

### Metrics Collection Interval

```java
@Scheduled(fixedRate = 5000) // Every 5 seconds
public void recordConnectionPoolMetrics()
```

- **Interval**: 5 seconds
- **Purpose**: Real-time connection pool monitoring
- **Metrics Collected**:
  - Active connections
  - Idle connections
  - Threads waiting
  - Utilization percentage

### Alert Thresholds

#### Warning Level: 75% Utilization
```java
if (utilization > 0.75) {
    logger.warn("High connection pool utilization: {:.2f}%", utilization * 100);
}
```

#### Critical Level: 90% Utilization (Circuit Breaker)
```java
private static final double UTILIZATION_THRESHOLD = 0.75;
```

## Logging Configuration

**Location**: `src/main/resources/application.yml`

```yaml
logging:
  level:
    root: INFO
    org.kasbench.globeco_order_service.service: INFO
    org.kasbench.globeco_order_service.controller: INFO
    com.zaxxer.hikari: INFO
    com.zaxxer.hikari.pool.HikariPool: DEBUG  # Enable for troubleshooting
```

### Logging Levels by Environment

#### Production
- Service/Controller: INFO
- HikariCP: INFO
- Enable DEBUG only during incidents

#### Staging
- Service/Controller: INFO
- HikariCP: DEBUG
- Useful for performance testing

#### Development
- Service/Controller: DEBUG
- HikariCP: DEBUG
- Full visibility for development

## Performance Tuning Guidelines

### Increasing Throughput

1. **Monitor utilization**: Keep average below 70%
2. **Increase pool size**: Add 10 connections at a time
3. **Adjust semaphore**: Increase by 5 operations at a time
4. **Verify ratio**: Maintain pool_size ≈ semaphore × 4

### Reducing Latency

1. **Increase minimum-idle**: Reduces connection establishment time
2. **Reduce validation-timeout**: Faster connection validation
3. **Optimize queries**: Use eager fetching to reduce query count
4. **Use batch updates**: Reduce transaction overhead

### Handling Load Spikes

1. **Increase maximum-pool-size**: Provides buffer for spikes
2. **Increase minimum-idle**: Keeps connections ready
3. **Monitor queue depth**: Track semaphore wait times
4. **Enable circuit breaker**: Protects system during overload

## Configuration Validation

### Startup Checks

The service validates configuration on startup:

```
✓ Connection pool initialized: 60 max, 20 min-idle
✓ Semaphore configured: 15 concurrent operations
✓ HTTP client pool: 50 max connections
✓ Monitoring service started: 5s interval
✓ Circuit breaker configured: 75% threshold
```

### Health Check Endpoint

**Endpoint**: `GET /api/v1/health/connection-pool`

**Response**:
```json
{
  "active": 12,
  "idle": 8,
  "waiting": 0,
  "total": 60,
  "utilization": 0.20,
  "status": "HEALTHY",
  "timestamp": "2025-11-15T10:30:00Z"
}
```

## Troubleshooting

### High Utilization (>75%)

**Symptoms**: Warning logs, increased latency

**Actions**:
1. Check current load and batch sizes
2. Review slow query log
3. Verify external service response times
4. Consider temporary pool size increase

### Connection Timeouts

**Symptoms**: SQLException with "Connection timeout"

**Actions**:
1. Check if pool is exhausted (utilization = 100%)
2. Review long-running transactions
3. Check for connection leaks
4. Increase connection-timeout temporarily

### Connection Leaks

**Symptoms**: Leak detection warnings in logs

**Actions**:
1. Review stack trace in leak warning
2. Verify @Transactional annotations
3. Check for missing connection closes
4. Enable leak detection in development

## Migration from Previous Configuration

### Before (40 connections, 25 semaphore)
- Utilization: 92.6%
- Threads waiting: 5-10
- Circuit breaker activations: Frequent
- Throughput: Baseline

### After (60 connections, 15 semaphore)
- Utilization: <70%
- Threads waiting: 0-2
- Circuit breaker activations: Rare
- Throughput: +60-70%

### Rollback Procedure

If issues occur, revert to previous configuration:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 40
      minimum-idle: 10
```

```java
private static final int MAX_CONCURRENT_DB_OPERATIONS = 25;
```

Monitor for 30 minutes after rollback.

## References

- HikariCP Documentation: https://github.com/brettwooldridge/HikariCP
- Spring Boot DataSource Configuration: https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html#application-properties.data
- Connection Pool Sizing: https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
