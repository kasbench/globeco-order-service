package org.kasbench.globeco_order_service.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for collecting and recording HTTP request metrics.
 * Provides infrastructure for HTTP request monitoring including:
 * - Request counter (http_requests_total)
 * - Request duration histogram (http_request_duration_seconds)
 * - In-flight requests gauge (http_requests_in_flight)
 * 
 * This service handles metric registration, recording, and route pattern sanitization
 * to prevent high cardinality issues while maintaining observability.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "metrics.custom.enabled", havingValue = "true", matchIfMissing = false)
public class HttpRequestMetricsService {

    private final MeterRegistry meterRegistry;
    private final AtomicInteger inFlightRequests;
    
    // Cache for metric instances to avoid repeated lookups
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    @Autowired
    public HttpRequestMetricsService(MeterRegistry meterRegistry) {
        log.info("HttpRequestMetricsService constructor called with MeterRegistry: {}", 
                meterRegistry != null ? meterRegistry.getClass().getSimpleName() : "null");
        
        this.meterRegistry = meterRegistry;
        this.inFlightRequests = new AtomicInteger(0);
        
        // Register the in-flight requests gauge
        registerInFlightGauge();
        
        log.info("HttpRequestMetricsService initialized successfully");
    }

    /**
     * Registers the in-flight requests gauge metric.
     * This gauge tracks the number of HTTP requests currently being processed.
     */
    private void registerInFlightGauge() {
        try {
            Gauge.builder("http_requests_in_flight", inFlightRequests, AtomicInteger::get)
                    .description("Number of HTTP requests currently being processed")
                    .register(meterRegistry);
            
            log.debug("Registered http_requests_in_flight gauge metric");
        } catch (Exception e) {
            log.error("Failed to register in-flight requests gauge: {}", e.getMessage(), e);
        }
    }

    /**
     * Records an HTTP request with all associated metrics.
     * This method is thread-safe and handles errors gracefully.
     * 
     * @param method HTTP method (GET, POST, etc.)
     * @param path Request path or route pattern
     * @param statusCode HTTP status code
     * @param durationNanos Request duration in nanoseconds
     */
    public void recordRequest(String method, String path, int statusCode, long durationNanos) {
        // Validate inputs first
        if (!isValidInput(method, path, statusCode, durationNanos)) {
            log.debug("Invalid input parameters for metric recording, using fallbacks");
        }
        
        try {
            // Sanitize inputs using utility classes with additional validation
            String sanitizedMethod = sanitizeMethodWithFallback(method);
            String sanitizedPath = sanitizePathWithFallback(path);
            String statusString = sanitizeStatusWithFallback(statusCode);
            long validDuration = validateDuration(durationNanos);
            
            // Record metrics with individual error handling
            boolean counterSuccess = recordRequestCounter(sanitizedMethod, sanitizedPath, statusString);
            boolean durationSuccess = recordRequestDuration(sanitizedMethod, sanitizedPath, statusString, validDuration);
            
            // Log success/failure details
            if (counterSuccess && durationSuccess) {
                log.debug("Successfully recorded HTTP request metrics: {} {} {} {}ns", 
                         sanitizedMethod, sanitizedPath, statusString, validDuration);
            } else {
                log.warn("Partial failure recording HTTP request metrics: {} {} {} (counter: {}, duration: {})", 
                        sanitizedMethod, sanitizedPath, statusString, counterSuccess, durationSuccess);
            }
            
        } catch (Exception e) {
            log.warn("Failed to record HTTP request metrics for {} {} {}: {}", 
                    method, path, statusCode, e.getMessage());
            
            // Log additional context for debugging
            if (log.isDebugEnabled()) {
                log.debug("Metric recording error details", e);
            }
            
            // Attempt emergency fallback recording
            attemptEmergencyRecording(method, path, statusCode, durationNanos);
        }
    }

    /**
     * Records the HTTP request counter metric.
     * Uses caching to avoid repeated metric lookups.
     * 
     * @return true if recording was successful, false otherwise
     */
    private boolean recordRequestCounter(String method, String path, String status) {
        try {
            String cacheKey = buildCacheKey("counter", method, path, status);
            Counter counter = counterCache.computeIfAbsent(cacheKey, 
                k -> createRequestCounterSafely(method, path, status));
            
            if (counter != null) {
                counter.increment();
                return true;
            } else {
                log.debug("Counter creation failed for {} {} {}", method, path, status);
                return false;
            }
            
        } catch (Exception e) {
            log.warn("Failed to record request counter for {} {} {}: {}", 
                    method, path, status, e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Counter recording error details", e);
            }
            return false;
        }
    }

    /**
     * Records the HTTP request duration histogram.
     * Uses caching to avoid repeated metric lookups.
     * 
     * @return true if recording was successful, false otherwise
     */
    private boolean recordRequestDuration(String method, String path, String status, long durationNanos) {
        try {
            String cacheKey = buildCacheKey("timer", method, path, status);
            Timer timer = timerCache.computeIfAbsent(cacheKey, 
                k -> createRequestTimerSafely(method, path, status));
            
            if (timer != null) {
                timer.record(durationNanos, TimeUnit.NANOSECONDS);
                return true;
            } else {
                log.debug("Timer creation failed for {} {} {}", method, path, status);
                return false;
            }
            
        } catch (Exception e) {
            log.warn("Failed to record request duration for {} {} {}: {}", 
                    method, path, status, e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Timer recording error details", e);
            }
            return false;
        }
    }

    /**
     * Creates a new HTTP request counter metric with error handling.
     */
    private Counter createRequestCounterSafely(String method, String path, String status) {
        try {
            return Counter.builder("http_requests_total")
                    .description("Total number of HTTP requests")
                    .tag("method", method)
                    .tag("path", path)
                    .tag("status", status)
                    .register(meterRegistry);
        } catch (Exception e) {
            log.warn("Failed to create request counter for {} {} {}: {}", method, path, status, e.getMessage());
            return null;
        }
    }

    /**
     * Creates a new HTTP request duration timer metric with standard buckets and error handling.
     */
    private Timer createRequestTimerSafely(String method, String path, String status) {
        try {
            return Timer.builder("http_request_duration_seconds")
                    .description("Duration of HTTP requests in seconds")
                    .tag("method", method)
                    .tag("path", path)
                    .tag("status", status)
                    .publishPercentileHistogram()
                    .serviceLevelObjectives(
                        Duration.ofMillis(5),    // 0.005s
                        Duration.ofMillis(10),   // 0.01s
                        Duration.ofMillis(25),   // 0.025s
                        Duration.ofMillis(50),   // 0.05s
                        Duration.ofMillis(100),  // 0.1s
                        Duration.ofMillis(250),  // 0.25s
                        Duration.ofMillis(500),  // 0.5s
                        Duration.ofSeconds(1),   // 1s
                        Duration.ofMillis(2500), // 2.5s
                        Duration.ofSeconds(5),   // 5s
                        Duration.ofSeconds(10)   // 10s
                    )
                    .register(meterRegistry);
        } catch (Exception e) {
            log.warn("Failed to create request timer for {} {} {}: {}", method, path, status, e.getMessage());
            return null;
        }
    }

    /**
     * Increments the in-flight requests gauge.
     * Called when a request starts processing.
     */
    public void incrementInFlightRequests() {
        try {
            if (inFlightRequests != null) {
                int newValue = inFlightRequests.incrementAndGet();
                log.trace("Incremented in-flight requests to: {}", newValue);
                
                // Sanity check for unreasonable values
                if (newValue > 10000) {
                    log.warn("In-flight requests count is unusually high: {}. This may indicate a leak.", newValue);
                }
            } else {
                log.debug("In-flight requests counter is null, cannot increment");
            }
        } catch (Exception e) {
            log.warn("Failed to increment in-flight requests: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("In-flight increment error details", e);
            }
        }
    }

    /**
     * Decrements the in-flight requests gauge.
     * Called when a request completes processing.
     */
    public void decrementInFlightRequests() {
        try {
            if (inFlightRequests != null) {
                int newValue = inFlightRequests.decrementAndGet();
                log.trace("Decremented in-flight requests to: {}", newValue);
                
                // Sanity check for negative values
                if (newValue < 0) {
                    log.warn("In-flight requests count went negative: {}. Resetting to 0.", newValue);
                    inFlightRequests.set(0);
                }
            } else {
                log.debug("In-flight requests counter is null, cannot decrement");
            }
        } catch (Exception e) {
            log.warn("Failed to decrement in-flight requests: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("In-flight decrement error details", e);
            }
        }
    }

    /**
     * Gets the current number of in-flight requests.
     */
    public int getInFlightRequests() {
        return inFlightRequests.get();
    }

    /**
     * Sanitizes HTTP method to ensure consistent formatting.
     * Delegates to HttpMethodNormalizer for consistent handling.
     */
    public static String sanitizeHttpMethod(String method) {
        return HttpMethodNormalizer.normalize(method);
    }

    /**
     * Sanitizes route path to prevent high cardinality issues.
     * Delegates to RoutePatternSanitizer for consistent handling.
     */
    public static String sanitizeRoutePath(String path) {
        return RoutePatternSanitizer.sanitize(path);
    }

    /**
     * Builds a cache key for metric instances.
     */
    private String buildCacheKey(String metricType, String method, String path, String status) {
        return metricType + ":" + method + ":" + path + ":" + status;
    }

    /**
     * Gets the current cache statistics for monitoring purposes.
     */
    public String getCacheStatistics() {
        return String.format("HttpRequestMetricsService Cache Stats - Counters: %d, Timers: %d, InFlight: %d",
                counterCache.size(), timerCache.size(), inFlightRequests.get());
    }

    /**
     * Clears the metric caches. Useful for testing or memory management.
     */
    public void clearCaches() {
        counterCache.clear();
        timerCache.clear();
        log.info("Cleared HTTP request metrics caches");
    }

    /**
     * Validates that the service is properly initialized and ready to record metrics.
     */
    public boolean isInitialized() {
        return meterRegistry != null && inFlightRequests != null;
    }

    /**
     * Gets detailed status information about the service.
     */
    public String getServiceStatus() {
        StringBuilder status = new StringBuilder();
        status.append("HttpRequestMetricsService Status:\n");
        status.append("  - Initialized: ").append(isInitialized()).append("\n");
        status.append("  - MeterRegistry: ").append(meterRegistry != null ? meterRegistry.getClass().getSimpleName() : "null").append("\n");
        status.append("  - In-flight requests: ").append(inFlightRequests != null ? inFlightRequests.get() : "null").append("\n");
        status.append("  - Cached counters: ").append(counterCache.size()).append("\n");
        status.append("  - Cached timers: ").append(timerCache.size()).append("\n");
        
        return status.toString();
    }
    
    /**
     * Validates input parameters for metric recording.
     */
    private boolean isValidInput(String method, String path, int statusCode, long durationNanos) {
        boolean valid = true;
        
        if (method == null || method.trim().isEmpty()) {
            log.debug("Invalid method parameter: {}", method);
            valid = false;
        }
        
        if (path == null || path.trim().isEmpty()) {
            log.debug("Invalid path parameter: {}", path);
            valid = false;
        }
        
        if (statusCode < 0 || statusCode > 999) {
            log.debug("Invalid status code parameter: {}", statusCode);
            valid = false;
        }
        
        if (durationNanos < 0) {
            log.debug("Invalid duration parameter: {}", durationNanos);
            valid = false;
        }
        
        return valid;
    }
    
    /**
     * Sanitizes HTTP method with additional fallback handling.
     */
    private String sanitizeMethodWithFallback(String method) {
        try {
            return HttpMethodNormalizer.normalize(method);
        } catch (Exception e) {
            log.debug("Failed to normalize method '{}', using fallback: {}", method, e.getMessage());
            return HttpMethodNormalizer.getUnknownMethod();
        }
    }
    
    /**
     * Sanitizes path with additional fallback handling.
     */
    private String sanitizePathWithFallback(String path) {
        try {
            return RoutePatternSanitizer.sanitize(path);
        } catch (Exception e) {
            log.debug("Failed to sanitize path '{}', using fallback: {}", path, e.getMessage());
            return RoutePatternSanitizer.getUnknownPath();
        }
    }
    
    /**
     * Sanitizes status code with additional fallback handling.
     */
    private String sanitizeStatusWithFallback(int statusCode) {
        try {
            return StatusCodeHandler.normalize(statusCode);
        } catch (Exception e) {
            log.debug("Failed to normalize status code '{}', using fallback: {}", statusCode, e.getMessage());
            return StatusCodeHandler.getUnknownStatus();
        }
    }
    
    /**
     * Validates and corrects duration values.
     */
    private long validateDuration(long durationNanos) {
        if (durationNanos < 0) {
            log.debug("Negative duration {} corrected to 0", durationNanos);
            return 0;
        }
        
        // Check for unreasonably large durations (more than 1 hour)
        long oneHourNanos = TimeUnit.HOURS.toNanos(1);
        if (durationNanos > oneHourNanos) {
            log.debug("Unusually large duration {} (>1 hour), capping to 1 hour", durationNanos);
            return oneHourNanos;
        }
        
        return durationNanos;
    }
    
    /**
     * Attempts emergency recording with minimal processing when normal recording fails.
     */
    private void attemptEmergencyRecording(String method, String path, int statusCode, long durationNanos) {
        try {
            // Use simple string values without normalization to avoid further errors
            String simpleMethod = method != null ? method.toUpperCase() : "UNKNOWN";
            String simplePath = path != null ? path : "/unknown";
            String simpleStatus = String.valueOf(statusCode > 0 ? statusCode : 500);
            
            // Try to record with basic counter only (skip timer to reduce complexity)
            String cacheKey = "emergency:" + simpleMethod + ":" + simplePath + ":" + simpleStatus;
            
            Counter emergencyCounter = counterCache.computeIfAbsent(cacheKey, k -> {
                try {
                    return Counter.builder("http_requests_total")
                            .description("Total number of HTTP requests (emergency recording)")
                            .tag("method", simpleMethod)
                            .tag("path", simplePath)
                            .tag("status", simpleStatus)
                            .register(meterRegistry);
                } catch (Exception e) {
                    log.debug("Emergency counter creation also failed: {}", e.getMessage());
                    return null;
                }
            });
            
            if (emergencyCounter != null) {
                emergencyCounter.increment();
                log.debug("Emergency metric recording succeeded for {} {} {}", simpleMethod, simplePath, simpleStatus);
            }
            
        } catch (Exception emergencyError) {
            log.debug("Emergency metric recording also failed: {}", emergencyError.getMessage());
            // At this point, we've exhausted all options - just log and continue
        }
    }
}