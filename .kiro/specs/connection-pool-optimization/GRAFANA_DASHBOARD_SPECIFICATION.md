# Grafana Dashboard Specification

## Overview

This document specifies the Grafana dashboard configuration for monitoring the Order Service connection pool optimization. The dashboard provides real-time visibility into connection pool health, batch processing performance, and circuit breaker status.

## Dashboard Layout

### Dashboard Name
**Order Service - Connection Pool Monitoring**

### Refresh Rate
- Default: 5 seconds
- Options: 5s, 10s, 30s, 1m, 5m

### Time Range
- Default: Last 1 hour
- Quick ranges: 5m, 15m, 1h, 6h, 24h, 7d

## Panel Specifications

### Row 1: Connection Pool Overview

#### Panel 1.1: Connection Pool Utilization (Gauge)
```json
{
  "title": "Connection Pool Utilization",
  "type": "gauge",
  "datasource": "Prometheus",
  "targets": [
    {
      "expr": "(hikaricp_connections_active{application=\"order-service\"} / hikaricp_connections{application=\"order-service\"}) * 100",
      "legendFormat": "Utilization %"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "percent",
      "thresholds": {
        "mode": "absolute",
        "steps": [
          { "value": 0, "color": "green" },
          { "value": 75, "color": "yellow" },
          { "value": 90, "color": "red" }
        ]
      }
    }
  }
}
```

**Purpose**: Real-time utilization percentage with color-coded thresholds

**Thresholds**:
- Green: 0-75% (Healthy)
- Yellow: 75-90% (Warning)
- Red: 90-100% (Critical)

#### Panel 1.2: Active Connections (Time Series)
```json
{
  "title": "Active Connections",
  "type": "timeseries",
  "datasource": "Prometheus",
  "targets": [
    {
      "expr": "hikaricp_connections_active{application=\"order-service\"}",
      "legendFormat": "Active"
    },
    {
      "expr": "hikaricp_connections_idle{application=\"order-service\"}",
      "legendFormat": "Idle"
    },
    {
      "expr": "hikaricp_connections{application=\"order-service\"}",
      "legendFormat": "Total"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "short",
      "color": {
        "mode": "palette-classic"
      }
    }
  }
}
```

**Purpose**: Track connection distribution over time

**Metrics**:
- Active: Currently in use
- Idle: Available in pool
- Total: Maximum pool size

#### Panel 1.3: Threads Waiting (Stat)
```json
{
  "title": "Threads Waiting for Connection",
  "type": "stat",
  "datasource": "Prometheus",
  "targets": [
    {
      "expr": "hikaricp_connections_pending{application=\"order-service\"}",
      "legendFormat": "Waiting"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "short",
      "thresholds": {
        "mode": "absolute",
        "steps": [
          { "value": 0, "color": "green" },
          { "value": 3, "color": "yellow" },
          { "value": 5, "color": "red" }
        ]
      }
    }
  }
}
```

**Purpose**: Alert on connection pool contention

**Thresholds**:
- Green: 0-2 threads
- Yellow: 3-4 threads
- Red: 5+ threads

### Row 2: Batch Processing Performance

#### Panel 2.1: Batch Processing Throughput (Time Series)
```json
{
  "title": "Orders Processed per Second",
  "type": "timeseries",
  "datasource": "Prometheus",
  "targets": [
    {
      "expr": "rate(bulk_submission_orders_total{application=\"order-service\"}[1m])",
      "legendFormat": "Orders/sec"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "ops",
      "color": {
        "mode": "palette-classic"
      }
    }
  }
}
```

**Purpose**: Monitor processing throughput

**Target**: >5 orders/second for 25-order batches

#### Panel 2.2: Batch Processing Latency (Time Series)
```json
{
  "title": "Batch Processing Latency",
  "type": "timeseries",
  "datasource": "Prometheus",
  "targets": [
    {
      "expr": "histogram_quantile(0.50, rate(bulk_submission_duration_seconds_bucket{application=\"order-service\"}[5m]))",
      "legendFormat": "P50"
    },
    {
      "expr": "histogram_quantile(0.95, rate(bulk_submission_duration_seconds_bucket{application=\"order-service\"}[5m]))",
      "legendFormat": "P95"
    },
    {
      "expr": "histogram_quantile(0.99, rate(bulk_submission_duration_seconds_bucket{application=\"order-service\"}[5m]))",
      "legendFormat": "P99"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "s",
      "color": {
        "mode": "palette-classic"
      }
    }
  }
}
```

**Purpose**: Track latency distribution

**Targets**:
- P50: <3 seconds
- P95: <5 seconds
- P99: <8 seconds

#### Panel 2.3: Success Rate (Gauge)
```json
{
  "title": "Batch Success Rate",
  "type": "gauge",
  "datasource": "Prometheus",
  "targets": [
    {
      "expr": "(sum(rate(bulk_submission_success_total{application=\"order-service\"}[5m])) / sum(rate(bulk_submission_total{application=\"order-service\"}[5m]))) * 100",
      "legendFormat": "Success %"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "percent",
      "thresholds": {
        "mode": "absolute",
        "steps": [
          { "value": 0, "color": "red" },
          { "value": 95, "color": "yellow" },
          { "value": 99, "color": "green" }
        ]
      }
    }
  }
}
```

**Purpose**: Monitor reliability

**Thresholds**:
- Red: <95%
- Yellow: 95-99%
- Green: >99%

### Row 3: Transaction Performance

#### Panel 3.1: Transaction Duration Breakdown (Time Series)
```json
{
  "title": "Transaction Duration by Phase",
  "type": "timeseries",
  "datasource": "Prometheus",
  "targets": [
    {
      "expr": "rate(bulk_submission_order_load_duration_seconds_sum{application=\"order-service\"}[5m]) / rate(bulk_submission_order_load_duration_seconds_count{application=\"order-service\"}[5m])",
      "legendFormat": "Order Load (Read)"
    },
    {
      "expr": "rate(bulk_submission_trade_service_duration_seconds_sum{application=\"order-service\"}[5m]) / rate(bulk_submission_trade_service_duration_seconds_count{application=\"order-service\"}[5m])",
      "legendFormat": "Trade Service Call"
    },
    {
      "expr": "rate(bulk_submission_database_update_duration_seconds_sum{application=\"order-service\"}[5m]) / rate(bulk_submission_database_update_duration_seconds_count{application=\"order-service\"}[5m])",
      "legendFormat": "Database Update (Write)"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "s",
      "color": {
        "mode": "palette-classic"
      }
    }
  }
}
```

**Purpose**: Identify bottlenecks in processing pipeline

**Targets**:
- Order Load: <1 second
- Trade Service: <3 seconds
- Database Update: <1 second

#### Panel 3.2: Database Query Performance (Time Series)
```json
{
  "title": "Database Query Execution Time",
  "type": "timeseries",
  "datasource": "Prometheus",
  "targets": [
    {
      "expr": "rate(database_query_duration_seconds_sum{application=\"order-service\",query_type=\"eager_fetch\"}[5m]) / rate(database_query_duration_seconds_count{application=\"order-service\",query_type=\"eager_fetch\"}[5m])",
      "legendFormat": "Eager Fetch"
    },
    {
      "expr": "rate(database_query_duration_seconds_sum{application=\"order-service\",query_type=\"batch_update\"}[5m]) / rate(database_query_duration_seconds_count{application=\"order-service\",query_type=\"batch_update\"}[5m])",
      "legendFormat": "Batch Update"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "s",
      "color": {
        "mode": "palette-classic"
      }
    }
  }
}
```

**Purpose**: Monitor query optimization effectiveness

### Row 4: Semaphore and Concurrency

#### Panel 4.1: Semaphore Available Permits (Time Series)
```json
{
  "title": "Semaphore Available Permits",
  "type": "timeseries",
  "datasource": "Prometheus",
  "targets": [
    {
      "expr": "semaphore_available_permits{application=\"order-service\"}",
      "legendFormat": "Available"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "short",
      "min": 0,
      "max": 15,
      "color": {
        "mode": "palette-classic"
      }
    }
  }
}
```

**Purpose**: Monitor concurrency control

**Expected**: Fluctuates between 0-15

#### Panel 4.2: Semaphore Wait Time (Time Series)
```json
{
  "title": "Semaphore Wait Time",
  "type": "timeseries",
  "datasource": "Prometheus",
  "targets": [
    {
      "expr": "rate(semaphore_wait_duration_seconds_sum{application=\"order-service\"}[5m]) / rate(semaphore_wait_duration_seconds_count{application=\"order-service\"}[5m])",
      "legendFormat": "Avg Wait Time"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "s",
      "color": {
        "mode": "palette-classic"
      }
    }
  }
}
```

**Purpose**: Detect queueing issues

**Target**: <100ms average

#### Panel 4.3: Semaphore Queue Events (Stat)
```json
{
  "title": "Semaphore Queue Events (5m)",
  "type": "stat",
  "datasource": "Prometheus",
  "targets": [
    {
      "expr": "increase(semaphore_wait_total{application=\"order-service\"}[5m])",
      "legendFormat": "Queue Events"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "short",
      "thresholds": {
        "mode": "absolute",
        "steps": [
          { "value": 0, "color": "green" },
          { "value": 10, "color": "yellow" },
          { "value": 50, "color": "red" }
        ]
      }
    }
  }
}
```

**Purpose**: Alert on excessive queueing

### Row 5: Circuit Breaker and Health

#### Panel 5.1: Circuit Breaker State (Stat)
```json
{
  "title": "Circuit Breaker State",
  "type": "stat",
  "datasource": "Prometheus",
  "targets": [
    {
      "expr": "circuit_breaker_state{application=\"order-service\"}",
      "legendFormat": "State"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "mappings": [
        { "value": 0, "text": "CLOSED", "color": "green" },
        { "value": 1, "text": "OPEN", "color": "red" },
        { "value": 2, "text": "HALF_OPEN", "color": "yellow" }
      ]
    }
  }
}
```

**Purpose**: Monitor circuit breaker status

**States**:
- CLOSED (0): Normal operation
- OPEN (1): Protection active
- HALF_OPEN (2): Testing recovery

#### Panel 5.2: Circuit Breaker Events (Time Series)
```json
{
  "title": "Circuit Breaker Events",
  "type": "timeseries",
  "datasource": "Prometheus",
  "targets": [
    {
      "expr": "increase(circuit_breaker_state_transitions_total{application=\"order-service\",to_state=\"OPEN\"}[5m])",
      "legendFormat": "Opens"
    },
    {
      "expr": "increase(circuit_breaker_state_transitions_total{application=\"order-service\",to_state=\"CLOSED\"}[5m])",
      "legendFormat": "Closes"
    },
    {
      "expr": "increase(circuit_breaker_rejections_total{application=\"order-service\"}[5m])",
      "legendFormat": "Rejections"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "short",
      "color": {
        "mode": "palette-classic"
      }
    }
  }
}
```

**Purpose**: Track protection activations

**Target**: <1 open event per hour

#### Panel 5.3: HTTP Connection Pool (Time Series)
```json
{
  "title": "HTTP Connection Pool",
  "type": "timeseries",
  "datasource": "Prometheus",
  "targets": [
    {
      "expr": "http_client_connections_active{application=\"order-service\"}",
      "legendFormat": "Active"
    },
    {
      "expr": "http_client_connections_idle{application=\"order-service\"}",
      "legendFormat": "Idle"
    },
    {
      "expr": "http_client_connections_pending{application=\"order-service\"}",
      "legendFormat": "Pending"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "short",
      "color": {
        "mode": "palette-classic"
      }
    }
  }
}
```

**Purpose**: Monitor external service connection pooling

### Row 6: Error Tracking

#### Panel 6.1: Error Rate (Time Series)
```json
{
  "title": "Error Rate by Type",
  "type": "timeseries",
  "datasource": "Prometheus",
  "targets": [
    {
      "expr": "rate(bulk_submission_errors_total{application=\"order-service\",error_type=\"connection_timeout\"}[5m])",
      "legendFormat": "Connection Timeout"
    },
    {
      "expr": "rate(bulk_submission_errors_total{application=\"order-service\",error_type=\"transaction_timeout\"}[5m])",
      "legendFormat": "Transaction Timeout"
    },
    {
      "expr": "rate(bulk_submission_errors_total{application=\"order-service\",error_type=\"validation_error\"}[5m])",
      "legendFormat": "Validation Error"
    },
    {
      "expr": "rate(bulk_submission_errors_total{application=\"order-service\",error_type=\"external_service_error\"}[5m])",
      "legendFormat": "External Service Error"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "ops",
      "color": {
        "mode": "palette-classic"
      }
    }
  }
}
```

**Purpose**: Identify error patterns

## Alert Rules

### Critical Alerts

#### Connection Pool Exhaustion
```yaml
- alert: ConnectionPoolExhausted
  expr: (hikaricp_connections_active / hikaricp_connections) > 0.90
  for: 2m
  labels:
    severity: critical
  annotations:
    summary: "Connection pool utilization critical"
    description: "Connection pool utilization is {{ $value | humanizePercentage }} for {{ $labels.application }}"
```

#### Circuit Breaker Open
```yaml
- alert: CircuitBreakerOpen
  expr: circuit_breaker_state == 1
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "Circuit breaker is OPEN"
    description: "Circuit breaker has opened for {{ $labels.application }}, rejecting requests"
```

#### High Thread Wait Count
```yaml
- alert: HighThreadWaitCount
  expr: hikaricp_connections_pending > 5
  for: 2m
  labels:
    severity: critical
  annotations:
    summary: "High number of threads waiting for connections"
    description: "{{ $value }} threads are waiting for database connections"
```

### Warning Alerts

#### High Connection Pool Utilization
```yaml
- alert: HighConnectionPoolUtilization
  expr: (hikaricp_connections_active / hikaricp_connections) > 0.75
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Connection pool utilization high"
    description: "Connection pool utilization is {{ $value | humanizePercentage }}"
```

#### Low Success Rate
```yaml
- alert: LowBatchSuccessRate
  expr: (sum(rate(bulk_submission_success_total[5m])) / sum(rate(bulk_submission_total[5m]))) < 0.95
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Batch processing success rate low"
    description: "Success rate is {{ $value | humanizePercentage }}"
```

#### High Semaphore Wait Time
```yaml
- alert: HighSemaphoreWaitTime
  expr: (rate(semaphore_wait_duration_seconds_sum[5m]) / rate(semaphore_wait_duration_seconds_count[5m])) > 0.5
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "High semaphore wait time"
    description: "Average semaphore wait time is {{ $value }}s"
```

## Dashboard Variables

### Application Filter
```json
{
  "name": "application",
  "type": "query",
  "datasource": "Prometheus",
  "query": "label_values(hikaricp_connections, application)",
  "multi": false,
  "includeAll": false
}
```

### Instance Filter
```json
{
  "name": "instance",
  "type": "query",
  "datasource": "Prometheus",
  "query": "label_values(hikaricp_connections{application=\"$application\"}, instance)",
  "multi": true,
  "includeAll": true
}
```

## Dashboard JSON Export

The complete dashboard JSON can be imported into Grafana. Save the dashboard configuration and export as JSON for version control and deployment automation.

## Usage Guidelines

### Daily Monitoring
- Check connection pool utilization trends
- Review batch processing throughput
- Monitor success rates

### Incident Response
- Check circuit breaker state first
- Review error rate panel for patterns
- Examine transaction duration breakdown
- Check threads waiting metric

### Performance Tuning
- Compare before/after optimization metrics
- Track P95/P99 latency improvements
- Monitor throughput increases
- Validate utilization stays below 75%

## References

- Grafana Documentation: https://grafana.com/docs/
- Prometheus Query Language: https://prometheus.io/docs/prometheus/latest/querying/basics/
- HikariCP Metrics: https://github.com/brettwooldridge/HikariCP/wiki/MBean-(JMX)-Monitoring-and-Management
