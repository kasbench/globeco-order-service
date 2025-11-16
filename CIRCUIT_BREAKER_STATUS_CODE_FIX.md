# Circuit Breaker Status Code Fix

## Problem

The calling service was receiving HTTP 400 (Bad Request) errors when the Order Service circuit breaker was open, with the error message:

```
Error: Invalid order data: System temporarily overloaded - please retry in a few minutes
```

This caused issues because:
1. **400 is a client error** - indicates the request was malformed, not that the service is temporarily unavailable
2. **Retry logic doesn't trigger** - clients treat 400 as a permanent error and don't retry
3. **Misleading error message** - "Invalid order data" suggests a data validation problem, not a service overload

## Solution

Changed the circuit breaker response to return **HTTP 503 (Service Unavailable)** instead of 400, which:
1. **Correctly indicates temporary unavailability** - 503 is the standard status for service overload
2. **Triggers automatic retry logic** - clients will retry 503 errors with exponential backoff
3. **Includes Retry-After header** - tells clients when to retry (180 seconds / 3 minutes)

## Changes Made

### BatchProcessingService.java

**Before:**
```java
if (!circuitBreaker.allowOperation()) {
    logger.error("Circuit breaker OPEN - rejecting batch of {} orders to protect system", orders.size());
    return OrderListResponseDTO.validationFailure(
        "System temporarily overloaded - please retry in a few minutes");
}
```

**After:**
```java
if (!circuitBreaker.allowOperation()) {
    logger.error("Circuit breaker OPEN - rejecting batch of {} orders to protect system", orders.size());
    // Throw SystemOverloadException to return 503 status code instead of 400
    throw new SystemOverloadException(
        "System temporarily overloaded - please retry in a few minutes",
        180, // 3 minutes retry delay
        "circuit_breaker_open"
    );
}
```

### How It Works

1. **Circuit breaker opens** when connection pool utilization exceeds threshold
2. **BatchProcessingService throws SystemOverloadException** instead of returning validation failure
3. **GlobalExceptionHandler catches the exception** and returns:
   - HTTP Status: **503 Service Unavailable**
   - Retry-After header: **180 seconds**
   - Error response body:
     ```json
     {
       "code": "SERVICE_OVERLOADED",
       "message": "System temporarily overloaded - please retry in a few minutes",
       "retryAfter": 180,
       "timestamp": "2025-11-16T...",
       "details": {
         "overloadReason": "circuit_breaker_open",
         "recommendedAction": "retry_with_exponential_backoff",
         "threadPoolUtilization": "85.0%",
         "databasePoolUtilization": "92.5%",
         "memoryUtilization": "78.3%"
       }
     }
     ```

## Testing

A test script has been created: `test-circuit-breaker-status-code.sh`

To test the fix:

```bash
# 1. Start the service
./gradlew bootRun

# 2. Run the test script
./test-circuit-breaker-status-code.sh
```

The script will:
- Verify normal operations return 200/207
- Check circuit breaker state
- If circuit breaker is open, verify it returns 503 (not 400)
- Verify Retry-After header is present

## Impact on Calling Services

### Before (400 Bad Request)
```javascript
// Client sees this as a permanent error
try {
  await orderService.submitOrders(orders);
} catch (error) {
  if (error.status === 400) {
    // Treated as validation error - NO RETRY
    logger.error("Invalid order data:", error.message);
    throw error; // Fails permanently
  }
}
```

### After (503 Service Unavailable)
```javascript
// Client automatically retries
try {
  await orderService.submitOrders(orders);
} catch (error) {
  if (error.status === 503) {
    // Treated as temporary unavailability - AUTOMATIC RETRY
    const retryAfter = error.response.headers['retry-after'];
    logger.warn(`Service overloaded, retrying in ${retryAfter}s`);
    // Retry logic kicks in automatically
  }
}
```

## Related Files

- `src/main/java/org/kasbench/globeco_order_service/service/BatchProcessingService.java` - Circuit breaker check
- `src/main/java/org/kasbench/globeco_order_service/controller/GlobalExceptionHandler.java` - Exception handling
- `src/main/java/org/kasbench/globeco_order_service/exception/SystemOverloadException.java` - Custom exception
- `src/main/java/org/kasbench/globeco_order_service/service/ConnectionPoolCircuitBreaker.java` - Circuit breaker implementation

## HTTP Status Code Reference

| Status | Meaning | Client Behavior |
|--------|---------|-----------------|
| 400 Bad Request | Client error - invalid data | No retry, fix request |
| 429 Too Many Requests | Rate limit exceeded | Retry with backoff |
| 503 Service Unavailable | Temporary overload | Retry with backoff |

For system overload conditions, **503 is the correct choice** as it indicates temporary unavailability that will resolve automatically.
