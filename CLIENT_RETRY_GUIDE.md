# Order Service Client Retry Guide

## Overview

The Order Service now returns proper HTTP status codes for system overload conditions. This guide helps client services implement appropriate retry logic.

## Status Codes

### 503 Service Unavailable
**When:** System is temporarily overloaded (circuit breaker open, high load)  
**Action:** Retry with exponential backoff  
**Retry-After Header:** Indicates minimum wait time before retry (typically 180 seconds)

### 429 Too Many Requests
**When:** Rate limit exceeded (if implemented)  
**Action:** Retry with exponential backoff  
**Retry-After Header:** Indicates when rate limit resets

### 400 Bad Request
**When:** Invalid request data (validation errors)  
**Action:** Do NOT retry - fix the request data  
**Examples:** Missing required fields, invalid order types, malformed JSON

## Recommended Retry Strategy

### JavaScript/TypeScript Example

```javascript
async function submitOrdersWithRetry(orders, maxRetries = 3) {
  let attempt = 0;
  
  while (attempt < maxRetries) {
    try {
      const response = await fetch('/api/v1/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(orders)
      });
      
      if (response.ok) {
        return await response.json();
      }
      
      // Handle different status codes
      if (response.status === 503 || response.status === 429) {
        // Service overloaded or rate limited - retry
        const retryAfter = parseInt(response.headers.get('Retry-After') || '60');
        const backoff = Math.min(retryAfter * 1000, 300000); // Max 5 minutes
        
        console.warn(`Service unavailable (${response.status}), retrying in ${backoff/1000}s`);
        await sleep(backoff);
        attempt++;
        continue;
      }
      
      if (response.status === 400) {
        // Client error - do not retry
        const error = await response.json();
        throw new Error(`Invalid request: ${error.message}`);
      }
      
      // Other errors
      throw new Error(`Unexpected status: ${response.status}`);
      
    } catch (error) {
      if (error.message.includes('Invalid request')) {
        // Don't retry validation errors
        throw error;
      }
      
      // Network errors - retry with exponential backoff
      const backoff = Math.min(1000 * Math.pow(2, attempt), 60000);
      console.warn(`Network error, retrying in ${backoff/1000}s:`, error.message);
      await sleep(backoff);
      attempt++;
    }
  }
  
  throw new Error(`Failed after ${maxRetries} attempts`);
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}
```

### Java Example

```java
public class OrderServiceClient {
    private static final int MAX_RETRIES = 3;
    private static final int MAX_BACKOFF_MS = 300_000; // 5 minutes
    
    public OrderListResponseDTO submitOrders(List<OrderPostDTO> orders) 
            throws ServiceException {
        int attempt = 0;
        
        while (attempt < MAX_RETRIES) {
            try {
                HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/v1/orders"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(orders)))
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                );
                
                int status = response.statusCode();
                
                if (status == 200 || status == 207) {
                    return objectMapper.readValue(
                        response.body(), OrderListResponseDTO.class);
                }
                
                if (status == 503 || status == 429) {
                    // Service overloaded - retry with backoff
                    int retryAfter = response.headers()
                        .firstValue("Retry-After")
                        .map(Integer::parseInt)
                        .orElse(60);
                    
                    long backoffMs = Math.min(retryAfter * 1000L, MAX_BACKOFF_MS);
                    
                    logger.warn("Service unavailable ({}), retrying in {}s", 
                               status, backoffMs / 1000);
                    Thread.sleep(backoffMs);
                    attempt++;
                    continue;
                }
                
                if (status == 400) {
                    // Client error - do not retry
                    ErrorResponseDTO error = objectMapper.readValue(
                        response.body(), ErrorResponseDTO.class);
                    throw new ValidationException("Invalid request: " + error.getMessage());
                }
                
                throw new ServiceException("Unexpected status: " + status);
                
            } catch (ValidationException e) {
                // Don't retry validation errors
                throw e;
            } catch (Exception e) {
                // Network errors - retry with exponential backoff
                long backoffMs = Math.min(
                    1000L * (long) Math.pow(2, attempt), 
                    60_000L
                );
                
                logger.warn("Network error, retrying in {}s: {}", 
                           backoffMs / 1000, e.getMessage());
                Thread.sleep(backoffMs);
                attempt++;
            }
        }
        
        throw new ServiceException("Failed after " + MAX_RETRIES + " attempts");
    }
}
```

### Python Example

```python
import time
import requests
from typing import List, Dict

class OrderServiceClient:
    MAX_RETRIES = 3
    MAX_BACKOFF_SECONDS = 300  # 5 minutes
    
    def submit_orders(self, orders: List[Dict]) -> Dict:
        attempt = 0
        
        while attempt < self.MAX_RETRIES:
            try:
                response = requests.post(
                    f"{self.base_url}/api/v1/orders",
                    json=orders,
                    timeout=30
                )
                
                if response.status_code in (200, 207):
                    return response.json()
                
                if response.status_code in (503, 429):
                    # Service overloaded - retry with backoff
                    retry_after = int(response.headers.get('Retry-After', 60))
                    backoff = min(retry_after, self.MAX_BACKOFF_SECONDS)
                    
                    print(f"Service unavailable ({response.status_code}), "
                          f"retrying in {backoff}s")
                    time.sleep(backoff)
                    attempt += 1
                    continue
                
                if response.status_code == 400:
                    # Client error - do not retry
                    error = response.json()
                    raise ValueError(f"Invalid request: {error.get('message')}")
                
                raise Exception(f"Unexpected status: {response.status_code}")
                
            except ValueError:
                # Don't retry validation errors
                raise
            except Exception as e:
                # Network errors - retry with exponential backoff
                backoff = min(2 ** attempt, 60)
                print(f"Network error, retrying in {backoff}s: {e}")
                time.sleep(backoff)
                attempt += 1
        
        raise Exception(f"Failed after {self.MAX_RETRIES} attempts")
```

## Best Practices

### 1. Respect Retry-After Header
Always check and respect the `Retry-After` header value. Don't retry before this time.

### 2. Implement Exponential Backoff
For network errors without a Retry-After header, use exponential backoff:
- 1st retry: 1 second
- 2nd retry: 2 seconds
- 3rd retry: 4 seconds
- etc.

### 3. Set Maximum Backoff
Cap the maximum wait time (e.g., 5 minutes) to prevent indefinite delays.

### 4. Don't Retry 400 Errors
400 Bad Request indicates a client error. Retrying won't help - fix the request instead.

### 5. Log Retry Attempts
Log each retry attempt with:
- Attempt number
- Status code received
- Wait time before retry
- Reason for retry

### 6. Circuit Breaker Pattern
Consider implementing a client-side circuit breaker to prevent cascading failures:

```javascript
class CircuitBreaker {
  constructor(threshold = 5, timeout = 60000) {
    this.failureCount = 0;
    this.threshold = threshold;
    this.timeout = timeout;
    this.state = 'CLOSED';
    this.nextAttempt = Date.now();
  }
  
  async call(fn) {
    if (this.state === 'OPEN') {
      if (Date.now() < this.nextAttempt) {
        throw new Error('Circuit breaker is OPEN');
      }
      this.state = 'HALF_OPEN';
    }
    
    try {
      const result = await fn();
      this.onSuccess();
      return result;
    } catch (error) {
      this.onFailure();
      throw error;
    }
  }
  
  onSuccess() {
    this.failureCount = 0;
    this.state = 'CLOSED';
  }
  
  onFailure() {
    this.failureCount++;
    if (this.failureCount >= this.threshold) {
      this.state = 'OPEN';
      this.nextAttempt = Date.now() + this.timeout;
    }
  }
}
```

## Error Response Format

### 503 Service Unavailable Response

```json
{
  "code": "SERVICE_OVERLOADED",
  "message": "System temporarily overloaded - please retry in a few minutes",
  "retryAfter": 180,
  "timestamp": "2025-11-16T10:30:00Z",
  "details": {
    "overloadReason": "circuit_breaker_open",
    "recommendedAction": "retry_with_exponential_backoff",
    "threadPoolUtilization": "85.0%",
    "databasePoolUtilization": "92.5%",
    "memoryUtilization": "78.3%"
  }
}
```

### 400 Bad Request Response

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Invalid order data: orderType must be one of [BUY, SELL]",
  "timestamp": "2025-11-16T10:30:00Z"
}
```

## Monitoring

Track these metrics in your client service:

1. **Retry Rate**: Percentage of requests that required retries
2. **503 Error Rate**: Frequency of service unavailable errors
3. **Retry Success Rate**: Percentage of retries that eventually succeeded
4. **Average Retry Delay**: Average time spent waiting for retries

## Support

If you see persistent 503 errors:
1. Check Order Service health: `GET /actuator/health`
2. Check circuit breaker state: `GET /actuator/metrics/circuit.breaker.state`
3. Contact Order Service team if issues persist > 5 minutes
