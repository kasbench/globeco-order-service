package org.kasbench.globeco_order_service.service;

import lombok.extern.slf4j.Slf4j;

/**
 * Context class to hold timing and request information for HTTP request metrics collection.
 * This class is used with ThreadLocal to maintain state across interceptor method calls
 * and provides safe timing operations with proper error handling.
 * 
 * The context tracks:
 * - Request start time for duration calculation
 * - HTTP method and path for metric labeling
 * - In-flight request tracking
 */
@Slf4j
public class RequestTimingContext {
    
    private final long startTime;
    private final String method;
    private final String path;
    private final HttpRequestMetricsService metricsService;
    private boolean inFlightIncremented;
    private boolean completed;

    /**
     * Creates a new request timing context and starts timing.
     * Automatically increments the in-flight requests gauge.
     * 
     * @param method HTTP method (GET, POST, etc.)
     * @param path Request path or route pattern
     * @param metricsService Service for recording metrics
     */
    public RequestTimingContext(String method, String path, HttpRequestMetricsService metricsService) {
        this.startTime = System.nanoTime();
        this.method = HttpMethodNormalizer.normalize(method);
        this.path = RoutePatternSanitizer.sanitize(path);
        this.metricsService = metricsService;
        this.inFlightIncremented = false;
        this.completed = false;
        
        // Increment in-flight requests gauge
        try {
            if (metricsService != null) {
                metricsService.incrementInFlightRequests();
                this.inFlightIncremented = true;
            }
        } catch (Exception e) {
            log.warn("Failed to increment in-flight requests during context creation: {}", e.getMessage());
        }
        
        log.trace("Created RequestTimingContext for {} {}", this.method, this.path);
    }

    /**
     * Completes the request timing and records all metrics.
     * This method is idempotent - calling it multiple times has no additional effect.
     * 
     * @param statusCode HTTP response status code
     */
    public void complete(int statusCode) {
        if (completed) {
            log.trace("RequestTimingContext already completed for {} {}", method, path);
            return;
        }
        
        try {
            long durationNanos = System.nanoTime() - startTime;
            String normalizedStatus = StatusCodeHandler.normalize(statusCode);
            
            // Record the request metrics
            if (metricsService != null) {
                metricsService.recordRequest(method, path, statusCode, durationNanos);
            }
            
            log.trace("Completed RequestTimingContext for {} {} - {} ({}ns)", 
                     method, path, normalizedStatus, durationNanos);
            
        } catch (Exception e) {
            log.warn("Failed to complete request timing for {} {}: {}", method, path, e.getMessage());
        } finally {
            // Always decrement in-flight requests if we incremented it
            if (inFlightIncremented && metricsService != null) {
                try {
                    metricsService.decrementInFlightRequests();
                } catch (Exception e) {
                    log.warn("Failed to decrement in-flight requests during completion: {}", e.getMessage());
                }
            }
            completed = true;
        }
    }

    /**
     * Ensures proper cleanup even if complete() was not called.
     * This method should be called in a finally block to guarantee cleanup.
     */
    public void cleanup() {
        if (!completed) {
            log.debug("Cleaning up incomplete RequestTimingContext for {} {}", method, path);
            // Complete with unknown status if not already completed
            complete(0); // 0 will be normalized to "unknown"
        }
    }

    /**
     * Gets the start time in nanoseconds.
     * 
     * @return start time from System.nanoTime()
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Gets the normalized HTTP method.
     * 
     * @return normalized HTTP method (uppercase)
     */
    public String getMethod() {
        return method;
    }

    /**
     * Gets the sanitized request path.
     * 
     * @return sanitized route pattern
     */
    public String getPath() {
        return path;
    }

    /**
     * Gets the current duration since context creation.
     * 
     * @return duration in nanoseconds
     */
    public long getCurrentDuration() {
        return System.nanoTime() - startTime;
    }

    /**
     * Checks if this context has been completed.
     * 
     * @return true if complete() has been called
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Checks if the in-flight counter was incremented for this context.
     * 
     * @return true if in-flight requests was incremented
     */
    public boolean isInFlightIncremented() {
        return inFlightIncremented;
    }

    @Override
    public String toString() {
        return String.format("RequestTimingContext{method='%s', path='%s', duration=%dns, completed=%s}", 
                           method, path, getCurrentDuration(), completed);
    }
}