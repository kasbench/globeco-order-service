# HTTP Request Metrics Debug Endpoint

This document describes the debug endpoint for validating HTTP request metrics. This endpoint is designed for debugging and validation purposes only and should **NOT** be scraped by monitoring systems.

## Configuration

The debug endpoint is controlled by the `metrics.debug.enabled` property:

```properties
# Enable debug metrics endpoint for validation
metrics.debug.enabled=true
```

When enabled, the endpoint is available at `/debug/metrics`.

## Available Endpoints

### 1. Complete Overview
**GET** `/debug/metrics`

Returns a comprehensive overview of all HTTP request metrics including:
- Current timestamp
- Configuration status
- HTTP request counters
- HTTP request timers
- In-flight requests gauge
- Cache statistics
- Service status

Example response:
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "configuration": {
    "globalEnabled": true,
    "httpEnabled": true,
    "httpRequestEnabled": true,
    "routeSanitizationEnabled": true,
    "maxPathSegments": 10,
    "metricCachingEnabled": true,
    "maxCacheSize": 1000,
    "detailedLoggingEnabled": false,
    "collectionInterval": "PT30S"
  },
  "httpRequestCounters": {
    "method=GET,path=/api/orders,status=200": {
      "count": 42,
      "tags": {
        "method": "GET",
        "path": "/api/orders",
        "status": "200"
      },
      "description": "Total number of HTTP requests"
    }
  },
  "httpRequestTimers": {
    "method=GET,path=/api/orders,status=200": {
      "count": 42,
      "totalTimeSeconds": 12.5,
      "meanSeconds": 0.297,
      "maxSeconds": 1.2,
      "tags": {
        "method": "GET",
        "path": "/api/orders",
        "status": "200"
      },
      "description": "Duration of HTTP requests"
    }
  },
  "inFlightRequests": {
    "current": 3,
    "description": "Number of HTTP requests currently being processed"
  },
  "cacheStatistics": {
    "statistics": "HttpRequestMetricsService Cache Stats - Counters: 15, Timers: 12, InFlight: 3",
    "serviceAvailable": true
  },
  "serviceStatus": {
    "initialized": true,
    "inFlightRequests": 3,
    "serviceStatus": "HttpRequestMetricsService Status: ...",
    "serviceAvailable": true
  }
}
```

### 2. Configuration Only
**GET** `/debug/metrics/config`

Returns only the configuration information:
```json
{
  "globalEnabled": true,
  "httpEnabled": true,
  "httpRequestEnabled": true,
  "routeSanitizationEnabled": true,
  "maxPathSegments": 10,
  "metricCachingEnabled": true,
  "maxCacheSize": 1000,
  "detailedLoggingEnabled": false,
  "collectionInterval": "PT30S"
}
```

### 3. HTTP Request Counters
**GET** `/debug/metrics/counters`

Returns all `http_requests_total` counter metrics:
```json
{
  "method=GET,path=/api/orders,status=200": {
    "count": 42,
    "tags": {
      "method": "GET",
      "path": "/api/orders",
      "status": "200"
    },
    "description": "Total number of HTTP requests"
  },
  "method=POST,path=/api/orders,status=201": {
    "count": 15,
    "tags": {
      "method": "POST",
      "path": "/api/orders",
      "status": "201"
    },
    "description": "Total number of HTTP requests"
  }
}
```

### 4. HTTP Request Timers
**GET** `/debug/metrics/timers`

Returns all `http_request_duration` timer metrics:
```json
{
  "method=GET,path=/api/orders,status=200": {
    "count": 42,
    "totalTimeSeconds": 12.5,
    "meanSeconds": 0.297,
    "maxSeconds": 1.2,
    "tags": {
      "method": "GET",
      "path": "/api/orders",
      "status": "200"
    },
    "description": "HTTP request duration"
  }
}
```

### 5. In-Flight Requests
**GET** `/debug/metrics/in-flight`

Returns the current in-flight requests gauge:
```json
{
  "current": 3,
  "description": "Number of HTTP requests currently being processed"
}
```

### 6. Cache Statistics
**GET** `/debug/metrics/cache`

Returns cache statistics from the HTTP request metrics service:
```json
{
  "statistics": "HttpRequestMetricsService Cache Stats - Counters: 15, Timers: 12, InFlight: 3",
  "serviceAvailable": true
}
```

### 7. Service Status
**GET** `/debug/metrics/status`

Returns service status information:
```json
{
  "initialized": true,
  "inFlightRequests": 3,
  "serviceStatus": "HttpRequestMetricsService Status: ...",
  "serviceAvailable": true
}
```

### 8. All HTTP Meters
**GET** `/debug/metrics/all`

Returns all registered meters that start with "http_":
```json
{
  "http_requests_total_method=GET,path=/api/orders,status=200": {
    "name": "http_requests_total",
    "type": "COUNTER",
    "description": "Total number of HTTP requests",
    "tags": {
      "method": "GET",
      "path": "/api/orders",
      "status": "200"
    },
    "count": 42
  },
  "http_request_duration_method=GET,path=/api/orders,status=200": {
    "name": "http_request_duration",
    "type": "TIMER",
    "description": "Duration of HTTP requests",
    "tags": {
      "method": "GET",
      "path": "/api/orders",
      "status": "200"
    },
    "count": 42,
    "totalTimeSeconds": 12.5,
    "meanSeconds": 0.297
  },
  "http_requests_in_flight": {
    "name": "http_requests_in_flight",
    "type": "GAUGE",
    "description": "Number of HTTP requests currently being processed",
    "tags": {},
    "value": 3
  }
}
```

## Usage Examples

### Validate Metrics After Making Requests

1. Make some HTTP requests to your application:
   ```bash
   curl http://localhost:8081/api/orders
   curl http://localhost:8081/api/orders/123
   curl -X POST http://localhost:8081/api/orders -d '{"symbol":"AAPL","quantity":100}'
   ```

2. Check the debug endpoint to see the metrics:
   ```bash
   curl http://localhost:8081/debug/metrics | jq .
   ```

3. Verify specific metrics:
   ```bash
   # Check counters only
   curl http://localhost:8081/debug/metrics/counters | jq .
   
   # Check timers only
   curl http://localhost:8081/debug/metrics/timers | jq .
   
   # Check configuration
   curl http://localhost:8081/debug/metrics/config | jq .
   ```

### Validate Configuration Changes

1. Update configuration in `application.properties`:
   ```properties
   metrics.custom.http.request.max-path-segments=5
   metrics.custom.http.request.metric-caching-enabled=false
   ```

2. Restart the application and check configuration:
   ```bash
   curl http://localhost:8081/debug/metrics/config | jq .maxPathSegments
   curl http://localhost:8081/debug/metrics/config | jq .metricCachingEnabled
   ```

### Monitor Cache Performance

Check cache statistics to validate caching behavior:
```bash
curl http://localhost:8081/debug/metrics/cache | jq .statistics
```

## Security Considerations

- **DO NOT** expose this endpoint in production environments
- **DO NOT** configure monitoring systems to scrape this endpoint
- This endpoint is for debugging and validation only
- Consider disabling it in production by setting `metrics.debug.enabled=false`

## OpenTelemetry Integration

The debug endpoint does not interfere with OpenTelemetry metrics export. All metrics visible in the debug endpoint are also exported to OpenTelemetry collectors as configured in your application properties.

The debug endpoint provides a convenient way to validate that metrics are being created correctly before they are exported to your monitoring infrastructure.
##
 Timer Unit Fix

### Issue
Previously, the `http_request_duration_seconds` metric was being recorded with nanosecond values using `TimeUnit.NANOSECONDS`, which created a unit mismatch. This caused the OpenTelemetry pipeline to add `_milliseconds` to the metric name, resulting in metrics like `http_request_duration_seconds_milliseconds_sum` in Prometheus.

### Root Cause
The issue was caused by the OpenTelemetry Collector's Prometheus exporter configuration setting `add_metric_suffixes: true`, which automatically adds unit suffixes to metric names when it detects unit mismatches or wants to make units explicit.

### Solution
Two changes were made to fix this:

1. **Changed the metric name** from `http_request_duration_seconds` to `http_request_duration` to avoid unit conflicts:
```java
Timer.builder("http_request_duration")  // was: http_request_duration_seconds
    .description("Duration of HTTP requests")  // was: Duration of HTTP requests in seconds
```

2. **Updated OpenTelemetry Collector configuration** to disable automatic metric suffixes:
```yaml
prometheus:
  add_metric_suffixes: false  # was: true
```

3. **Improved timer recording** to use Duration objects:
```java
timer.record(Duration.ofNanos(durationNanos));  // was: timer.record(durationNanos, TimeUnit.NANOSECONDS)
```

This ensures that:
1. The metric name is generic and doesn't imply specific units
2. The OpenTelemetry Collector doesn't add unit suffixes
3. Values are correctly displayed in seconds in both the debug endpoint and Prometheus
4. The metric name remains consistent: `http_request_duration`

### Verification
You can verify the fix by:
1. Making HTTP requests to generate metrics
2. Checking the debug endpoint: `curl http://localhost:8081/debug/metrics/timers`
3. Confirming that timer values are shown in seconds
4. Verifying in Prometheus that the metric name is `http_request_duration` (not `http_request_duration_seconds_milliseconds`)