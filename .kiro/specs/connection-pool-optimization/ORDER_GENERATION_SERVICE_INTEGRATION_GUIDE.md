# Order Generation Service Integration Guide

## Overview

This guide provides comprehensive integration patterns for the Order Generation Service (client) to interact with the Order Service's connection pool optimization features. It covers retry strategies, error handling patterns, circuit breaker awareness, and techniques to prevent dropped orders.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Client Configuration](#client-configuration)
3. [Retry Strategies](#retry-strategies)
4. [Error Handling Patterns](#error-handling-patterns)
5. [Circuit Breaker Awareness](#circuit-breaker-awareness)
6. [Preventing Dropped Orders](#preventing-dropped-orders)
7. [Monitoring and Observability](#monitoring-and-observability)
8. [Best Practices](#best-practices)
9. [Code Examples](#code-examples)
10. [Testing](#testing)

## Architecture Overview

### System Interaction

```
┌─────────────────────────────────────────────────────────┐
│         Order Generation Service (Client)                │
│                                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │  Order Queue (In-Memory/Redis)                  │    │
│  │  • Pending orders                               │    │
│  │  • Failed orders for retry                      │    │
│  └────────────────────────────────────────────────┘    │
│                       │                                  │
│                       ▼                                  │
│  ┌────────────────────────────────────────────────┐    │
│  │  Retry Manager                                  │    │
│  │  • Exponential backoff                          │    │
│  │  • Circuit breaker awareness                    │    │
│  │  • Queue management                            │    │
│  └────────────────────────────────────────────────┘    │
│                       │                                  │
│                       ▼                                  │
│  ┌────────────────────────────────────────────────┐    │
│  │  HTTP Client (RestTemplate/WebClient)           │    │
│  │  • Connection pooling                           │    │
│  │  • Timeout configuration                        │    │
│  │  • Error handling                               │    │
│  └────────────────────────────────────────────────┘    │
└──────────────────────┬───────────────────────────────────┘
                       │
                       │ HTTP POST /api/v1/orders/batch
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│              Order Service (Server)                      │
│                                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │  Circuit Breaker                                │    │
│  │  • Monitors connection pool                     │    │
│  │  • Opens at 90% utilization                     │    │
│  │  • Returns 503 when open                        │    │
│  └────────────────────────────────────────────────┘    │
│                       │                                  │
│                       ▼                                  │
│  ┌────────────────────────────────────────────────┐    │
│  │  Batch Processing Service                       │    │
│  │  • Semaphore (15 concurrent ops)                │    │
│  │  • Connection pool (60 max)                     │    │
│  └────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### Key Integration Points

1. **Request Submission**: POST /api/v1/orders/batch
2. **Health Check**: GET /api/v1/health/connection-pool
3. **Circuit Breaker Status**: Monitor 503 responses
4. **Retry-After Header**: Respect recovery time

## Client Configuration

### HTTP Client Setup

**Spring RestTemplate Configuration**:
```java
@Configuration
public class OrderServiceClientConfiguration {
    
    @Bean
    public PoolingHttpClientConnectionManager connectionManager() {
        PoolingHttpClientConnectionManager manager = 
            new PoolingHttpClientConnectionManager();
        
        // Configure connection pool
        manager.setMaxTotal(50);              // Total connections
        manager.setDefaultMaxPerRoute(25);    // Per-route connections
        manager.setValidateAfterInactivity(2000);
        
        return manager;
    }
    
    @Bean
    public RestTemplate orderServiceRestTemplate(
            PoolingHttpClientConnectionManager connectionManager) {
        
        // Configure HTTP client
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(5000)          // 5 seconds
            .setSocketTimeout(60000)          // 60 seconds (batch processing)
            .setConnectionRequestTimeout(3000) // 3 seconds
            .build();
        
        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .setRetryHandler(new CustomRetryHandler())
            .build();
        
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory(httpClient);
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // Add interceptors
        restTemplate.getInterceptors().add(new LoggingInterceptor());
        restTemplate.getInterceptors().add(new MetricsInterceptor());
        
        return restTemplate;
    }
}
```

### Timeout Configuration

**Recommended Timeouts**:

| Timeout Type | Value | Rationale |
|--------------|-------|-----------|
| Connect Timeout | 5s | Time to establish connection |
| Socket Timeout | 60s | Time for batch processing (25 orders) |
| Connection Request Timeout | 3s | Time to get connection from pool |

**Configuration Properties**:
```yaml
order-service:
  client:
    base-url: http://order-service:8080
    connect-timeout: 5000
    read-timeout: 60000
    connection-request-timeout: 3000
    max-connections: 50
    max-connections-per-route: 25
```

## Retry Strategies

### Exponential Backoff with Jitter

**Implementation**:
```java
@Component
public class ExponentialBackoffRetryStrategy {
    
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;  // 1 second
    private static final long MAX_DELAY_MS = 30000;  // 30 seconds
    private static final double JITTER_FACTOR = 0.1;
    
    public <T> T executeWithRetry(Supplier<T> operation, 
                                   Predicate<Exception> shouldRetry) {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < MAX_RETRIES) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                
                if (!shouldRetry.test(e) || attempt >= MAX_RETRIES - 1) {
                    throw new RetryExhaustedException(
                        "Max retries exceeded", e);
                }
                
                long delay = calculateDelay(attempt);
                logger.warn("Retry attempt {}/{} after {}ms", 
                           attempt + 1, MAX_RETRIES, delay);
                
                sleep(delay);
                attempt++;
            }
        }
        
        throw new RetryExhaustedException("Max retries exceeded", 
                                         lastException);
    }
    
    private long calculateDelay(int attempt) {
        // Exponential backoff: 1s, 2s, 4s, 8s, ...
        long exponentialDelay = BASE_DELAY_MS * (1L << attempt);
        
        // Cap at max delay
        long cappedDelay = Math.min(exponentialDelay, MAX_DELAY_MS);
        
        // Add jitter to prevent thundering herd
        long jitter = (long) (cappedDelay * JITTER_FACTOR * Math.random());
        
        return cappedDelay + jitter;
    }
}
```

### Circuit Breaker-Aware Retry

**Implementation**:
```java
@Component
public class CircuitBreakerAwareRetryStrategy {
    
    private static final int MAX_RETRIES = 3;
    private static final long CIRCUIT_BREAKER_RECOVERY_MS = 15000;
    
    public <T> T executeWithCircuitBreakerAwareness(
            Supplier<T> operation) {
        
        int attempt = 0;
        
        while (attempt < MAX_RETRIES) {
            try {
                return operation.get();
                
            } catch (HttpServerErrorException e) {
                if (e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                    
                    // Check if circuit breaker is open
                    String message = e.getResponseBodyAsString();
                    if (message.contains("temporarily overloaded")) {
                        
                        logger.warn("Circuit breaker is OPEN, attempt {}/{}", 
                                   attempt + 1, MAX_RETRIES);
                        
                        // Get Retry-After header
                        long retryAfter = getRetryAfter(e);
                        
                        if (attempt < MAX_RETRIES - 1) {
                            // Wait for circuit breaker recovery
                            sleep(retryAfter);
                            attempt++;
                            continue;
                        }
                    }
                }
                
                throw e;
                
            } catch (Exception e) {
                throw new OrderSubmissionException(
                    "Failed to submit orders", e);
            }
        }
        
        throw new CircuitBreakerOpenException(
            "Circuit breaker remained open after " + MAX_RETRIES + " attempts");
    }
    
    private long getRetryAfter(HttpServerErrorException e) {
        String retryAfter = e.getResponseHeaders()
            .getFirst("Retry-After");
        
        if (retryAfter != null) {
            try {
                return Long.parseLong(retryAfter) * 1000;
            } catch (NumberFormatException ex) {
                // Ignore
            }
        }
        
        return CIRCUIT_BREAKER_RECOVERY_MS;
    }
}
```

### Retry Decision Logic

**When to Retry**:
```java
public boolean shouldRetry(Exception e) {
    // Retry on circuit breaker open (503)
    if (e instanceof HttpServerErrorException) {
        HttpServerErrorException httpError = (HttpServerErrorException) e;
        if (httpError.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
            return true;
        }
    }
    
    // Retry on connection timeout
    if (e instanceof ResourceAccessException) {
        return true;
    }
    
    // Retry on read timeout
    if (e instanceof SocketTimeoutException) {
        return true;
    }
    
    // Don't retry on validation errors (400)
    if (e instanceof HttpClientErrorException) {
        return false;
    }
    
    return false;
}
```

## Error Handling Patterns

### Comprehensive Error Handler

**Implementation**:
```java
@Component
public class OrderSubmissionErrorHandler {
    
    @Autowired
    private OrderQueue orderQueue;
    
    @Autowired
    private MetricsService metricsService;
    
    public BatchSubmitResponseDTO handleSubmission(
            List<OrderDTO> orders) {
        
        try {
            return submitOrders(orders);
            
        } catch (HttpServerErrorException e) {
            return handleServerError(orders, e);
            
        } catch (HttpClientErrorException e) {
            return handleClientError(orders, e);
            
        } catch (ResourceAccessException e) {
            return handleConnectionError(orders, e);
            
        } catch (Exception e) {
            return handleUnexpectedError(orders, e);
        }
    }
    
    private BatchSubmitResponseDTO handleServerError(
            List<OrderDTO> orders, HttpServerErrorException e) {
        
        if (e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
            // Circuit breaker open
            logger.warn("Circuit breaker open, queueing {} orders", 
                       orders.size());
            
            orderQueue.enqueue(orders);
            metricsService.incrementCircuitBreakerRejections();
            
            return BatchSubmitResponseDTO.builder()
                .status("QUEUED")
                .message("Orders queued for retry after circuit breaker recovery")
                .queuedCount(orders.size())
                .build();
        }
        
        // Other server errors
        logger.error("Server error: {}", e.getMessage());
        metricsService.incrementServerErrors();
        
        return BatchSubmitResponseDTO.failure(
            "Server error: " + e.getMessage());
    }
    
    private BatchSubmitResponseDTO handleClientError(
            List<OrderDTO> orders, HttpClientErrorException e) {
        
        // Don't retry client errors (validation, etc.)
        logger.error("Client error: {}", e.getResponseBodyAsString());
        metricsService.incrementClientErrors();
        
        return BatchSubmitResponseDTO.failure(
            "Validation error: " + e.getResponseBodyAsString());
    }
    
    private BatchSubmitResponseDTO handleConnectionError(
            List<OrderDTO> orders, ResourceAccessException e) {
        
        // Connection timeout or refused
        logger.warn("Connection error, queueing {} orders", orders.size());
        
        orderQueue.enqueue(orders);
        metricsService.incrementConnectionErrors();
        
        return BatchSubmitResponseDTO.builder()
            .status("QUEUED")
            .message("Orders queued for retry after connection error")
            .queuedCount(orders.size())
            .build();
    }
}
```

### Error Classification

| Error Type | HTTP Status | Retry? | Queue? | Action |
|------------|-------------|--------|--------|--------|
| Circuit Breaker Open | 503 | Yes | Yes | Wait 15s, retry |
| Connection Timeout | N/A | Yes | Yes | Exponential backoff |
| Read Timeout | N/A | Yes | Yes | Exponential backoff |
| Validation Error | 400 | No | No | Log and alert |
| Server Error | 500 | Yes | Yes | Exponential backoff |
| Rate Limit | 429 | Yes | Yes | Respect Retry-After |

## Circuit Breaker Awareness

### Health Check Monitoring

**Proactive Health Checking**:
```java
@Component
@Scheduled(fixedRate = 10000) // Every 10 seconds
public class OrderServiceHealthMonitor {
    
    @Autowired
    private RestTemplate orderServiceRestTemplate;
    
    @Value("${order-service.client.base-url}")
    private String baseUrl;
    
    private volatile boolean isHealthy = true;
    private volatile double connectionPoolUtilization = 0.0;
    
    public void checkHealth() {
        try {
            String url = baseUrl + "/api/v1/health/connection-pool";
            
            ResponseEntity<ConnectionPoolHealth> response = 
                orderServiceRestTemplate.getForEntity(
                    url, ConnectionPoolHealth.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                ConnectionPoolHealth health = response.getBody();
                
                connectionPoolUtilization = health.getUtilization();
                isHealthy = health.getUtilization() < 0.75;
                
                if (!isHealthy) {
                    logger.warn("Order Service connection pool utilization high: {}%", 
                               health.getUtilization() * 100);
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to check Order Service health", e);
            isHealthy = false;
        }
    }
    
    public boolean isHealthy() {
        return isHealthy;
    }
    
    public double getConnectionPoolUtilization() {
        return connectionPoolUtilization;
    }
    
    public boolean shouldThrottle() {
        // Throttle if utilization > 70%
        return connectionPoolUtilization > 0.70;
    }
}
```

### Adaptive Rate Limiting

**Implementation**:
```java
@Component
public class AdaptiveRateLimiter {
    
    @Autowired
    private OrderServiceHealthMonitor healthMonitor;
    
    private final RateLimiter rateLimiter;
    
    public AdaptiveRateLimiter() {
        // Start with 10 requests per second
        this.rateLimiter = RateLimiter.create(10.0);
    }
    
    @Scheduled(fixedRate = 5000) // Every 5 seconds
    public void adjustRate() {
        double utilization = healthMonitor.getConnectionPoolUtilization();
        
        if (utilization > 0.75) {
            // High utilization - reduce rate by 50%
            double currentRate = rateLimiter.getRate();
            rateLimiter.setRate(currentRate * 0.5);
            logger.warn("Reducing submission rate to {} req/s due to high utilization", 
                       rateLimiter.getRate());
            
        } else if (utilization < 0.50) {
            // Low utilization - increase rate by 20%
            double currentRate = rateLimiter.getRate();
            rateLimiter.setRate(Math.min(currentRate * 1.2, 20.0));
            logger.info("Increasing submission rate to {} req/s", 
                       rateLimiter.getRate());
        }
    }
    
    public void acquire() {
        rateLimiter.acquire();
    }
}
```

## Preventing Dropped Orders

### Persistent Order Queue

**Redis-Based Queue Implementation**:
```java
@Component
public class PersistentOrderQueue {
    
    @Autowired
    private RedisTemplate<String, OrderBatch> redisTemplate;
    
    private static final String QUEUE_KEY = "order:queue:pending";
    private static final String FAILED_KEY = "order:queue:failed";
    
    public void enqueue(List<OrderDTO> orders) {
        OrderBatch batch = OrderBatch.builder()
            .id(UUID.randomUUID().toString())
            .orders(orders)
            .enqueuedAt(Instant.now())
            .retryCount(0)
            .build();
        
        redisTemplate.opsForList().rightPush(QUEUE_KEY, batch);
        
        logger.info("Enqueued batch {} with {} orders", 
                   batch.getId(), orders.size());
    }
    
    public OrderBatch dequeue() {
        return redisTemplate.opsForList().leftPop(QUEUE_KEY);
    }
    
    public void markFailed(OrderBatch batch, String reason) {
        batch.setFailedAt(Instant.now());
        batch.setFailureReason(reason);
        batch.setRetryCount(batch.getRetryCount() + 1);
        
        if (batch.getRetryCount() < 5) {
            // Re-queue for retry
            redisTemplate.opsForList().rightPush(QUEUE_KEY, batch);
        } else {
            // Move to failed queue for manual intervention
            redisTemplate.opsForList().rightPush(FAILED_KEY, batch);
            logger.error("Batch {} moved to failed queue after {} retries", 
                        batch.getId(), batch.getRetryCount());
        }
    }
    
    public long getPendingCount() {
        return redisTemplate.opsForList().size(QUEUE_KEY);
    }
    
    public long getFailedCount() {
        return redisTemplate.opsForList().size(FAILED_KEY);
    }
}
```

### Queue Processor

**Background Queue Processing**:
```java
@Component
public class OrderQueueProcessor {
    
    @Autowired
    private PersistentOrderQueue orderQueue;
    
    @Autowired
    private OrderServiceClient orderServiceClient;
    
    @Autowired
    private OrderServiceHealthMonitor healthMonitor;
    
    @Scheduled(fixedDelay = 5000) // Every 5 seconds
    public void processQueue() {
        // Don't process if service is unhealthy
        if (!healthMonitor.isHealthy()) {
            logger.debug("Skipping queue processing - service unhealthy");
            return;
        }
        
        // Process up to 10 batches per iteration
        int processed = 0;
        while (processed < 10) {
            OrderBatch batch = orderQueue.dequeue();
            
            if (batch == null) {
                break; // Queue empty
            }
            
            try {
                BatchSubmitResponseDTO response = 
                    orderServiceClient.submitBatch(batch.getOrders());
                
                if (response.getStatus().equals("SUCCESS")) {
                    logger.info("Successfully processed queued batch {}", 
                               batch.getId());
                    processed++;
                    
                } else {
                    // Re-queue for retry
                    orderQueue.markFailed(batch, response.getMessage());
                }
                
            } catch (Exception e) {
                logger.error("Error processing queued batch {}", 
                            batch.getId(), e);
                orderQueue.markFailed(batch, e.getMessage());
            }
        }
        
        if (processed > 0) {
            logger.info("Processed {} queued batches", processed);
        }
    }
}
```

### Idempotency Support

**Idempotent Request Implementation**:
```java
@Component
public class IdempotentOrderSubmission {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private OrderServiceClient orderServiceClient;
    
    private static final String IDEMPOTENCY_KEY_PREFIX = "order:idempotency:";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;
    
    public BatchSubmitResponseDTO submitWithIdempotency(
            List<OrderDTO> orders, String idempotencyKey) {
        
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        
        // Check if already processed
        String cachedResponse = redisTemplate.opsForValue().get(key);
        if (cachedResponse != null) {
            logger.info("Returning cached response for idempotency key: {}", 
                       idempotencyKey);
            return deserialize(cachedResponse);
        }
        
        // Submit orders
        BatchSubmitResponseDTO response = 
            orderServiceClient.submitBatch(orders);
        
        // Cache response
        if (response.getStatus().equals("SUCCESS")) {
            redisTemplate.opsForValue().set(
                key, 
                serialize(response), 
                IDEMPOTENCY_TTL_HOURS, 
                TimeUnit.HOURS
            );
        }
        
        return response;
    }
    
    public String generateIdempotencyKey(List<OrderDTO> orders) {
        // Generate deterministic key from order content
        String content = orders.stream()
            .map(o -> o.getSecurityId() + ":" + o.getQuantity())
            .collect(Collectors.joining(","));
        
        return DigestUtils.sha256Hex(content);
    }
}
```

## Monitoring and Observability

### Client-Side Metrics

**Metrics to Track**:
```java
@Component
public class OrderSubmissionMetrics {
    
    private final Counter submissionAttempts;
    private final Counter submissionSuccesses;
    private final Counter submissionFailures;
    private final Counter circuitBreakerRejections;
    private final Counter queuedOrders;
    private final Timer submissionDuration;
    private final Gauge queueDepth;
    
    public OrderSubmissionMetrics(MeterRegistry registry) {
        this.submissionAttempts = Counter.builder("order.submission.attempts")
            .description("Total order submission attempts")
            .register(registry);
        
        this.submissionSuccesses = Counter.builder("order.submission.success")
            .description("Successful order submissions")
            .register(registry);
        
        this.submissionFailures = Counter.builder("order.submission.failures")
            .tag("error_type", "unknown")
            .description("Failed order submissions")
            .register(registry);
        
        this.circuitBreakerRejections = Counter.builder("order.circuit_breaker.rejections")
            .description("Orders rejected due to circuit breaker")
            .register(registry);
        
        this.queuedOrders = Counter.builder("order.queued")
            .description("Orders queued for retry")
            .register(registry);
        
        this.submissionDuration = Timer.builder("order.submission.duration")
            .description("Order submission duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
        
        this.queueDepth = Gauge.builder("order.queue.depth", 
                                        this::getQueueDepth)
            .description("Current queue depth")
            .register(registry);
    }
    
    public void recordAttempt() {
        submissionAttempts.increment();
    }
    
    public void recordSuccess() {
        submissionSuccesses.increment();
    }
    
    public void recordFailure(String errorType) {
        Counter.builder("order.submission.failures")
            .tag("error_type", errorType)
            .register(registry)
            .increment();
    }
    
    public void recordCircuitBreakerRejection() {
        circuitBreakerRejections.increment();
    }
    
    public void recordQueued(int count) {
        queuedOrders.increment(count);
    }
}
```

### Logging Best Practices

**Structured Logging**:
```java
@Slf4j
@Component
public class OrderSubmissionLogger {
    
    public void logSubmissionAttempt(String batchId, int orderCount) {
        logger.info("Submitting order batch: batchId={}, orderCount={}", 
                   batchId, orderCount);
    }
    
    public void logSubmissionSuccess(String batchId, long durationMs) {
        logger.info("Order batch submitted successfully: batchId={}, durationMs={}", 
                   batchId, durationMs);
    }
    
    public void logCircuitBreakerRejection(String batchId, int orderCount) {
        logger.warn("Order batch rejected by circuit breaker: batchId={}, orderCount={}, action=queued", 
                   batchId, orderCount);
    }
    
    public void logRetryAttempt(String batchId, int attempt, int maxAttempts) {
        logger.info("Retrying order batch: batchId={}, attempt={}/{}", 
                   batchId, attempt, maxAttempts);
    }
    
    public void logQueueProcessing(int processed, int remaining) {
        logger.info("Processed queued batches: processed={}, remaining={}", 
                   processed, remaining);
    }
    
    public void logError(String batchId, String errorType, String message) {
        logger.error("Order submission error: batchId={}, errorType={}, message={}", 
                    batchId, errorType, message);
    }
}
```

## Best Practices

### 1. Always Use Idempotency Keys

```java
// Generate idempotency key from order content
String idempotencyKey = idempotentSubmission.generateIdempotencyKey(orders);

// Submit with idempotency
BatchSubmitResponseDTO response = 
    idempotentSubmission.submitWithIdempotency(orders, idempotencyKey);
```

**Benefits**:
- Prevents duplicate submissions on retry
- Safe to retry without side effects
- Enables exactly-once semantics

### 2. Implement Persistent Queueing

```java
// Always queue on circuit breaker rejection
if (response.getStatusCode() == 503) {
    orderQueue.enqueue(orders);
}

// Process queue in background
@Scheduled(fixedDelay = 5000)
public void processQueue() {
    // Process queued orders
}
```

**Benefits**:
- No orders dropped during circuit breaker activation
- Automatic retry after recovery
- Visibility into pending orders

### 3. Monitor Order Service Health

```java
// Check health before submitting
if (!healthMonitor.isHealthy()) {
    // Queue instead of submitting
    orderQueue.enqueue(orders);
    return;
}

// Adjust rate based on utilization
if (healthMonitor.shouldThrottle()) {
    rateLimiter.acquire();
}
```

**Benefits**:
- Proactive load management
- Prevents overwhelming service
- Reduces circuit breaker activations

### 4. Use Exponential Backoff with Jitter

```java
// Calculate delay with jitter
long delay = calculateDelayWithJitter(attempt);

// Wait before retry
Thread.sleep(delay);
```

**Benefits**:
- Prevents thundering herd
- Gives service time to recover
- Distributes retry load

### 5. Respect Retry-After Header

```java
// Get Retry-After from response
String retryAfter = response.getHeaders().getFirst("Retry-After");

// Wait for specified duration
if (retryAfter != null) {
    Thread.sleep(Long.parseLong(retryAfter) * 1000);
}
```

**Benefits**:
- Aligns with circuit breaker recovery
- Prevents premature retries
- Improves success rate

### 6. Implement Circuit Breaker on Client Side

```java
@CircuitBreaker(name = "orderService", fallbackMethod = "fallbackSubmit")
public BatchSubmitResponseDTO submitOrders(List<OrderDTO> orders) {
    return orderServiceClient.submitBatch(orders);
}

public BatchSubmitResponseDTO fallbackSubmit(List<OrderDTO> orders, Exception e) {
    // Queue orders for later processing
    orderQueue.enqueue(orders);
    
    return BatchSubmitResponseDTO.builder()
        .status("QUEUED")
        .message("Orders queued due to service unavailability")
        .build();
}
```

**Benefits**:
- Client-side protection
- Fast failure detection
- Automatic fallback to queueing

### 7. Set Appropriate Timeouts

```java
// Connection timeout: 5 seconds
requestConfig.setConnectTimeout(5000);

// Read timeout: 60 seconds (for batch processing)
requestConfig.setSocketTimeout(60000);

// Connection request timeout: 3 seconds
requestConfig.setConnectionRequestTimeout(3000);
```

**Benefits**:
- Prevents indefinite blocking
- Aligns with server-side timeouts
- Enables timely retries

### 8. Monitor and Alert

```java
// Alert on high queue depth
if (orderQueue.getPendingCount() > 1000) {
    alertService.sendAlert("High order queue depth: " + 
                          orderQueue.getPendingCount());
}

// Alert on high failure rate
double failureRate = calculateFailureRate();
if (failureRate > 0.05) {
    alertService.sendAlert("High order submission failure rate: " + 
                          failureRate);
}
```

**Benefits**:
- Early problem detection
- Proactive intervention
- Visibility into system health

## Code Examples

### Complete Integration Example

```java
@Service
@Slf4j
public class OrderGenerationService {
    
    @Autowired
    private OrderServiceClient orderServiceClient;
    
    @Autowired
    private PersistentOrderQueue orderQueue;
    
    @Autowired
    private OrderServiceHealthMonitor healthMonitor;
    
    @Autowired
    private IdempotentOrderSubmission idempotentSubmission;
    
    @Autowired
    private ExponentialBackoffRetryStrategy retryStrategy;
    
    @Autowired
    private OrderSubmissionMetrics metrics;
    
    @Autowired
    private AdaptiveRateLimiter rateLimiter;
    
    public BatchSubmitResponseDTO generateAndSubmitOrders(
            int count, String portfolioId) {
        
        // Generate orders
        List<OrderDTO> orders = generateOrders(count, portfolioId);
        
        // Generate idempotency key
        String idempotencyKey = 
            idempotentSubmission.generateIdempotencyKey(orders);
        
        // Check service health
        if (!healthMonitor.isHealthy()) {
            log.warn("Order Service unhealthy, queueing {} orders", count);
            orderQueue.enqueue(orders);
            metrics.recordQueued(count);
            
            return BatchSubmitResponseDTO.builder()
                .status("QUEUED")
                .message("Orders queued - service temporarily unavailable")
                .queuedCount(count)
                .build();
        }
        
        // Apply rate limiting
        rateLimiter.acquire();
        
        // Submit with retry
        metrics.recordAttempt();
        
        try {
            BatchSubmitResponseDTO response = retryStrategy.executeWithRetry(
                () -> idempotentSubmission.submitWithIdempotency(
                    orders, idempotencyKey),
                this::shouldRetry
            );
            
            if (response.getStatus().equals("SUCCESS")) {
                metrics.recordSuccess();
                log.info("Successfully submitted {} orders", count);
            } else {
                metrics.recordFailure("submission_failed");
                log.error("Failed to submit orders: {}", response.getMessage());
            }
            
            return response;
            
        } catch (CircuitBreakerOpenException e) {
            // Circuit breaker open - queue orders
            log.warn("Circuit breaker open, queueing {} orders", count);
            orderQueue.enqueue(orders);
            metrics.recordCircuitBreakerRejection();
            metrics.recordQueued(count);
            
            return BatchSubmitResponseDTO.builder()
                .status("QUEUED")
                .message("Orders queued - circuit breaker open")
                .queuedCount(count)
                .build();
            
        } catch (RetryExhaustedException e) {
            // Max retries exceeded - queue for manual intervention
            log.error("Max retries exceeded for {} orders", count, e);
            orderQueue.enqueue(orders);
            metrics.recordFailure("retry_exhausted");
            metrics.recordQueued(count);
            
            return BatchSubmitResponseDTO.builder()
                .status("QUEUED")
                .message("Orders queued - max retries exceeded")
                .queuedCount(count)
                .build();
        }
    }
    
    private boolean shouldRetry(Exception e) {
        return e instanceof HttpServerErrorException ||
               e instanceof ResourceAccessException ||
               e instanceof SocketTimeoutException;
    }
}
```

## Testing

### Integration Test Example

```java
@SpringBootTest
@AutoConfigureMockMvc
public class OrderGenerationServiceIntegrationTest {
    
    @Autowired
    private OrderGenerationService orderGenerationService;
    
    @Autowired
    private PersistentOrderQueue orderQueue;
    
    @MockBean
    private OrderServiceClient orderServiceClient;
    
    @Test
    public void testCircuitBreakerHandling() {
        // Simulate circuit breaker open
        when(orderServiceClient.submitBatch(any()))
            .thenThrow(new HttpServerErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "System temporarily overloaded"));
        
        // Submit orders
        BatchSubmitResponseDTO response = 
            orderGenerationService.generateAndSubmitOrders(25, "PORT-001");
        
        // Verify orders were queued
        assertEquals("QUEUED", response.getStatus());
        assertEquals(25, response.getQueuedCount());
        assertEquals(25, orderQueue.getPendingCount());
    }
    
    @Test
    public void testRetryWithExponentialBackoff() {
        // First attempt fails, second succeeds
        when(orderServiceClient.submitBatch(any()))
            .thenThrow(new ResourceAccessException("Connection timeout"))
            .thenReturn(BatchSubmitResponseDTO.success(25));
        
        // Submit orders
        long startTime = System.currentTimeMillis();
        BatchSubmitResponseDTO response = 
            orderGenerationService.generateAndSubmitOrders(25, "PORT-001");
        long duration = System.currentTimeMillis() - startTime;
        
        // Verify success after retry
        assertEquals("SUCCESS", response.getStatus());
        
        // Verify exponential backoff was applied (at least 1 second delay)
        assertTrue(duration >= 1000);
    }
    
    @Test
    public void testIdempotency() {
        // Submit same orders twice
        List<OrderDTO> orders = generateTestOrders(25);
        String idempotencyKey = 
            idempotentSubmission.generateIdempotencyKey(orders);
        
        // First submission
        BatchSubmitResponseDTO response1 = 
            idempotentSubmission.submitWithIdempotency(orders, idempotencyKey);
        
        // Second submission (should return cached response)
        BatchSubmitResponseDTO response2 = 
            idempotentSubmission.submitWithIdempotency(orders, idempotencyKey);
        
        // Verify only one actual submission
        verify(orderServiceClient, times(1)).submitBatch(any());
        
        // Verify responses are identical
        assertEquals(response1.getStatus(), response2.getStatus());
    }
    
    @Test
    public void testQueueProcessing() {
        // Queue some orders
        List<OrderDTO> orders = generateTestOrders(25);
        orderQueue.enqueue(orders);
        
        // Configure mock to succeed
        when(orderServiceClient.submitBatch(any()))
            .thenReturn(BatchSubmitResponseDTO.success(25));
        
        // Process queue
        orderQueueProcessor.processQueue();
        
        // Verify queue is empty
        assertEquals(0, orderQueue.getPendingCount());
        
        // Verify orders were submitted
        verify(orderServiceClient, times(1)).submitBatch(any());
    }
    
    @Test
    public void testAdaptiveRateLimiting() {
        // Simulate high utilization
        when(healthMonitor.getConnectionPoolUtilization())
            .thenReturn(0.80);
        
        // Adjust rate
        rateLimiter.adjustRate();
        
        // Verify rate was reduced
        assertTrue(rateLimiter.getRate() < 10.0);
        
        // Simulate low utilization
        when(healthMonitor.getConnectionPoolUtilization())
            .thenReturn(0.40);
        
        // Adjust rate
        rateLimiter.adjustRate();
        
        // Verify rate was increased
        assertTrue(rateLimiter.getRate() > 5.0);
    }
}
```

### Load Test Example

```java
@Test
public void testHighLoadWithCircuitBreaker() {
    // Simulate 100 concurrent submissions
    ExecutorService executor = Executors.newFixedThreadPool(100);
    List<Future<BatchSubmitResponseDTO>> futures = new ArrayList<>();
    
    for (int i = 0; i < 100; i++) {
        futures.add(executor.submit(() -> 
            orderGenerationService.generateAndSubmitOrders(25, "PORT-001")
        ));
    }
    
    // Wait for completion
    List<BatchSubmitResponseDTO> responses = futures.stream()
        .map(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        })
        .collect(Collectors.toList());
    
    // Verify no orders were dropped
    long successCount = responses.stream()
        .filter(r -> r.getStatus().equals("SUCCESS"))
        .count();
    
    long queuedCount = responses.stream()
        .filter(r -> r.getStatus().equals("QUEUED"))
        .count();
    
    // All orders should be either successful or queued
    assertEquals(100, successCount + queuedCount);
    
    // Verify queue will eventually process all orders
    while (orderQueue.getPendingCount() > 0) {
        orderQueueProcessor.processQueue();
        Thread.sleep(1000);
    }
    
    // Verify all orders were eventually processed
    assertEquals(0, orderQueue.getPendingCount());
}
```

## Summary

This integration guide provides comprehensive patterns for the Order Generation Service to interact safely with the Order Service's connection pool optimization features:

1. **Retry Strategies**: Exponential backoff with jitter and circuit breaker awareness
2. **Error Handling**: Comprehensive error classification and handling
3. **Circuit Breaker Awareness**: Health monitoring and adaptive rate limiting
4. **Preventing Dropped Orders**: Persistent queueing and idempotency
5. **Monitoring**: Client-side metrics and structured logging
6. **Best Practices**: Proven patterns for reliable integration
7. **Testing**: Comprehensive test coverage for all scenarios

By following these patterns, the Order Generation Service can:
- Never drop orders during circuit breaker activations
- Automatically retry failed submissions
- Adapt to Order Service load conditions
- Provide visibility into order processing status
- Maintain high reliability and throughput

## References

- Connection Pool Configuration: See CONNECTION_POOL_CONFIGURATION.md
- Circuit Breaker Guide: See CIRCUIT_BREAKER_GUIDE.md
- Operational Runbook: See OPERATIONAL_RUNBOOK.md
- Alert Definitions: See ALERT_DEFINITIONS.md
