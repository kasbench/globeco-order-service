# Circuit Breaker Behavior and Recovery Guide

## Overview

The Order Service implements a connection pool circuit breaker to protect the system from cascading failures when the database connection pool becomes exhausted. This guide explains the circuit breaker behavior, configuration, and recovery procedures.

## Circuit Breaker Purpose

### Problem Statement

Without circuit breaker protection:
- Connection pool exhaustion causes all requests to timeout
- Threads accumulate waiting for connections
- System becomes unresponsive
- Recovery is slow even after load decreases
- Cascading failures to upstream services

### Solution

The circuit breaker:
- Detects connection pool exhaustion early
- Fails fast instead of waiting for timeouts
- Prevents thread accumulation
- Allows system to recover quickly
- Protects upstream services from cascading failures

## Circuit Breaker States

### State Diagram

```
         ┌─────────────┐
         │   CLOSED    │ ◄─────────┐
         │  (Normal)   │            │
         └──────┬──────┘            │
                │                   │
                │ Utilization >90%  │ Test Request
                │ (3 failures)      │ Succeeds
                ▼                   │
         ┌─────────────┐            │
         │    OPEN     │            │
         │ (Protected) │            │
         └──────┬──────┘            │
                │                   │
                │ After 15s         │
                │ (Recovery Time)   │
                ▼                   │
         ┌─────────────┐            │
         │  HALF_OPEN  │────────────┘
         │  (Testing)  │
         └─────────────┘
                │
                │ Test Request
                │ Fails
                ▼
         ┌─────────────┐
         │    OPEN     │
         │ (Protected) │
         └─────────────┘
```

### CLOSED State (Normal Operation)

**Behavior**:
- All requests are processed normally
- Connection pool utilization monitored continuously
- Failure counter tracks consecutive failures

**Transition to OPEN**:
- Trigger: Connection pool utilization exceeds 90%
- Condition: 3 consecutive utilization checks above threshold
- Action: Circuit breaker opens immediately

**Metrics**:
```
circuit_breaker_state = 0
circuit_breaker_failures = 0-2
circuit_breaker_rejections = 0
```

**Logs**:
```
INFO  Circuit breaker state: CLOSED
DEBUG Connection pool utilization: 65.5%
```

### OPEN State (Protection Active)

**Behavior**:
- All batch submission requests are rejected immediately
- Returns 503 Service Unavailable
- No database connections consumed
- Threads do not wait for connections
- System can recover resources

**Response**:
```json
{
  "status": "VALIDATION_FAILURE",
  "message": "System temporarily overloaded - please retry in a few minutes",
  "timestamp": "2025-11-15T10:30:00Z",
  "results": []
}
```

**Duration**:
- Fixed: 15 seconds (recovery time)
- After 15 seconds, transitions to HALF_OPEN automatically

**Transition to HALF_OPEN**:
- Trigger: Recovery time elapsed (15 seconds)
- Action: Allow single test request through

**Metrics**:
```
circuit_breaker_state = 1
circuit_breaker_rejections = [increasing]
circuit_breaker_open_duration_seconds = 0-15
```

**Logs**:
```
ERROR Circuit breaker OPEN - rejecting batch submission
INFO  Circuit breaker opened due to high connection pool utilization: 92.5%
INFO  Circuit breaker will attempt recovery in 15 seconds
```

### HALF_OPEN State (Testing Recovery)

**Behavior**:
- Single test request allowed through
- Other requests still rejected
- Connection pool utilization checked
- Decision made based on test result

**Test Request Success**:
- Condition: Utilization < 75%
- Action: Transition to CLOSED
- Result: Normal operation resumes

**Test Request Failure**:
- Condition: Utilization still > 90%
- Action: Return to OPEN
- Result: Wait another 15 seconds

**Transition to CLOSED**:
- Trigger: Test request succeeds
- Condition: Utilization < 75%
- Action: Reset failure counter

**Transition to OPEN**:
- Trigger: Test request fails
- Condition: Utilization still high
- Action: Reset recovery timer

**Metrics**:
```
circuit_breaker_state = 2
circuit_breaker_test_requests = 1
```

**Logs**:
```
INFO  Circuit breaker entering HALF_OPEN state
INFO  Testing connection pool recovery
INFO  Test request succeeded - circuit breaker CLOSED
# OR
WARN  Test request failed - circuit breaker remains OPEN
```

## Configuration

### Circuit Breaker Settings

**Location**: `src/main/java/org/kasbench/globeco_order_service/service/ConnectionPoolCircuitBreaker.java`

```java
public class ConnectionPoolCircuitBreaker {
    // Utilization threshold for opening circuit breaker
    private static final double UTILIZATION_THRESHOLD = 0.75;
    
    // Number of consecutive failures before opening
    private static final int FAILURE_THRESHOLD = 3;
    
    // Time to wait before testing recovery (milliseconds)
    private static final long RECOVERY_TIME_MS = 15000;
    
    // Minimum time between state checks (milliseconds)
    private static final long CHECK_INTERVAL_MS = 1000;
}
```

### Configuration Parameters Explained

#### UTILIZATION_THRESHOLD: 0.75 (75%)

**Purpose**: Threshold for opening circuit breaker

**Rationale**:
- Connection pool max size: 60
- Threshold at 75%: 45 active connections
- Provides buffer before exhaustion (90%)
- Allows time for graceful degradation

**Tuning**:
- Increase (0.80): More aggressive, less protection
- Decrease (0.70): More conservative, earlier protection
- Consider: Database capacity, typical load patterns

#### FAILURE_THRESHOLD: 3

**Purpose**: Number of consecutive high utilization checks before opening

**Rationale**:
- Prevents false positives from transient spikes
- 3 checks × 1 second interval = 3 seconds confirmation
- Balances responsiveness with stability

**Tuning**:
- Increase (5): More tolerant of spikes, slower response
- Decrease (1): Immediate response, risk of false positives
- Consider: Load variability, spike frequency

#### RECOVERY_TIME_MS: 15000 (15 seconds)

**Purpose**: Time to wait before testing recovery

**Rationale**:
- Allows connection pool to drain
- Gives time for in-flight requests to complete
- Prevents rapid open/close cycling

**Tuning**:
- Increase (30000): Longer recovery, more conservative
- Decrease (10000): Faster recovery, risk of premature reopening
- Consider: Average request duration, pool drain time

#### CHECK_INTERVAL_MS: 1000 (1 second)

**Purpose**: Minimum time between utilization checks

**Rationale**:
- Balances responsiveness with overhead
- Aligns with metrics collection interval
- Prevents excessive checking

**Tuning**:
- Increase (5000): Less overhead, slower detection
- Decrease (500): Faster detection, more overhead
- Consider: Metrics collection frequency

## Monitoring Circuit Breaker

### Metrics

#### circuit_breaker_state
- **Type**: Gauge
- **Values**: 0 (CLOSED), 1 (OPEN), 2 (HALF_OPEN)
- **Purpose**: Current circuit breaker state
- **Alert**: State = 1 for > 1 minute

#### circuit_breaker_state_transitions_total
- **Type**: Counter
- **Labels**: from_state, to_state
- **Purpose**: Track state changes
- **Alert**: > 10 transitions per hour

#### circuit_breaker_rejections_total
- **Type**: Counter
- **Purpose**: Count rejected requests
- **Alert**: > 100 rejections per minute

#### circuit_breaker_failures_total
- **Type**: Counter
- **Purpose**: Count utilization threshold violations
- **Alert**: Increasing trend

#### circuit_breaker_open_duration_seconds
- **Type**: Histogram
- **Purpose**: Time spent in OPEN state
- **Alert**: P95 > 60 seconds

### Grafana Panels

#### Circuit Breaker State (Stat Panel)
```promql
circuit_breaker_state{application="order-service"}
```

**Display**:
- 0 → Green "CLOSED"
- 1 → Red "OPEN"
- 2 → Yellow "HALF_OPEN"

#### State Transitions (Time Series)
```promql
increase(circuit_breaker_state_transitions_total{application="order-service"}[5m])
```

**Purpose**: Visualize state changes over time

#### Rejection Rate (Time Series)
```promql
rate(circuit_breaker_rejections_total{application="order-service"}[1m])
```

**Purpose**: Track rejected requests per second

### Logs

#### State Transition Logs

**Circuit Breaker Opens**:
```
2025-11-15 10:30:00.123 ERROR [ConnectionPoolCircuitBreaker] Circuit breaker state transition: CLOSED -> OPEN
2025-11-15 10:30:00.123 ERROR [ConnectionPoolCircuitBreaker] Reason: Connection pool utilization exceeded threshold: 92.5% (threshold: 75.0%)
2025-11-15 10:30:00.123 INFO  [ConnectionPoolCircuitBreaker] Circuit breaker will attempt recovery in 15 seconds
```

**Circuit Breaker Tests Recovery**:
```
2025-11-15 10:30:15.456 INFO  [ConnectionPoolCircuitBreaker] Circuit breaker state transition: OPEN -> HALF_OPEN
2025-11-15 10:30:15.456 INFO  [ConnectionPoolCircuitBreaker] Testing connection pool recovery
2025-11-15 10:30:15.789 INFO  [ConnectionPoolCircuitBreaker] Connection pool utilization: 68.3%
```

**Circuit Breaker Closes**:
```
2025-11-15 10:30:15.789 INFO  [ConnectionPoolCircuitBreaker] Circuit breaker state transition: HALF_OPEN -> CLOSED
2025-11-15 10:30:15.789 INFO  [ConnectionPoolCircuitBreaker] Connection pool recovered, resuming normal operation
```

**Circuit Breaker Remains Open**:
```
2025-11-15 10:30:15.789 WARN  [ConnectionPoolCircuitBreaker] Connection pool utilization still high: 91.2%
2025-11-15 10:30:15.789 WARN  [ConnectionPoolCircuitBreaker] Circuit breaker state transition: HALF_OPEN -> OPEN
2025-11-15 10:30:15.789 INFO  [ConnectionPoolCircuitBreaker] Circuit breaker will attempt recovery in 15 seconds
```

#### Request Rejection Logs

```
2025-11-15 10:30:05.123 ERROR [OrderController] Circuit breaker OPEN - rejecting batch submission
2025-11-15 10:30:05.123 INFO  [OrderController] Batch submission rejected: 25 orders
2025-11-15 10:30:05.123 INFO  [OrderController] Client should retry after circuit breaker recovery
```

## Recovery Procedures

### Automatic Recovery (Normal Case)

**Timeline**:
```
T+0s:  Circuit breaker opens (utilization > 90%)
       - All requests rejected
       - Connection pool begins draining
       
T+5s:  In-flight requests completing
       - Active connections decreasing
       - Utilization dropping
       
T+10s: Most connections released
       - Utilization < 80%
       - Pool stabilizing
       
T+15s: Circuit breaker tests recovery
       - Single test request allowed
       - Utilization checked
       
T+16s: Test succeeds (utilization < 75%)
       - Circuit breaker closes
       - Normal operation resumes
```

**Expected Behavior**:
- Total downtime: ~15-20 seconds
- Automatic recovery without intervention
- No data loss (requests can be retried)

**Monitoring**:
```bash
# Watch circuit breaker state
watch -n 1 'curl -s http://localhost:8080/actuator/metrics/circuit.breaker.state | jq .measurements[0].value'

# Expected output:
# 0 (CLOSED) → 1 (OPEN) → 2 (HALF_OPEN) → 0 (CLOSED)
```

### Manual Recovery (If Automatic Fails)

**Scenario**: Circuit breaker remains OPEN after multiple recovery attempts

**Diagnosis**:
```bash
# 1. Check circuit breaker state
curl http://localhost:8080/actuator/metrics/circuit.breaker.state

# 2. Check connection pool utilization
curl http://localhost:8080/api/v1/health/connection-pool

# 3. Check for underlying issues
kubectl logs -l app=order-service --tail=200 | grep -E "circuit|connection"
```

**Common Causes**:
1. **Sustained High Load**: Load hasn't decreased
2. **Connection Leaks**: Connections not being released
3. **Slow Queries**: Database queries taking too long
4. **External Service Delays**: Trade service slow

**Resolution Steps**:

**Option 1: Wait for Load to Decrease**
```bash
# If load is temporarily high, wait for it to decrease
# Monitor utilization every 30 seconds
watch -n 30 'curl -s http://localhost:8080/api/v1/health/connection-pool | jq .utilization'

# Circuit breaker will recover automatically when utilization drops
```

**Option 2: Increase Connection Pool Temporarily**
```bash
# Increase pool size to handle current load
kubectl set env deployment/order-service SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=80

# Wait for pods to restart
kubectl rollout status deployment/order-service

# Monitor recovery
watch -n 5 'curl -s http://localhost:8080/actuator/metrics/circuit.breaker.state'
```

**Option 3: Restart Service**
```bash
# Last resort: restart to clear any stuck state
kubectl rollout restart deployment/order-service

# Monitor startup
kubectl logs -f -l app=order-service

# Verify circuit breaker state
curl http://localhost:8080/actuator/metrics/circuit.breaker.state
```

### Forced Circuit Breaker Close (Emergency Only)

**Warning**: Only use in emergency situations with approval from senior engineer

**Procedure**:
```bash
# 1. Verify underlying issue is resolved
curl http://localhost:8080/api/v1/health/connection-pool
# Utilization should be < 70%

# 2. Restart service to reset circuit breaker
kubectl rollout restart deployment/order-service

# 3. Monitor closely for 15 minutes
watch -n 10 'curl -s http://localhost:8080/api/v1/health/connection-pool'

# 4. If utilization spikes again, investigate root cause
```

## Client Integration

### Handling Circuit Breaker Responses

**Response Format**:
```json
{
  "status": "VALIDATION_FAILURE",
  "message": "System temporarily overloaded - please retry in a few minutes",
  "timestamp": "2025-11-15T10:30:00Z",
  "results": []
}
```

**HTTP Status**: 503 Service Unavailable

**Retry-After Header**: 15 (seconds)

### Recommended Client Behavior

#### Immediate Response
```java
if (response.getStatusCode() == 503) {
    String message = response.getBody().getMessage();
    if (message.contains("temporarily overloaded")) {
        // Circuit breaker is open
        logger.warn("Order Service circuit breaker is open");
        
        // Don't retry immediately
        // Wait for Retry-After header duration
        int retryAfter = response.getHeaders().getFirst("Retry-After");
        Thread.sleep(retryAfter * 1000);
        
        // Then retry
        return retrySubmission(orders);
    }
}
```

#### Retry Strategy
```java
public class CircuitBreakerAwareRetryStrategy {
    private static final int MAX_RETRIES = 3;
    private static final int BASE_DELAY_MS = 15000; // 15 seconds
    
    public Response submitWithRetry(List<Order> orders) {
        int attempt = 0;
        
        while (attempt < MAX_RETRIES) {
            Response response = orderService.submitBatch(orders);
            
            if (response.getStatusCode() == 503 && 
                response.getBody().getMessage().contains("temporarily overloaded")) {
                
                attempt++;
                logger.warn("Circuit breaker open, attempt {}/{}", attempt, MAX_RETRIES);
                
                if (attempt < MAX_RETRIES) {
                    // Wait for circuit breaker recovery
                    int delay = BASE_DELAY_MS * attempt; // Exponential backoff
                    Thread.sleep(delay);
                    continue;
                }
                
                // Max retries exceeded
                throw new ServiceUnavailableException("Order Service circuit breaker open after " + MAX_RETRIES + " attempts");
            }
            
            // Success or other error
            return response;
        }
    }
}
```

#### Preventing Dropped Orders

**Problem**: Orders submitted during circuit breaker open period may be lost

**Solution**: Implement client-side queueing

```java
public class OrderSubmissionQueue {
    private final Queue<BatchSubmissionRequest> pendingOrders = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public void submitOrQueue(List<Order> orders) {
        Response response = orderService.submitBatch(orders);
        
        if (response.getStatusCode() == 503 && 
            response.getBody().getMessage().contains("temporarily overloaded")) {
            
            // Queue for later submission
            pendingOrders.add(new BatchSubmissionRequest(orders, Instant.now()));
            logger.info("Queued {} orders for later submission", orders.size());
            
            // Schedule retry
            scheduler.schedule(this::processPendingOrders, 20, TimeUnit.SECONDS);
        }
    }
    
    private void processPendingOrders() {
        while (!pendingOrders.isEmpty()) {
            BatchSubmissionRequest request = pendingOrders.peek();
            
            try {
                Response response = orderService.submitBatch(request.getOrders());
                
                if (response.getStatusCode() == 200) {
                    // Success - remove from queue
                    pendingOrders.poll();
                    logger.info("Successfully submitted queued orders");
                } else if (response.getStatusCode() == 503) {
                    // Still overloaded - retry later
                    logger.warn("Circuit breaker still open, will retry");
                    scheduler.schedule(this::processPendingOrders, 20, TimeUnit.SECONDS);
                    break;
                } else {
                    // Other error - remove from queue and log
                    pendingOrders.poll();
                    logger.error("Failed to submit queued orders: {}", response.getBody());
                }
            } catch (Exception e) {
                logger.error("Error processing queued orders", e);
                scheduler.schedule(this::processPendingOrders, 20, TimeUnit.SECONDS);
                break;
            }
        }
    }
}
```

### Error Handling Patterns

#### Pattern 1: Fail Fast
```java
// For non-critical operations
try {
    orderService.submitBatch(orders);
} catch (ServiceUnavailableException e) {
    if (e.getMessage().contains("circuit breaker")) {
        logger.warn("Circuit breaker open, skipping non-critical submission");
        return; // Don't retry
    }
}
```

#### Pattern 2: Queue and Retry
```java
// For critical operations
try {
    orderService.submitBatch(orders);
} catch (ServiceUnavailableException e) {
    if (e.getMessage().contains("circuit breaker")) {
        logger.warn("Circuit breaker open, queueing for retry");
        orderQueue.add(orders);
        scheduleRetry();
    }
}
```

#### Pattern 3: Alternative Path
```java
// For operations with fallback
try {
    orderService.submitBatch(orders);
} catch (ServiceUnavailableException e) {
    if (e.getMessage().contains("circuit breaker")) {
        logger.warn("Circuit breaker open, using alternative submission path");
        alternativeOrderService.submitBatch(orders);
    }
}
```

## Testing Circuit Breaker

### Manual Testing

**Trigger Circuit Breaker**:
```bash
# 1. Reduce connection pool size to trigger exhaustion
kubectl set env deployment/order-service SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10

# 2. Send concurrent batch requests
for i in {1..20}; do
  curl -X POST http://localhost:8080/api/v1/orders/batch \
    -H "Content-Type: application/json" \
    -d @test-batch.json &
done

# 3. Monitor circuit breaker state
watch -n 1 'curl -s http://localhost:8080/actuator/metrics/circuit.breaker.state'

# 4. Verify circuit breaker opens
# Expected: State changes from 0 to 1

# 5. Wait for recovery
# Expected: After 15s, state changes to 2, then 0

# 6. Restore connection pool size
kubectl set env deployment/order-service SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=60
```

### Automated Testing

**Integration Test**:
```java
@Test
public void testCircuitBreakerActivation() {
    // Reduce pool size
    hikariConfig.setMaximumPoolSize(5);
    
    // Submit concurrent requests to exhaust pool
    List<CompletableFuture<Response>> futures = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
        futures.add(CompletableFuture.supplyAsync(() -> 
            orderService.submitBatch(createTestOrders(25))
        ));
    }
    
    // Wait for completion
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    
    // Verify circuit breaker opened
    assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
    
    // Wait for recovery
    Thread.sleep(20000);
    
    // Verify circuit breaker closed
    assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
}
```

## Troubleshooting

### Circuit Breaker Won't Close

**Symptoms**:
- Circuit breaker remains OPEN for > 5 minutes
- Multiple recovery attempts fail
- Utilization still high after recovery time

**Diagnosis**:
```bash
# Check connection pool
curl http://localhost:8080/api/v1/health/connection-pool

# Check for connection leaks
kubectl logs -l app=order-service | grep "Connection leak"

# Check for slow queries
kubectl exec -it postgres-pod -- psql -U postgres -c "
SELECT pid, now() - query_start as duration, query 
FROM pg_stat_activity 
WHERE state = 'active' 
ORDER BY duration DESC;"
```

**Resolution**: See "Manual Recovery" section above

### Circuit Breaker Opens Too Frequently

**Symptoms**:
- Circuit breaker opens multiple times per hour
- State transitions > 10 per hour
- Frequent service degradation

**Diagnosis**:
```bash
# Check utilization trends
# Review Grafana dashboard for patterns

# Check load patterns
curl http://localhost:8080/actuator/metrics/http.server.requests

# Check batch sizes
kubectl logs -l app=order-service | grep "Processing batch"
```

**Resolution**:
1. Increase connection pool size
2. Reduce batch sizes
3. Optimize queries
4. Scale horizontally (add more pods)

### False Positive Activations

**Symptoms**:
- Circuit breaker opens during normal load
- Utilization spikes briefly then recovers
- No actual connection pool exhaustion

**Diagnosis**:
```bash
# Check failure threshold
# Review circuit breaker configuration

# Check utilization patterns
# Look for brief spikes vs sustained high utilization
```

**Resolution**:
1. Increase FAILURE_THRESHOLD (e.g., from 3 to 5)
2. Increase UTILIZATION_THRESHOLD (e.g., from 0.75 to 0.80)
3. Increase CHECK_INTERVAL_MS (e.g., from 1000 to 2000)

## Best Practices

### For Operations Team

1. **Monitor Proactively**: Watch for warning alerts before circuit breaker opens
2. **Understand Patterns**: Learn normal utilization patterns
3. **Quick Response**: Follow runbook procedures immediately
4. **Document Incidents**: Record all circuit breaker activations
5. **Regular Reviews**: Analyze trends monthly

### For Development Team

1. **Test Circuit Breaker**: Include in integration tests
2. **Handle Gracefully**: Implement proper error handling
3. **Retry Intelligently**: Use exponential backoff
4. **Queue Critical Operations**: Don't drop important requests
5. **Monitor Metrics**: Track circuit breaker state in dashboards

### For Client Applications

1. **Respect Retry-After**: Don't retry immediately
2. **Implement Queueing**: Prevent order loss
3. **Use Exponential Backoff**: Avoid overwhelming service
4. **Monitor Circuit Breaker**: Track 503 responses
5. **Have Fallback**: Consider alternative paths

## References

- Martin Fowler's Circuit Breaker Pattern: https://martinfowler.com/bliki/CircuitBreaker.html
- Release It! by Michael Nygard
- Connection Pool Configuration Guide: See CONNECTION_POOL_CONFIGURATION.md
- Operational Runbook: See OPERATIONAL_RUNBOOK.md
