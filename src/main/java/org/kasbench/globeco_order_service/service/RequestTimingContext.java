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
        this.metricsService = metricsService;
        this.inFlightIncremented = false;
        this.completed = false;
        
        // Safely normalize method and path with fallbacks
        String normalizedMethod;
        try {
            normalizedMethod = HttpMethodNormalizer.normalize(method);
        } catch (Exception e) {
            log.debug("Failed to normalize method '{}', using fallback: {}", method, e.getMessage());
            normalizedMethod = HttpMethodNormalizer.getUnknownMethod();
        }
        this.method = normalizedMethod;
        
        String sanitizedPath;
        try {
            sanitizedPath = RoutePatternSanitizer.sanitize(path);
        } catch (Exception e) {
            log.debug("Failed to sanitize path '{}', using fallback: {}", path, e.getMessage());
            sanitizedPath = RoutePatternSanitizer.getUnknownPath();
        }
        this.path = sanitizedPath;
        
        // Increment in-flight requests gauge with error handling
        if (metricsService != null) {
            try {
                metricsService.incrementInFlightRequests();
                this.inFlightIncremented = true;
                log.trace("Successfully incremented in-flight requests for {} {}", this.method, this.path);
            } catch (Exception e) {
                log.warn("Failed to increment in-flight requests during context creation for {} {}: {}", 
                        this.method, this.path, e.getMessage());
                if (log.isDebugEnabled()) {
                    log.debug("In-flight increment error details", e);
                }
            }
        } else {
            log.debug("MetricsService is null, cannot increment in-flight requests for {} {}", this.method, this.path);
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
        
        boolean metricsRecorded = false;
        String normalizedStatus = "unknown";
        long durationNanos = 0;
        
        try {
            // Calculate duration safely
            durationNanos = calculateDurationSafely();
            
            // Normalize status code safely
            normalizedStatus = normalizeStatusCodeSafely(statusCode);
            
            // Record the request metrics
            if (metricsService != null) {
                metricsService.recordRequest(method, path, statusCode, durationNanos);
                metricsRecorded = true;
                log.trace("Successfully recorded metrics for {} {} - {} ({}ns)", 
                         method, path, normalizedStatus, durationNanos);
            } else {
                log.debug("MetricsService is null, cannot record metrics for {} {}", method, path);
            }
            
        } catch (Exception e) {
            log.warn("Failed to complete request timing for {} {} {}: {}", 
                    method, path, statusCode, e.getMessage());
            
            if (log.isDebugEnabled()) {
                log.debug("Request completion error details", e);
            }
            
            // Note: Don't attempt fallback recording here since the recordRequest call
            // was made (even if it failed). Fallback recording should only be used
            // when we can't make the normal call at all.
            
        } finally {
            // Always decrement in-flight requests if we incremented it
            decrementInFlightSafely();
            completed = true;
            
            log.trace("Completed RequestTimingContext for {} {} - {} ({}ns, recorded: {})", 
                     method, path, normalizedStatus, durationNanos, metricsRecorded);
        }
    }

    /**
     * Ensures proper cleanup even if complete() was not called.
     * This method should be called in a finally block to guarantee cleanup.
     */
    public void cleanup() {
        if (!completed) {
            log.debug("Cleaning up incomplete RequestTimingContext for {} {}", method, path);
            try {
                // Complete with unknown status if not already completed
                complete(0); // 0 will be normalized to "unknown"
            } catch (Exception e) {
                log.warn("Failed to complete context during cleanup for {} {}: {}", method, path, e.getMessage());
                
                // Emergency cleanup - just decrement in-flight counter
                decrementInFlightSafely();
                completed = true;
            }
        }
    }
    
    /**
     * Safely calculates the duration since context creation.
     */
    private long calculateDurationSafely() {
        try {
            long duration = System.nanoTime() - startTime;
            
            // Sanity check for negative durations (clock adjustments)
            if (duration < 0) {
                log.debug("Negative duration detected ({}ns), using 0", duration);
                return 0;
            }
            
            return duration;
        } catch (Exception e) {
            log.debug("Failed to calculate duration, using 0: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Safely normalizes status code with fallback.
     */
    private String normalizeStatusCodeSafely(int statusCode) {
        try {
            return StatusCodeHandler.normalize(statusCode);
        } catch (Exception e) {
            log.debug("Failed to normalize status code {}, using fallback: {}", statusCode, e.getMessage());
            return StatusCodeHandler.getUnknownStatus();
        }
    }
    
    /**
     * Safely decrements in-flight requests counter.
     */
    private void decrementInFlightSafely() {
        if (inFlightIncremented && metricsService != null) {
            try {
                metricsService.decrementInFlightRequests();
                log.trace("Successfully decremented in-flight requests for {} {}", method, path);
            } catch (Exception e) {
                log.warn("Failed to decrement in-flight requests during completion for {} {}: {}", 
                        method, path, e.getMessage());
                if (log.isDebugEnabled()) {
                    log.debug("In-flight decrement error details", e);
                }
            }
        }
    }
    
    /**
     * Attempts fallback recording when normal completion fails.
     */
    private void attemptFallbackRecording(int statusCode, long durationNanos) {
        try {
            if (metricsService != null) {
                // Try with simplified parameters
                String fallbackMethod = method != null ? method : "UNKNOWN";
                String fallbackPath = path != null ? path : "/unknown";
                int fallbackStatus = statusCode > 0 ? statusCode : 500;
                long fallbackDuration = durationNanos >= 0 ? durationNanos : 0;
                
                metricsService.recordRequest(fallbackMethod, fallbackPath, fallbackStatus, fallbackDuration);
                log.debug("Fallback metric recording succeeded for {} {} {}", fallbackMethod, fallbackPath, fallbackStatus);
            }
        } catch (Exception fallbackError) {
            log.debug("Fallback metric recording also failed for {} {}: {}", method, path, fallbackError.getMessage());
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