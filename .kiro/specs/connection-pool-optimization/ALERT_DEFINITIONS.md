# Alert Definitions for Connection Pool Optimization

## Overview

This document defines all alerts for monitoring the Order Service connection pool optimization. Alerts are categorized by severity and include clear thresholds, evaluation periods, and response procedures.

## Alert Severity Levels

| Severity | Response Time | Escalation | Examples |
|----------|---------------|------------|----------|
| **Critical** | Immediate (< 5 min) | Page on-call | Circuit breaker open, pool exhausted |
| **Warning** | 15 minutes | Slack notification | High utilization, elevated latency |
| **Info** | Next business day | Email | Configuration changes, deployments |

## Alert Configuration Format

All alerts follow this structure:
- **Alert Name**: Unique identifier
- **Severity**: Critical, Warning, or Info
- **Description**: What the alert indicates
- **Query**: Prometheus query expression
- **Threshold**: Value that triggers alert
- **Duration**: How long condition must persist
- **Labels**: Additional metadata
- **Annotations**: Human-readable details
- **Runbook**: Link to resolution procedure

## Critical Alerts

### CRITICAL-001: Connection Pool Exhausted

**Severity**: Critical

**Description**: Connection pool utilization has reached 90% or higher, indicating imminent exhaustion.

**Prometheus Query**:
```yaml
alert: ConnectionPoolExhausted
expr: (hikaricp_connections_active{application="order-service"} / hikaricp_connections{application="order-service"}) > 0.90
for: 2m
labels:
  severity: critical
  component: database
  team: backend
annotations:
  summary: "Connection pool utilization critical on {{ $labels.instance }}"
  description: "Connection pool utilization is {{ $value | humanizePercentage }} (threshold: 90%)"
  impact: "Service degradation, potential circuit breaker activation"
  runbook_url: "https://wiki.company.com/runbooks/connection-pool-exhausted"
```

**Threshold**: 90% utilization

**Duration**: 2 minutes

**Response Procedure**:
1. Check circuit breaker state
2. Review connection pool health endpoint
3. Identify cause (high load, slow queries, leaks)
4. Follow "High Connection Pool Utilization" runbook
5. Consider temporary pool size increase

**Escalation**: Page on-call engineer immediately

---

### CRITICAL-002: Circuit Breaker Open

**Severity**: Critical

**Description**: Circuit breaker has opened, rejecting all batch submission requests.

**Prometheus Query**:
```yaml
alert: CircuitBreakerOpen
expr: circuit_breaker_state{application="order-service"} == 1
for: 1m
labels:
  severity: critical
  component: circuit-breaker
  team: backend
annotations:
  summary: "Circuit breaker is OPEN on {{ $labels.instance }}"
  description: "Circuit breaker has opened, rejecting requests to protect the system"
  impact: "All batch submissions are being rejected with 503 errors"
  runbook_url: "https://wiki.company.com/runbooks/circuit-breaker-open"
```

**Threshold**: State = 1 (OPEN)

**Duration**: 1 minute

**Response Procedure**:
1. Verify circuit breaker state
2. Check connection pool utilization
3. Wait for automatic recovery (15 seconds)
4. If not recovering, follow "Circuit Breaker Activation" runbook
5. Notify stakeholders of service degradation

**Escalation**: Page on-call engineer immediately

**Auto-Resolution**: Alert resolves when circuit breaker closes

---

### CRITICAL-003: High Thread Wait Count

**Severity**: Critical

**Description**: Multiple threads are waiting for database connections, indicating pool contention.

**Prometheus Query**:
```yaml
alert: HighThreadWaitCount
expr: hikaricp_connections_pending{application="order-service"} > 5
for: 2m
labels:
  severity: critical
  component: database
  team: backend
annotations:
  summary: "{{ $value }} threads waiting for connections on {{ $labels.instance }}"
  description: "High number of threads waiting for database connections"
  impact: "Increased latency, potential timeouts"
  runbook_url: "https://wiki.company.com/runbooks/connection-timeout"
```

**Threshold**: > 5 threads waiting

**Duration**: 2 minutes

**Response Procedure**:
1. Check connection pool utilization
2. Review active connections
3. Check for long-running transactions
4. Follow "Connection Timeout Errors" runbook
5. Consider increasing pool size

**Escalation**: Page on-call engineer immediately

---

### CRITICAL-004: Connection Timeout Rate High

**Severity**: Critical

**Description**: High rate of connection timeout errors indicating pool exhaustion.

**Prometheus Query**:
```yaml
alert: ConnectionTimeoutRateHigh
expr: rate(bulk_submission_errors_total{application="order-service",error_type="connection_timeout"}[5m]) > 0.5
for: 3m
labels:
  severity: critical
  component: database
  team: backend
annotations:
  summary: "High connection timeout rate on {{ $labels.instance }}"
  description: "{{ $value | humanize }} connection timeouts per second"
  impact: "Failed batch submissions, degraded service"
  runbook_url: "https://wiki.company.com/runbooks/connection-timeout"
```

**Threshold**: > 0.5 timeouts per second

**Duration**: 3 minutes

**Response Procedure**:
1. Check connection pool status
2. Review threads waiting metric
3. Identify blocking operations
4. Follow "Connection Timeout Errors" runbook

**Escalation**: Page on-call engineer immediately

---

### CRITICAL-005: Batch Success Rate Critical

**Severity**: Critical

**Description**: Batch processing success rate has fallen below acceptable threshold.

**Prometheus Query**:
```yaml
alert: BatchSuccessRateCritical
expr: (sum(rate(bulk_submission_success_total{application="order-service"}[5m])) / sum(rate(bulk_submission_total{application="order-service"}[5m]))) < 0.90
for: 5m
labels:
  severity: critical
  component: batch-processing
  team: backend
annotations:
  summary: "Batch success rate critical on {{ $labels.instance }}"
  description: "Success rate is {{ $value | humanizePercentage }} (threshold: 90%)"
  impact: "High failure rate affecting order processing"
  runbook_url: "https://wiki.company.com/runbooks/low-success-rate"
```

**Threshold**: < 90% success rate

**Duration**: 5 minutes

**Response Procedure**:
1. Check error logs for patterns
2. Review error rate by type
3. Check external service health
4. Follow appropriate error-specific runbook

**Escalation**: Page on-call engineer immediately

---

## Warning Alerts

### WARNING-001: High Connection Pool Utilization

**Severity**: Warning

**Description**: Connection pool utilization is elevated but not yet critical.

**Prometheus Query**:
```yaml
alert: HighConnectionPoolUtilization
expr: (hikaricp_connections_active{application="order-service"} / hikaricp_connections{application="order-service"}) > 0.75
for: 5m
labels:
  severity: warning
  component: database
  team: backend
annotations:
  summary: "Connection pool utilization high on {{ $labels.instance }}"
  description: "Connection pool utilization is {{ $value | humanizePercentage }} (threshold: 75%)"
  impact: "Approaching capacity, monitor closely"
  runbook_url: "https://wiki.company.com/runbooks/high-utilization"
```

**Threshold**: 75% utilization

**Duration**: 5 minutes

**Response Procedure**:
1. Monitor utilization trend
2. Check current load
3. Review batch processing metrics
4. Prepare for potential intervention
5. Document in incident channel

**Escalation**: Slack notification to #backend-alerts

---

### WARNING-002: Threads Waiting for Connections

**Severity**: Warning

**Description**: Some threads are waiting for connections, indicating early contention.

**Prometheus Query**:
```yaml
alert: ThreadsWaitingForConnections
expr: hikaricp_connections_pending{application="order-service"} > 2
for: 3m
labels:
  severity: warning
  component: database
  team: backend
annotations:
  summary: "{{ $value }} threads waiting for connections on {{ $labels.instance }}"
  description: "Threads are waiting for database connections"
  impact: "Potential latency increase"
  runbook_url: "https://wiki.company.com/runbooks/threads-waiting"
```

**Threshold**: > 2 threads waiting

**Duration**: 3 minutes

**Response Procedure**:
1. Monitor thread wait count
2. Check connection pool utilization
3. Review active operations
4. Prepare for escalation if worsens

**Escalation**: Slack notification to #backend-alerts

---

### WARNING-003: Batch Success Rate Low

**Severity**: Warning

**Description**: Batch processing success rate is below target but above critical threshold.

**Prometheus Query**:
```yaml
alert: BatchSuccessRateLow
expr: (sum(rate(bulk_submission_success_total{application="order-service"}[5m])) / sum(rate(bulk_submission_total{application="order-service"}[5m]))) < 0.95
for: 10m
labels:
  severity: warning
  component: batch-processing
  team: backend
annotations:
  summary: "Batch success rate low on {{ $labels.instance }}"
  description: "Success rate is {{ $value | humanizePercentage }} (threshold: 95%)"
  impact: "Elevated failure rate"
  runbook_url: "https://wiki.company.com/runbooks/low-success-rate"
```

**Threshold**: < 95% success rate

**Duration**: 10 minutes

**Response Procedure**:
1. Review error logs
2. Check error distribution by type
3. Monitor for escalation to critical
4. Investigate root cause

**Escalation**: Slack notification to #backend-alerts

---

### WARNING-004: High Batch Processing Latency

**Severity**: Warning

**Description**: Batch processing latency (P95) is elevated above target.

**Prometheus Query**:
```yaml
alert: HighBatchProcessingLatency
expr: histogram_quantile(0.95, rate(bulk_submission_duration_seconds_bucket{application="order-service"}[5m])) > 8
for: 10m
labels:
  severity: warning
  component: batch-processing
  team: backend
annotations:
  summary: "High batch processing latency on {{ $labels.instance }}"
  description: "P95 latency is {{ $value }}s (threshold: 8s)"
  impact: "Slower order processing"
  runbook_url: "https://wiki.company.com/runbooks/high-latency"
```

**Threshold**: P95 > 8 seconds

**Duration**: 10 minutes

**Response Procedure**:
1. Check transaction duration breakdown
2. Identify slow phase (load, service call, update)
3. Review database query performance
4. Check external service response times

**Escalation**: Slack notification to #backend-alerts

---

### WARNING-005: High Semaphore Wait Time

**Severity**: Warning

**Description**: Average semaphore wait time is elevated, indicating queueing.

**Prometheus Query**:
```yaml
alert: HighSemaphoreWaitTime
expr: (rate(semaphore_wait_duration_seconds_sum{application="order-service"}[5m]) / rate(semaphore_wait_duration_seconds_count{application="order-service"}[5m])) > 0.5
for: 5m
labels:
  severity: warning
  component: concurrency
  team: backend
annotations:
  summary: "High semaphore wait time on {{ $labels.instance }}"
  description: "Average wait time is {{ $value }}s (threshold: 0.5s)"
  impact: "Requests queueing, potential latency increase"
  runbook_url: "https://wiki.company.com/runbooks/semaphore-wait"
```

**Threshold**: > 0.5 seconds average

**Duration**: 5 minutes

**Response Procedure**:
1. Check semaphore available permits
2. Review concurrent operation count
3. Check connection pool utilization
4. Consider adjusting semaphore limit

**Escalation**: Slack notification to #backend-alerts

---

### WARNING-006: Circuit Breaker Frequent Transitions

**Severity**: Warning

**Description**: Circuit breaker is transitioning states frequently, indicating instability.

**Prometheus Query**:
```yaml
alert: CircuitBreakerFrequentTransitions
expr: increase(circuit_breaker_state_transitions_total{application="order-service"}[1h]) > 10
for: 5m
labels:
  severity: warning
  component: circuit-breaker
  team: backend
annotations:
  summary: "Frequent circuit breaker transitions on {{ $labels.instance }}"
  description: "{{ $value }} state transitions in the last hour (threshold: 10)"
  impact: "System instability, intermittent service degradation"
  runbook_url: "https://wiki.company.com/runbooks/circuit-breaker-flapping"
```

**Threshold**: > 10 transitions per hour

**Duration**: 5 minutes

**Response Procedure**:
1. Review connection pool utilization patterns
2. Check for load spikes
3. Review circuit breaker configuration
4. Consider threshold adjustments

**Escalation**: Slack notification to #backend-alerts

---

### WARNING-007: Connection Leak Detected

**Severity**: Warning

**Description**: HikariCP has detected a potential connection leak.

**Prometheus Query**:
```yaml
alert: ConnectionLeakDetected
expr: increase(hikaricp_connections_timeout_total{application="order-service"}[10m]) > 0
for: 1m
labels:
  severity: warning
  component: database
  team: backend
annotations:
  summary: "Connection leak detected on {{ $labels.instance }}"
  description: "{{ $value }} connection leak(s) detected in the last 10 minutes"
  impact: "Potential connection pool exhaustion"
  runbook_url: "https://wiki.company.com/runbooks/connection-leak"
```

**Threshold**: > 0 leaks in 10 minutes

**Duration**: 1 minute

**Response Procedure**:
1. Review application logs for leak stack traces
2. Identify code path causing leak
3. Check for missing @Transactional annotations
4. Prepare hotfix if needed
5. Monitor for recurrence

**Escalation**: Slack notification to #backend-alerts

---

### WARNING-008: Database Query Performance Degraded

**Severity**: Warning

**Description**: Database query execution time is elevated.

**Prometheus Query**:
```yaml
alert: DatabaseQueryPerformanceDegraded
expr: rate(database_query_duration_seconds_sum{application="order-service",query_type="eager_fetch"}[5m]) / rate(database_query_duration_seconds_count{application="order-service",query_type="eager_fetch"}[5m]) > 2
for: 10m
labels:
  severity: warning
  component: database
  team: backend
annotations:
  summary: "Database query performance degraded on {{ $labels.instance }}"
  description: "Average query time is {{ $value }}s (threshold: 2s)"
  impact: "Slower order loading"
  runbook_url: "https://wiki.company.com/runbooks/slow-queries"
```

**Threshold**: > 2 seconds average

**Duration**: 10 minutes

**Response Procedure**:
1. Check database CPU and I/O
2. Review slow query log
3. Check for missing indexes
4. Engage database team if needed

**Escalation**: Slack notification to #backend-alerts

---

### WARNING-009: HTTP Connection Pool Saturation

**Severity**: Warning

**Description**: HTTP client connection pool is approaching capacity.

**Prometheus Query**:
```yaml
alert: HttpConnectionPoolSaturation
expr: (http_client_connections_active{application="order-service"} / http_client_connections_max{application="order-service"}) > 0.80
for: 5m
labels:
  severity: warning
  component: http-client
  team: backend
annotations:
  summary: "HTTP connection pool saturation on {{ $labels.instance }}"
  description: "HTTP pool utilization is {{ $value | humanizePercentage }} (threshold: 80%)"
  impact: "Potential delays in external service calls"
  runbook_url: "https://wiki.company.com/runbooks/http-pool-saturation"
```

**Threshold**: 80% utilization

**Duration**: 5 minutes

**Response Procedure**:
1. Check external service response times
2. Review HTTP connection pool configuration
3. Consider increasing pool size
4. Check for connection leaks

**Escalation**: Slack notification to #backend-alerts

---

## Info Alerts

### INFO-001: Connection Pool Configuration Changed

**Severity**: Info

**Description**: Connection pool configuration has been modified.

**Prometheus Query**:
```yaml
alert: ConnectionPoolConfigurationChanged
expr: changes(hikaricp_connections{application="order-service"}[5m]) > 0
for: 1m
labels:
  severity: info
  component: configuration
  team: backend
annotations:
  summary: "Connection pool configuration changed on {{ $labels.instance }}"
  description: "Connection pool max size changed to {{ $value }}"
  impact: "Configuration change, monitor for effects"
```

**Threshold**: Configuration change detected

**Duration**: 1 minute

**Response Procedure**:
1. Verify change was intentional
2. Monitor connection pool metrics
3. Document change in change log

**Escalation**: Email to backend-team@company.com

---

### INFO-002: Service Deployment

**Severity**: Info

**Description**: Order Service has been deployed.

**Prometheus Query**:
```yaml
alert: ServiceDeployment
expr: changes(up{application="order-service"}[2m]) > 0
for: 1m
labels:
  severity: info
  component: deployment
  team: backend
annotations:
  summary: "Order Service deployed on {{ $labels.instance }}"
  description: "Service restart detected"
  impact: "Brief service interruption during deployment"
```

**Threshold**: Service restart detected

**Duration**: 1 minute

**Response Procedure**:
1. Verify deployment was planned
2. Monitor service health
3. Check for errors in logs

**Escalation**: Email to backend-team@company.com

---

## Alert Routing

### PagerDuty Integration

**Critical Alerts**:
```yaml
route:
  receiver: pagerduty-critical
  group_by: ['alertname', 'instance']
  group_wait: 10s
  group_interval: 5m
  repeat_interval: 4h
  routes:
    - match:
        severity: critical
      receiver: pagerduty-critical
      continue: true
```

### Slack Integration

**Warning Alerts**:
```yaml
route:
  receiver: slack-backend-alerts
  group_by: ['alertname']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 12h
  routes:
    - match:
        severity: warning
      receiver: slack-backend-alerts
```

### Email Integration

**Info Alerts**:
```yaml
route:
  receiver: email-backend-team
  group_by: ['alertname']
  group_wait: 5m
  group_interval: 1h
  repeat_interval: 24h
  routes:
    - match:
        severity: info
      receiver: email-backend-team
```

## Alert Testing

### Testing Critical Alerts

**Connection Pool Exhausted**:
```bash
# Temporarily reduce pool size
kubectl set env deployment/order-service SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10

# Generate load
for i in {1..50}; do
  curl -X POST http://localhost:8080/api/v1/orders/batch -d @test-batch.json &
done

# Verify alert fires
# Check PagerDuty for alert

# Restore configuration
kubectl set env deployment/order-service SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=60
```

**Circuit Breaker Open**:
```bash
# Trigger circuit breaker (same as above)
# Verify alert fires within 1 minute
# Verify alert resolves after recovery
```

### Testing Warning Alerts

**High Connection Pool Utilization**:
```bash
# Generate moderate load
for i in {1..20}; do
  curl -X POST http://localhost:8080/api/v1/orders/batch -d @test-batch.json &
done

# Verify alert fires after 5 minutes
# Verify alert resolves when load decreases
```

## Alert Maintenance

### Monthly Review

1. **Review Alert Frequency**
   - Check how often each alert fired
   - Identify noisy alerts
   - Adjust thresholds if needed

2. **Review False Positives**
   - Document false positive incidents
   - Adjust duration or threshold
   - Consider removing if consistently false

3. **Review Response Times**
   - Check average time to acknowledge
   - Check average time to resolve
   - Identify bottlenecks in response

4. **Update Runbooks**
   - Document new scenarios
   - Update procedures based on learnings
   - Add new troubleshooting steps

### Quarterly Optimization

1. **Threshold Tuning**
   - Analyze historical data
   - Adjust thresholds based on patterns
   - Balance sensitivity vs noise

2. **New Alert Creation**
   - Identify gaps in monitoring
   - Create alerts for new scenarios
   - Test thoroughly before production

3. **Alert Consolidation**
   - Identify redundant alerts
   - Combine related alerts
   - Simplify alert structure

## Alert Metrics

### Alert Performance Metrics

Track these metrics for alert effectiveness:

- **Mean Time to Detect (MTTD)**: Time from issue start to alert
- **Mean Time to Acknowledge (MTTA)**: Time from alert to acknowledgment
- **Mean Time to Resolve (MTTR)**: Time from alert to resolution
- **False Positive Rate**: Percentage of alerts that were false positives
- **Alert Fatigue Score**: Frequency of alerts per engineer

### Target Metrics

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| MTTD | < 2 minutes | TBD | 🟡 |
| MTTA | < 5 minutes | TBD | 🟡 |
| MTTR | < 15 minutes | TBD | 🟡 |
| False Positive Rate | < 5% | TBD | 🟡 |
| Alerts per Week | < 10 | TBD | 🟡 |

## References

- Prometheus Alerting: https://prometheus.io/docs/alerting/latest/overview/
- PagerDuty Best Practices: https://www.pagerduty.com/resources/learn/call-to-action/
- Operational Runbook: See OPERATIONAL_RUNBOOK.md
- Circuit Breaker Guide: See CIRCUIT_BREAKER_GUIDE.md
