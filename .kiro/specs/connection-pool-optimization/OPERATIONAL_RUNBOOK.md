# Operational Runbook - Connection Pool Optimization

## Overview

This runbook provides step-by-step procedures for operating and troubleshooting the Order Service connection pool optimization. Use this guide for incident response, performance tuning, and routine maintenance.

## Quick Reference

### Health Check Endpoints

| Endpoint | Purpose | Expected Response |
|----------|---------|-------------------|
| `/actuator/health` | Overall service health | `{"status": "UP"}` |
| `/api/v1/health/connection-pool` | Connection pool status | Utilization < 75% |
| `/actuator/metrics/hikaricp.connections.active` | Active connections | < 45 (75% of 60) |
| `/actuator/prometheus` | All metrics | Prometheus format |

### Key Metrics

| Metric | Healthy Range | Warning | Critical |
|--------|---------------|---------|----------|
| Connection Pool Utilization | 0-70% | 75-90% | >90% |
| Threads Waiting | 0-2 | 3-4 | ≥5 |
| Batch Success Rate | >99% | 95-99% | <95% |
| P95 Latency | <5s | 5-8s | >8s |
| Circuit Breaker State | CLOSED | HALF_OPEN | OPEN |

### Emergency Contacts

- **On-Call Engineer**: [PagerDuty rotation]
- **Database Team**: [Contact info]
- **Platform Team**: [Contact info]
- **Escalation**: [Manager contact]

## Incident Response Procedures

### Scenario 1: High Connection Pool Utilization (>75%)

#### Symptoms
- Warning alerts firing
- Increased latency
- Grafana shows utilization >75%
- Log entries: "High connection pool utilization"

#### Immediate Actions (5 minutes)

1. **Check Current State**
   ```bash
   # Check connection pool health
   curl http://localhost:8080/api/v1/health/connection-pool
   
   # Check active connections
   curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
   
   # Check threads waiting
   curl http://localhost:8080/actuator/metrics/hikaricp.connections.pending
   ```

2. **Review Grafana Dashboard**
   - Open "Order Service - Connection Pool Monitoring"
   - Check utilization trend (increasing or stable?)
   - Review batch processing throughput
   - Check for error spikes

3. **Check Application Logs**
   ```bash
   # Recent errors
   kubectl logs -l app=order-service --tail=100 | grep -i error
   
   # Connection pool warnings
   kubectl logs -l app=order-service --tail=100 | grep "connection pool"
   
   # Long-running transactions
   kubectl logs -l app=order-service --tail=100 | grep "leak detection"
   ```

#### Root Cause Analysis (10 minutes)

**Check 1: Abnormal Load**
```bash
# Check request rate
curl http://localhost:8080/actuator/metrics/http.server.requests | grep count

# Check batch sizes
kubectl logs -l app=order-service --tail=50 | grep "Processing batch"
```

**Action**: If load is abnormally high, coordinate with upstream services to reduce traffic.

**Check 2: Slow Queries**
```bash
# Check database query times
curl http://localhost:8080/actuator/metrics/database.query.duration

# Review slow query log
kubectl exec -it postgres-pod -- psql -U postgres -c "SELECT query, mean_exec_time FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 10;"
```

**Action**: If queries are slow, check database performance and indexes.

**Check 3: External Service Delays**
```bash
# Check trade service response times
curl http://localhost:8080/actuator/metrics/http.client.requests | grep trade-service
```

**Action**: If external service is slow, check its health and consider circuit breaker activation.

**Check 4: Connection Leaks**
```bash
# Check for leak warnings
kubectl logs -l app=order-service --tail=500 | grep "Connection leak"
```

**Action**: If leaks detected, identify the code path and prepare hotfix.

#### Resolution Steps

**Option 1: Temporary Pool Size Increase** (Quick mitigation)
```bash
# Update ConfigMap
kubectl edit configmap order-service-config

# Change maximum-pool-size from 60 to 80
spring:
  datasource:
    hikari:
      maximum-pool-size: 80

# Restart pods
kubectl rollout restart deployment order-service

# Monitor for 15 minutes
watch -n 5 'curl -s http://localhost:8080/api/v1/health/connection-pool | jq .utilization'
```

**Option 2: Reduce Concurrent Operations** (Conservative approach)
```bash
# Update ConfigMap
kubectl edit configmap order-service-config

# Add environment variable
MAX_CONCURRENT_DB_OPERATIONS: "10"

# Restart pods
kubectl rollout restart deployment order-service
```

**Option 3: Enable Circuit Breaker** (Protection mode)
- Circuit breaker should activate automatically at 90% utilization
- Monitor for automatic recovery after 15 seconds
- If not recovering, investigate root cause

#### Post-Incident Actions

1. **Document Findings**
   - Record peak utilization reached
   - Note duration of incident
   - Identify root cause
   - Document resolution steps taken

2. **Review Metrics**
   - Export Grafana dashboard for incident period
   - Analyze utilization patterns
   - Check for recurring patterns

3. **Follow-up Tasks**
   - Schedule capacity planning review
   - Update alert thresholds if needed
   - Create Jira ticket for permanent fix

### Scenario 2: Circuit Breaker Activation

#### Symptoms
- Critical alert: "Circuit breaker is OPEN"
- Requests returning 503 errors
- Log entries: "Circuit breaker OPEN - rejecting batch"
- Grafana shows circuit_breaker_state = 1

#### Immediate Actions (2 minutes)

1. **Confirm Circuit Breaker State**
   ```bash
   # Check circuit breaker metrics
   curl http://localhost:8080/actuator/metrics/circuit.breaker.state
   
   # Check rejection count
   curl http://localhost:8080/actuator/metrics/circuit.breaker.rejections
   ```

2. **Check Connection Pool**
   ```bash
   # Connection pool utilization
   curl http://localhost:8080/api/v1/health/connection-pool
   ```

3. **Review Recent Logs**
   ```bash
   # Circuit breaker events
   kubectl logs -l app=order-service --tail=200 | grep "Circuit breaker"
   
   # Connection pool events
   kubectl logs -l app=order-service --tail=200 | grep "connection pool"
   ```

#### Understanding Circuit Breaker Behavior

**Automatic Recovery**:
- Circuit breaker opens at 90% utilization
- Waits 15 seconds (recovery time)
- Transitions to HALF_OPEN
- Tests with single request
- If successful, transitions to CLOSED
- If failed, returns to OPEN for another 15 seconds

**Expected Timeline**:
- T+0s: Circuit breaker opens
- T+15s: Transitions to HALF_OPEN
- T+16s: Test request
- T+17s: Returns to CLOSED (if successful)

#### Resolution Steps

**Step 1: Wait for Automatic Recovery** (15-30 seconds)
```bash
# Monitor circuit breaker state
watch -n 2 'curl -s http://localhost:8080/actuator/metrics/circuit.breaker.state'

# Expected: OPEN (1) → HALF_OPEN (2) → CLOSED (0)
```

**Step 2: If Not Recovering** (After 2 minutes)
```bash
# Check if underlying issue persists
curl http://localhost:8080/api/v1/health/connection-pool

# If utilization still >90%, follow "High Connection Pool Utilization" procedure
```

**Step 3: Manual Intervention** (If automatic recovery fails)
```bash
# Restart service to reset circuit breaker
kubectl rollout restart deployment order-service

# Monitor recovery
kubectl logs -f -l app=order-service | grep "Circuit breaker"
```

#### Communication

**Notify Stakeholders**:
```
Subject: Order Service Circuit Breaker Activated

The Order Service circuit breaker has activated due to high connection pool utilization.

Status: [OPEN/RECOVERING/RESOLVED]
Impact: Order submissions temporarily rejected
Expected Recovery: [TIME]
Action Taken: [DESCRIPTION]

Updates will be provided every 5 minutes.
```

#### Post-Incident Actions

1. **Analyze Trigger**
   - What caused utilization to exceed 90%?
   - Was it gradual or sudden?
   - Were there preceding warnings?

2. **Review Alert Response**
   - Did warning alerts fire before critical?
   - Was response time adequate?
   - Were runbook procedures effective?

3. **Preventive Measures**
   - Adjust pool size if needed
   - Review batch size limits
   - Update monitoring thresholds

### Scenario 3: Connection Timeout Errors

#### Symptoms
- Errors: "Connection is not available, request timed out after 5000ms"
- Increased latency
- Failed batch submissions
- Threads waiting metric elevated

#### Immediate Actions (5 minutes)

1. **Check Connection Pool Status**
   ```bash
   # Full connection pool health
   curl http://localhost:8080/api/v1/health/connection-pool | jq .
   
   # Expected output:
   # {
   #   "active": 60,
   #   "idle": 0,
   #   "waiting": 10,
   #   "total": 60,
   #   "utilization": 1.0,
   #   "status": "CRITICAL"
   # }
   ```

2. **Identify Blocking Operations**
   ```bash
   # Check for long-running transactions
   kubectl logs -l app=order-service --tail=200 | grep "Transaction"
   
   # Check database for blocking queries
   kubectl exec -it postgres-pod -- psql -U postgres -c "
   SELECT pid, usename, state, query_start, state_change, query 
   FROM pg_stat_activity 
   WHERE state != 'idle' 
   ORDER BY query_start;"
   ```

3. **Check Semaphore Status**
   ```bash
   # Semaphore available permits
   curl http://localhost:8080/actuator/metrics/semaphore.available.permits
   
   # Semaphore wait time
   curl http://localhost:8080/actuator/metrics/semaphore.wait.duration
   ```

#### Root Cause Analysis

**Scenario A: Pool Exhausted by Normal Load**
- All connections active
- No long-running transactions
- High request rate

**Action**: Increase pool size temporarily

**Scenario B: Long-Running Transactions**
- Some connections held for >30 seconds
- Leak detection warnings in logs

**Action**: Identify and kill blocking transactions

**Scenario C: Database Performance Issue**
- Queries taking longer than usual
- Database CPU/IO high

**Action**: Engage database team

#### Resolution Steps

**For Scenario A: Increase Pool Size**
```bash
# Update configuration
kubectl set env deployment/order-service SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=80

# Monitor
watch -n 5 'curl -s http://localhost:8080/api/v1/health/connection-pool | jq .utilization'
```

**For Scenario B: Kill Long-Running Transactions**
```bash
# Identify long-running transactions
kubectl exec -it postgres-pod -- psql -U postgres -c "
SELECT pid, now() - query_start as duration, query 
FROM pg_stat_activity 
WHERE state = 'active' AND now() - query_start > interval '30 seconds';"

# Kill specific transaction (use with caution)
kubectl exec -it postgres-pod -- psql -U postgres -c "SELECT pg_terminate_backend(PID);"
```

**For Scenario C: Database Performance**
```bash
# Check database metrics
kubectl exec -it postgres-pod -- psql -U postgres -c "
SELECT schemaname, tablename, seq_scan, idx_scan 
FROM pg_stat_user_tables 
WHERE seq_scan > 1000 
ORDER BY seq_scan DESC;"

# Engage database team for optimization
```

### Scenario 4: Low Batch Processing Throughput

#### Symptoms
- Orders per second below target (<5 ops/s)
- Increased P95/P99 latency
- No errors but slow processing
- Success rate normal (>99%)

#### Immediate Actions (5 minutes)

1. **Check Current Throughput**
   ```bash
   # Orders per second
   curl http://localhost:8080/actuator/metrics/bulk.submission.orders.rate
   
   # Batch processing duration
   curl http://localhost:8080/actuator/metrics/bulk.submission.duration
   ```

2. **Review Transaction Breakdown**
   ```bash
   # Order load time
   curl http://localhost:8080/actuator/metrics/bulk.submission.order.load.duration
   
   # Trade service call time
   curl http://localhost:8080/actuator/metrics/bulk.submission.trade.service.duration
   
   # Database update time
   curl http://localhost:8080/actuator/metrics/bulk.submission.database.update.duration
   ```

3. **Check Grafana Dashboard**
   - Review "Transaction Duration by Phase" panel
   - Identify which phase is slowest
   - Check for trends over time

#### Root Cause Analysis

**Phase 1: Order Load Slow** (>1 second)
- Possible N+1 query issue
- Database performance problem
- Missing indexes

**Phase 2: Trade Service Slow** (>3 seconds)
- External service performance issue
- Network latency
- HTTP connection pool exhaustion

**Phase 3: Database Update Slow** (>1 second)
- Batch update not working
- Lock contention
- Database write performance

#### Resolution Steps

**For Slow Order Load**:
```bash
# Verify eager fetching is enabled
kubectl logs -l app=order-service --tail=100 | grep "findAllByIdWithRelations"

# Check query execution
kubectl exec -it postgres-pod -- psql -U postgres -c "
SELECT query, calls, mean_exec_time 
FROM pg_stat_statements 
WHERE query LIKE '%Order%' 
ORDER BY mean_exec_time DESC 
LIMIT 5;"

# If N+1 queries detected, verify JOIN FETCH is working
```

**For Slow Trade Service**:
```bash
# Check HTTP connection pool
curl http://localhost:8080/actuator/metrics/http.client.connections.active

# Check trade service health
curl http://trade-service:8080/actuator/health

# Review HTTP client configuration
kubectl get configmap order-service-config -o yaml | grep -A 10 "http.client"
```

**For Slow Database Update**:
```bash
# Verify batch updates are enabled
kubectl logs -l app=order-service --tail=100 | grep "batchUpdateOrderStatuses"

# Check for lock contention
kubectl exec -it postgres-pod -- psql -U postgres -c "
SELECT locktype, relation::regclass, mode, granted 
FROM pg_locks 
WHERE NOT granted;"

# Review batch size configuration
kubectl get configmap order-service-config -o yaml | grep BATCH_SIZE
```

### Scenario 5: Connection Leak Detection

#### Symptoms
- Log warnings: "Connection leak detection triggered"
- Gradual increase in active connections
- Connections not returning to pool
- Eventually leads to pool exhaustion

#### Immediate Actions (10 minutes)

1. **Identify Leak Source**
   ```bash
   # Find leak detection warnings with stack traces
   kubectl logs -l app=order-service --tail=1000 | grep -A 50 "Connection leak"
   
   # Look for common patterns in stack traces
   ```

2. **Check Current Pool State**
   ```bash
   # Connection pool health
   curl http://localhost:8080/api/v1/health/connection-pool
   
   # Active connections trend
   # (Should be stable, not continuously increasing)
   ```

3. **Review Recent Code Changes**
   ```bash
   # Check recent deployments
   kubectl rollout history deployment order-service
   
   # Review recent commits
   git log --oneline --since="24 hours ago"
   ```

#### Root Cause Analysis

**Common Causes**:
1. Missing @Transactional annotation
2. Transaction not properly closed
3. Manual connection management without close
4. Exception thrown before connection release
5. Async operation holding connection

#### Resolution Steps

**Immediate Mitigation**:
```bash
# Restart service to release leaked connections
kubectl rollout restart deployment order-service

# Monitor for leak recurrence
watch -n 10 'kubectl logs -l app=order-service --tail=50 | grep "Connection leak"'
```

**Permanent Fix**:
1. Identify code path from stack trace
2. Verify @Transactional annotations present
3. Check for try-catch blocks that might prevent cleanup
4. Review async operations
5. Add proper connection management
6. Deploy hotfix
7. Monitor for 24 hours

**Example Fix**:
```java
// Before (leak potential)
public void processOrder(Integer id) {
    Order order = orderRepository.findById(id).orElseThrow();
    // Exception here could leak connection
    externalService.call(order);
}

// After (leak prevented)
@Transactional(timeout = 5)
public void processOrder(Integer id) {
    try {
        Order order = orderRepository.findById(id).orElseThrow();
        externalService.call(order);
    } catch (Exception e) {
        logger.error("Error processing order", e);
        throw e; // Transaction will rollback and release connection
    }
}
```

## Routine Maintenance

### Daily Health Checks

**Morning Check** (5 minutes):
```bash
# 1. Check service health
curl http://localhost:8080/actuator/health

# 2. Check connection pool utilization (last 24h average)
# Review Grafana dashboard

# 3. Check for any alerts
# Review PagerDuty/alert manager

# 4. Check error rate
curl http://localhost:8080/actuator/metrics/bulk.submission.errors

# 5. Review logs for warnings
kubectl logs -l app=order-service --since=24h | grep -i warn | wc -l
```

**Expected Results**:
- Service health: UP
- Average utilization: <70%
- No active alerts
- Error rate: <1%
- Warning count: <50

### Weekly Performance Review

**Monday Review** (15 minutes):

1. **Throughput Analysis**
   - Compare last week vs previous week
   - Identify trends
   - Check for degradation

2. **Latency Analysis**
   - Review P95/P99 trends
   - Identify slow periods
   - Correlate with load patterns

3. **Error Analysis**
   - Review error types and frequencies
   - Identify patterns
   - Create tickets for recurring issues

4. **Capacity Planning**
   - Review peak utilization
   - Forecast growth
   - Plan capacity increases

### Monthly Optimization Review

**First Monday of Month** (30 minutes):

1. **Configuration Review**
   - Are current settings optimal?
   - Any configuration drift?
   - Update documentation if changed

2. **Performance Baseline**
   - Document current performance metrics
   - Compare to previous month
   - Identify improvements or degradation

3. **Alert Tuning**
   - Review alert frequency
   - Adjust thresholds if needed
   - Remove noisy alerts

4. **Runbook Updates**
   - Document new scenarios encountered
   - Update procedures based on learnings
   - Share with team

## Performance Tuning Guide

### Increasing Pool Size

**When to Increase**:
- Utilization consistently >70%
- Threads waiting >2
- No connection leaks detected
- Database can handle more connections

**How to Increase**:
```bash
# Increase by 10 connections at a time
# Current: 60, New: 70
kubectl set env deployment/order-service SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=70

# Also increase minimum-idle proportionally (33%)
kubectl set env deployment/order-service SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=23

# Monitor for 24 hours
# Check: Utilization should decrease, throughput should increase
```

### Adjusting Semaphore Limit

**When to Increase**:
- Connection pool utilization <60%
- High semaphore wait times
- Throughput below target
- Database not saturated

**How to Increase**:
```bash
# Increase by 5 operations at a time
# Current: 15, New: 20
kubectl set env deployment/order-service MAX_CONCURRENT_DB_OPERATIONS=20

# Monitor for 1 hour
# Check: Throughput should increase, utilization should increase
```

**When to Decrease**:
- Connection pool utilization >75%
- Threads waiting >3
- Database showing signs of saturation

### Optimizing Transaction Timeouts

**Read Transaction Timeout**:
```yaml
# Current: 3 seconds
# Increase if legitimate queries timing out
# Decrease if queries should be faster

# Check average query time first
curl http://localhost:8080/actuator/metrics/database.query.duration

# If P95 < 2s, keep at 3s
# If P95 > 2.5s, increase to 5s
```

**Write Transaction Timeout**:
```yaml
# Current: 5 seconds
# Increase if batch updates timing out
# Decrease if updates should be faster

# Check average update time first
curl http://localhost:8080/actuator/metrics/bulk.submission.database.update.duration

# If P95 < 4s, keep at 5s
# If P95 > 4.5s, increase to 8s
```

## Troubleshooting Decision Tree

```
Connection Pool Issue?
│
├─ Utilization >90%?
│  ├─ Yes → Circuit Breaker Scenario
│  └─ No → Continue
│
├─ Threads Waiting >5?
│  ├─ Yes → Connection Timeout Scenario
│  └─ No → Continue
│
├─ Throughput Low?
│  ├─ Yes → Low Throughput Scenario
│  └─ No → Continue
│
├─ Leak Warnings?
│  ├─ Yes → Connection Leak Scenario
│  └─ No → Continue
│
└─ Check External Dependencies
   ├─ Database slow?
   ├─ Trade service slow?
   └─ Network issues?
```

## Escalation Procedures

### Level 1: On-Call Engineer (0-15 minutes)
- Follow runbook procedures
- Attempt immediate mitigation
- Gather diagnostic information

### Level 2: Senior Engineer (15-30 minutes)
- Escalate if runbook procedures don't resolve
- Complex root cause analysis needed
- Code changes required

### Level 3: Engineering Manager (30+ minutes)
- Escalate if service degradation continues
- Multiple teams coordination needed
- Business impact significant

### Level 4: VP Engineering (Critical)
- Escalate for major outage
- Customer impact severe
- Executive communication needed

## Appendix

### Useful Commands Reference

```bash
# Connection pool health
curl http://localhost:8080/api/v1/health/connection-pool | jq .

# All metrics
curl http://localhost:8080/actuator/metrics | jq '.names[]' | sort

# Specific metric
curl http://localhost:8080/actuator/metrics/METRIC_NAME | jq .

# Recent logs
kubectl logs -l app=order-service --tail=100

# Follow logs
kubectl logs -f -l app=order-service

# Logs with timestamp
kubectl logs -l app=order-service --timestamps --since=1h

# Pod status
kubectl get pods -l app=order-service

# Restart deployment
kubectl rollout restart deployment order-service

# Rollback deployment
kubectl rollout undo deployment order-service

# Check ConfigMap
kubectl get configmap order-service-config -o yaml

# Edit ConfigMap
kubectl edit configmap order-service-config

# Database connection
kubectl exec -it postgres-pod -- psql -U postgres -d orderdb
```

### Metric Names Reference

| Metric Name | Description |
|-------------|-------------|
| `hikaricp.connections.active` | Active connections |
| `hikaricp.connections.idle` | Idle connections |
| `hikaricp.connections.pending` | Threads waiting |
| `hikaricp.connections` | Total connections |
| `bulk.submission.duration` | Batch processing time |
| `bulk.submission.success` | Successful batches |
| `bulk.submission.errors` | Failed batches |
| `semaphore.available.permits` | Available permits |
| `semaphore.wait.duration` | Wait time |
| `circuit.breaker.state` | Circuit breaker state |
| `database.query.duration` | Query execution time |

### Log Pattern Reference

| Pattern | Meaning |
|---------|---------|
| `High connection pool utilization` | Warning threshold exceeded |
| `Connection leak detection` | Potential connection leak |
| `Circuit breaker OPEN` | Protection activated |
| `Transaction timeout` | Transaction exceeded limit |
| `Connection is not available` | Pool exhausted |
| `Semaphore timeout` | Concurrency limit reached |

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-11-15 | DevOps Team | Initial version |

## Feedback

For runbook improvements or questions, contact the DevOps team or create a ticket in the Operations project.
