# HTTP Request Timer Unit Fix Summary

## Problem
The `http_request_duration_seconds` metric was appearing in Prometheus as `http_request_duration_seconds_milliseconds_sum`, indicating that the OpenTelemetry pipeline was detecting a unit mismatch and correcting it by adding unit suffixes.

## Root Cause Analysis
The issue was caused by two factors:

1. **Metric naming convention conflict**: The metric was named `http_request_duration_seconds` (implying seconds) but the OpenTelemetry Collector was detecting the values as being in a different unit.

2. **OpenTelemetry Collector configuration**: The `add_metric_suffixes: true` setting in the Prometheus exporter was automatically adding unit suffixes when it detected unit mismatches or wanted to make units explicit.

## Solution Implemented

### 1. Changed Metric Name (Application Level)
**File**: `src/main/java/org/kasbench/globeco_order_service/service/HttpRequestMetricsService.java`

```java
// Before
Timer.builder("http_request_duration_seconds")
    .description("Duration of HTTP requests in seconds")

// After  
Timer.builder("http_request_duration")
    .description("Duration of HTTP requests")
```

**Rationale**: Using a generic name without unit specification prevents the OpenTelemetry Collector from making assumptions about units and applying automatic corrections.

### 2. Updated OpenTelemetry Collector Configuration
**File**: `documentation/otel-collector.yaml`

```yaml
# Before
prometheus:
  add_metric_suffixes: true

# After
prometheus:
  add_metric_suffixes: false
```

**Rationale**: Disabling automatic metric suffixes prevents the collector from modifying metric names based on detected or inferred units.

### 3. Improved Timer Recording Method
**File**: `src/main/java/org/kasbench/globeco_order_service/service/HttpRequestMetricsService.java`

```java
// Before
timer.record(durationNanos, TimeUnit.NANOSECONDS);

// After
timer.record(Duration.ofNanos(durationNanos));
```

**Rationale**: Using `Duration.ofNanos()` creates a proper Duration object that Micrometer can handle correctly, ensuring proper unit conversion to the timer's base unit.

### 4. Updated All Test Files
Updated all test files to use the new metric name `http_request_duration` instead of `http_request_duration_seconds`:

- `HttpRequestMetricsServiceTest.java`
- `HttpRequestMetricsServiceIntegrationTest.java` 
- `HttpRequestMetricsTimerUnitTest.java`
- `MetricsDebugControllerTest.java`

### 5. Updated Debug Controller
**File**: `src/main/java/org/kasbench/globeco_order_service/controller/MetricsDebugController.java`

Updated the debug controller to look for the new metric name when filtering timer metrics.

### 6. Updated Documentation
**File**: `documentation/metrics-debug-endpoint.md`

Updated all examples and references to use the new metric name and explain the fix.

## Expected Results

After deploying these changes:

### In the Debug Endpoint
```bash
curl http://localhost:8081/debug/metrics/timers
```
Should show metrics with the name `http_request_duration` and values correctly displayed in seconds.

### In Prometheus
The metrics should appear as:
- `http_request_duration_sum` (instead of `http_request_duration_seconds_milliseconds_sum`)
- `http_request_duration_count`
- `http_request_duration_bucket`

### Values
All timer values should be correctly displayed in seconds, matching the debug endpoint output.

## Verification Steps

1. **Deploy the updated application** with the new metric name
2. **Update the OpenTelemetry Collector** with `add_metric_suffixes: false`
3. **Make HTTP requests** to generate metrics
4. **Check the debug endpoint**: `curl http://localhost:8081/debug/metrics/timers`
5. **Verify in Prometheus** that metric names are `http_request_duration_*` without additional unit suffixes
6. **Confirm values** are displayed in seconds in both locations

## Benefits of This Approach

1. **Consistent naming**: Metric names remain stable across the pipeline
2. **No unit conflicts**: Generic naming prevents unit assumption conflicts
3. **Proper value handling**: Duration objects ensure correct unit conversion
4. **Pipeline control**: Explicit control over metric naming in the collector
5. **Maintainability**: Clear separation between application metrics and pipeline processing

## Alternative Approaches Considered

1. **Keep `_seconds` suffix with `add_metric_suffixes: true`**: Would require ensuring perfect unit alignment, which is error-prone.

2. **Use different timer base units**: Would require changing Micrometer configuration, which affects other metrics.

3. **Custom metric transformation in collector**: Would add complexity to the pipeline configuration.

The chosen approach provides the cleanest solution with the least complexity and maximum control over the final metric names.