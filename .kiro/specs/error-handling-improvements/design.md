# Design Document

## Overview

This design implements proper HTTP error handling for the Order Service by correcting status codes, implementing structured error responses, and adding system overload detection. The solution addresses the core issue where system overload conditions incorrectly return 400 Bad Request instead of 503 Service Unavailable, preventing proper client retry behavior.

The design focuses on minimal changes to existing code while establishing a robust error handling framework that can be extended for future error scenarios.

## Architecture

### Current Error Handling Analysis

Based on code analysis, the current system:
- Uses `ResponseEntity.badRequest()` for various error conditions in `OrderController`
- Has comprehensive validation logic for request parameters
- Lacks system overload detection mechanisms
- Returns simple Map-based error responses without structured format
- Does not distinguish between client errors and system capacity issues

### Proposed Error Handling Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   HTTP Request  │───▶│  Error Handler   │───▶│ Structured      │
│                 │    │  Interceptor     │    │ Error Response  │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │ System Overload  │
                       │ Detection        │
                       └──────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │ Error            │
                       │ Classification   │
                       └──────────────────┘
```

## Components and Interfaces

### 1. Error Response DTOs

**ErrorResponseDTO** - Standardized error response structure:
```java
public class ErrorResponseDTO {
    private String code;           // Error code (e.g., "SERVICE_OVERLOADED")
    private String message;        // Human-readable error message
    private Integer retryAfter;    // Retry delay in seconds (optional)
    private String timestamp;      // ISO timestamp
    private Map<String, Object> details; // Additional error details (optional)
}
```

**SystemOverloadException** - Custom exception for overload conditions:
```java
public class SystemOverloadException extends RuntimeException {
    private final int retryAfterSeconds;
    private final String overloadReason;
}
```

### 2. System Overload Detection Service

**SystemOverloadDetector** - Monitors system resources and determines overload state:
```java
@Service
public class SystemOverloadDetector {
    // Monitor thread pool utilization
    public boolean isThreadPoolOverloaded();
    
    // Monitor database connection pool
    public boolean isDatabaseConnectionPoolOverloaded();
    
    // Monitor memory usage
    public boolean isMemoryOverloaded();
    
    // Calculate recommended retry delay
    public int calculateRetryDelay();
    
    // Overall overload check
    public boolean isSystemOverloaded();
}
```

### 3. Global Exception Handler

**GlobalExceptionHandler** - Centralized error handling with proper status codes:
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(SystemOverloadException.class)
    public ResponseEntity<ErrorResponseDTO> handleSystemOverload(SystemOverloadException ex);
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationError(IllegalArgumentException ex);
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericError(Exception ex);
}
```

### 4. Enhanced Controller Error Handling

Modify existing controllers to:
- Check for system overload before processing requests
- Throw appropriate exceptions instead of returning ResponseEntity directly
- Maintain backward compatibility with existing error responses

## Data Models

### Error Response Schema

```json
{
  "error": {
    "code": "SERVICE_OVERLOADED",
    "message": "System temporarily overloaded - please retry in a few minutes",
    "retryAfter": 300,
    "timestamp": "2024-01-15T10:30:00Z",
    "details": {
      "overloadReason": "database_connection_pool_exhausted",
      "currentLoad": "95%",
      "recommendedAction": "retry_with_exponential_backoff"
    }
  }
}
```

### System Overload Metrics

Track overload conditions with these metrics:
- `system_overload_total` - Counter of overload occurrences
- `system_overload_duration_seconds` - Histogram of overload duration
- `system_overload_retry_after_seconds` - Gauge of current retry delay
- `system_resource_utilization` - Gauge of resource utilization by type

## Error Handling

### Error Classification Matrix

| Condition | Status Code | Error Code | Retry Behavior |
|-----------|-------------|------------|----------------|
| Invalid request data | 400 | VALIDATION_ERROR | Do not retry |
| System overload | 503 | SERVICE_OVERLOADED | Retry with backoff |
| Rate limiting | 429 | RATE_LIMITED | Retry after delay |
| Database unavailable | 503 | DATABASE_UNAVAILABLE | Retry with backoff |
| External service timeout | 503 | EXTERNAL_SERVICE_TIMEOUT | Retry with backoff |
| Internal errors | 500 | INTERNAL_ERROR | Retry with backoff |

### Overload Detection Logic

```java
public boolean isSystemOverloaded() {
    // Check thread pool utilization (>90% = overloaded)
    if (getThreadPoolUtilization() > 0.9) {
        return true;
    }
    
    // Check database connection pool (>95% = overloaded)
    if (getDatabaseConnectionUtilization() > 0.95) {
        return true;
    }
    
    // Check memory usage (>85% = overloaded)
    if (getMemoryUtilization() > 0.85) {
        return true;
    }
    
    // Check active request count vs capacity
    if (getActiveRequestRatio() > 0.9) {
        return true;
    }
    
    return false;
}
```

### Retry Delay Calculation

```java
public int calculateRetryDelay() {
    double overloadSeverity = Math.max(
        getThreadPoolUtilization(),
        getDatabaseConnectionUtilization(),
        getMemoryUtilization()
    );
    
    // Base delay: 60 seconds
    // Scale up to 300 seconds based on severity
    int baseDelay = 60;
    int maxDelay = 300;
    
    return (int) (baseDelay + (maxDelay - baseDelay) * overloadSeverity);
}
```

## Testing Strategy

### Unit Tests

1. **SystemOverloadDetector Tests**
   - Test overload detection with various resource utilization levels
   - Test retry delay calculation logic
   - Test edge cases (zero utilization, 100% utilization)

2. **GlobalExceptionHandler Tests**
   - Test proper status code mapping for each exception type
   - Test structured error response generation
   - Test Retry-After header inclusion

3. **Controller Integration Tests**
   - Test overload detection integration in controllers
   - Test backward compatibility with existing error responses
   - Test proper exception throwing vs direct ResponseEntity returns

### Integration Tests

1. **Load Testing**
   - Simulate high load conditions to trigger overload detection
   - Verify 503 responses are returned with proper headers
   - Test system recovery after load decreases

2. **Client Retry Testing**
   - Test client retry behavior with corrected status codes
   - Verify exponential backoff works with Retry-After headers
   - Test retry success after system recovery

### Performance Tests

1. **Overload Detection Performance**
   - Measure overhead of overload detection checks
   - Ensure detection doesn't significantly impact response times
   - Test detection accuracy under various load patterns

2. **Error Response Performance**
   - Measure time to generate structured error responses
   - Test memory usage of error response objects
   - Verify no performance regression for normal operations

## Implementation Phases

### Phase 1: Core Error Handling Infrastructure
- Create ErrorResponseDTO and SystemOverloadException classes
- Implement SystemOverloadDetector service
- Create GlobalExceptionHandler with basic error mapping
- Add system overload detection to main endpoints

### Phase 2: Enhanced Error Responses
- Implement structured error response generation
- Add detailed error information and retry guidance
- Integrate with existing metrics and monitoring
- Add comprehensive logging for error conditions

### Phase 3: Advanced Features
- Implement adaptive retry delay calculation
- Add circuit breaker patterns for external services
- Create error rate monitoring and alerting
- Add error response caching for high-frequency errors

## Monitoring and Observability

### Metrics to Implement

1. **Error Classification Metrics**
   - `http_errors_total{status_code, error_code}` - Count of errors by type
   - `system_overload_events_total` - Count of overload events
   - `error_response_generation_duration_seconds` - Time to generate error responses

2. **System Health Metrics**
   - `system_resource_utilization{resource_type}` - Current resource utilization
   - `overload_detection_duration_seconds` - Time spent detecting overload
   - `retry_after_seconds{error_type}` - Current retry delay recommendations

### Alerting Rules

1. **High Error Rate Alert**
   - Trigger when 5xx error rate exceeds 5% over 5 minutes
   - Include error breakdown by type in alert

2. **System Overload Alert**
   - Trigger when overload events exceed 10 per minute
   - Include resource utilization details

3. **Client Impact Alert**
   - Trigger when 503 responses exceed 1% of total requests
   - Monitor client retry success rates

## Backward Compatibility

### Existing API Compatibility

- Maintain existing error response format for validation errors
- Preserve existing HTTP status codes for legitimate client errors
- Ensure existing client integrations continue to work
- Add new structured format as enhancement, not replacement

### Migration Strategy

1. **Soft Launch**: Deploy with feature flag to enable new error handling
2. **Gradual Rollout**: Enable for specific endpoints first
3. **Monitor Impact**: Track client behavior and error rates
4. **Full Deployment**: Enable globally after validation

## Security Considerations

### Error Information Disclosure

- Avoid exposing internal system details in error messages
- Sanitize error details to prevent information leakage
- Log detailed errors internally while returning generic messages to clients

### Rate Limiting Integration

- Coordinate with existing rate limiting mechanisms
- Ensure overload detection doesn't interfere with rate limiting
- Provide clear distinction between rate limiting (429) and overload (503)

## Configuration

### Application Properties

```properties
# Error Handling Configuration
error.handling.enabled=true
error.handling.structured-responses=true
error.handling.include-retry-after=true
error.handling.include-error-details=false

# System Overload Detection
system.overload.detection.enabled=true
system.overload.thread-pool.threshold=0.9
system.overload.database.threshold=0.95
system.overload.memory.threshold=0.85
system.overload.request-ratio.threshold=0.9

# Retry Configuration
system.overload.retry.base-delay-seconds=60
system.overload.retry.max-delay-seconds=300
system.overload.retry.adaptive-calculation=true
```