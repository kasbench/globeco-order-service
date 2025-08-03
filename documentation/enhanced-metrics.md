# Microservices HTTP Metrics Requirements

## Overview

All microservices must implement standardized HTTP request metrics that can be exported to the OpenTelemetry (Otel) Collector. These metrics provide consistent observability across services regardless of implementation language or framework.

## Required Metrics

### 1. HTTP Requests Total Counter

- **Metric Name**: `http_requests_total`
- **Type**: Counter
- **Description**: "Total number of HTTP requests"
- **Labels**:
    - `method`: HTTP method (GET, POST, PUT, DELETE, etc.)
    - `path`: Request path/route (e.g., "/api/users", "/health") _(Note: May be transformed from `endpoint` by OTel pipeline)_
    - `status`: HTTP status code as string (e.g., "200", "404", "500")
- **Behavior**: Increment by 1 for each completed HTTP request

### 2. HTTP Request Duration Histogram

- **Metric Name**: `http_request_duration_seconds`
- **Type**: Histogram
- **Description**: "Duration of HTTP requests in seconds"
- **Labels**:
    - `method`: HTTP method (GET, POST, PUT, DELETE, etc.)
    - `path`: Request path/route (e.g., "/api/users", "/health") _(Note: May be transformed from `endpoint` by OTel pipeline)_
    - `status`: HTTP status code as string (e.g., "200", "404", "500")
- **Unit**: Seconds (floating point)
- **Buckets**: Use framework/library default histogram buckets or equivalent to: [0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10]
- **Behavior**: Record the duration of each HTTP request from start to completion

### 3. HTTP Requests In Flight Gauge

- **Metric Name**: `http_requests_in_flight`
- **Type**: Gauge
- **Description**: "Number of HTTP requests currently being processed"
- **Labels**: None
- **Behavior**:
    - Increment when request processing begins
    - Decrement when request processing completes (regardless of success/failure)

## Implementation Requirements

### Middleware/Filter Integration

- Implement as HTTP middleware, filter, or interceptor that wraps all HTTP endpoints
- Ensure metrics are recorded for ALL HTTP requests, including:
    - API endpoints
    - Health checks
    - Static file serving
    - Error responses (4xx, 5xx)

### Timing Accuracy

- Start timing when the request enters the application layer
- Stop timing when the response is fully written
- Use high-precision timing (microsecond accuracy where possible)

### Label Value Guidelines

- **Method**: Use uppercase HTTP method names (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS)
- **Endpoint**:
    - Use the route pattern, not the actual URL with parameters
    - Example: Use "/api/users/{id}" instead of "/api/users/123"
    - For frameworks that don't provide route patterns, use the actual path but sanitize sensitive data
    - **Note**: This label may be transformed to `path` by the OpenTelemetry pipeline
- **Status**: Convert numeric HTTP status codes to strings ("200", "404", "500")

### Error Handling

- Metrics recording must not interfere with normal request processing
- If metric recording fails, log the error but continue processing the request
- Ensure metrics are recorded even for requests that result in exceptions/errors

### Performance Considerations

- Minimize overhead of metrics collection
- Use efficient label value extraction
- Avoid creating high-cardinality metrics (limit unique endpoint values)

### Export Configuration

- Metrics must be exportable to the existing Otel Collector
- Follow OpenTelemetry semantic conventions where applicable
- Ensure metrics are properly registered/initialized at application startup

## Framework-Specific Notes

### Go (chi/gorilla/gin/echo)

- Implement as HTTP middleware function
- Use request context for timing and in-flight tracking

### Python (FastAPI/Flask/Django)

- Implement as ASGI/WSGI middleware or decorator
- Consider async/await patterns for proper timing

### Java (Spring Boot)

- Implement as Filter or HandlerInterceptor
- Use Spring's built-in metrics integration where possible
- Consider MeterRegistry for metric registration

### JavaScript/TypeScript (Next.js/Express)

- Implement as middleware function
- Handle both API routes and page requests
- Consider Next.js specific routing patterns

## Validation Requirements

### Testing

Each implementation must be tested to verify:

1. All three metrics are created and registered
2. Counter increments correctly for each request
3. Histogram records accurate durations
4. Gauge properly tracks concurrent requests
5. Labels contain correct values
6. Metrics are exported to Otel Collector

### Monitoring

Once deployed, verify metrics are:

- Appearing in monitoring dashboards
- Showing expected patterns and values
- Compatible with existing alerting rules
- Properly aggregated across service instances

## Example Expected Metrics Output

```
# HELP http_requests_total Total number of HTTP requests
# TYPE http_requests_total counter
http_requests_total{method="GET",path="/api/health",status="200"} 1523
http_requests_total{method="POST",path="/api/users",status="201"} 45
http_requests_total{method="GET",path="/api/users/{id}",status="404"} 12

# HELP http_request_duration_seconds Duration of HTTP requests in seconds
# TYPE http_request_duration_seconds histogram
http_request_duration_seconds_bucket{method="GET",path="/api/health",status="200",le="0.005"} 1200
http_request_duration_seconds_bucket{method="GET",path="/api/health",status="200",le="0.01"} 1520
# ... additional buckets

# HELP http_requests_in_flight Number of HTTP requests currently being processed
# TYPE http_requests_in_flight gauge
http_requests_in_flight 3
```

This standardization ensures consistent observability and monitoring capabilities across all microservices in the system.