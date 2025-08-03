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
import java.util.regex.Pattern;
import java.util.Arrays;

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
    
    // Route pattern sanitization
    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{[^}]+\\}");
    private static final int MAX_PATH_SEGMENTS = 10;
    private static final String UNKNOWN_PATH = "/unknown";

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
        try {
            // Sanitize inputs
            String sanitizedMethod = sanitizeHttpMethod(method);
            String sanitizedPath = sanitizeRoutePath(path);
            String statusString = String.valueOf(statusCode);
            
            // Record counter metric
            recordRequestCounter(sanitizedMethod, sanitizedPath, statusString);
            
            // Record duration histogram
            recordRequestDuration(sanitizedMethod, sanitizedPath, statusString, durationNanos);
            
            log.debug("Recorded HTTP request metrics: {} {} {} {}ns", 
                     sanitizedMethod, sanitizedPath, statusString, durationNanos);
            
        } catch (Exception e) {
            log.warn("Failed to record HTTP request metrics for {} {}: {}", 
                    method, path, e.getMessage());
        }
    }

    /**
     * Records the HTTP request counter metric.
     * Uses caching to avoid repeated metric lookups.
     */
    private void recordRequestCounter(String method, String path, String status) {
        try {
            String cacheKey = buildCacheKey("counter", method, path, status);
            Counter counter = counterCache.computeIfAbsent(cacheKey, 
                k -> createRequestCounter(method, path, status));
            
            counter.increment();
            
        } catch (Exception e) {
            log.warn("Failed to record request counter for {} {} {}: {}", 
                    method, path, status, e.getMessage());
        }
    }

    /**
     * Records the HTTP request duration histogram.
     * Uses caching to avoid repeated metric lookups.
     */
    private void recordRequestDuration(String method, String path, String status, long durationNanos) {
        try {
            String cacheKey = buildCacheKey("timer", method, path, status);
            Timer timer = timerCache.computeIfAbsent(cacheKey, 
                k -> createRequestTimer(method, path, status));
            
            timer.record(durationNanos, TimeUnit.NANOSECONDS);
            
        } catch (Exception e) {
            log.warn("Failed to record request duration for {} {} {}: {}", 
                    method, path, status, e.getMessage());
        }
    }

    /**
     * Creates a new HTTP request counter metric.
     */
    private Counter createRequestCounter(String method, String path, String status) {
        return Counter.builder("http_requests_total")
                .description("Total number of HTTP requests")
                .tag("method", method)
                .tag("path", path)
                .tag("status", status)
                .register(meterRegistry);
    }

    /**
     * Creates a new HTTP request duration timer metric with standard buckets.
     */
    private Timer createRequestTimer(String method, String path, String status) {
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
    }

    /**
     * Increments the in-flight requests gauge.
     * Called when a request starts processing.
     */
    public void incrementInFlightRequests() {
        try {
            inFlightRequests.incrementAndGet();
            log.trace("Incremented in-flight requests to: {}", inFlightRequests.get());
        } catch (Exception e) {
            log.warn("Failed to increment in-flight requests: {}", e.getMessage());
        }
    }

    /**
     * Decrements the in-flight requests gauge.
     * Called when a request completes processing.
     */
    public void decrementInFlightRequests() {
        try {
            inFlightRequests.decrementAndGet();
            log.trace("Decremented in-flight requests to: {}", inFlightRequests.get());
        } catch (Exception e) {
            log.warn("Failed to decrement in-flight requests: {}", e.getMessage());
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
     * Converts to uppercase and handles null/empty values.
     */
    public static String sanitizeHttpMethod(String method) {
        if (method == null || method.trim().isEmpty()) {
            return "UNKNOWN";
        }
        return method.trim().toUpperCase();
    }

    /**
     * Sanitizes route path to prevent high cardinality issues.
     * This method:
     * - Removes query parameters
     * - Limits path segments to prevent explosion
     * - Handles null/empty paths
     * - Preserves route patterns like /api/users/{id}
     */
    public static String sanitizeRoutePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return UNKNOWN_PATH;
        }
        
        String sanitized = path.trim();
        
        // Remove query parameters
        int queryIndex = sanitized.indexOf('?');
        if (queryIndex > 0) {
            sanitized = sanitized.substring(0, queryIndex);
        }
        
        // Remove fragment
        int fragmentIndex = sanitized.indexOf('#');
        if (fragmentIndex > 0) {
            sanitized = sanitized.substring(0, fragmentIndex);
        }
        
        // Ensure path starts with /
        if (!sanitized.startsWith("/")) {
            sanitized = "/" + sanitized;
        }
        
        // Limit path segments to prevent high cardinality
        String[] segments = sanitized.split("/");
        if (segments.length > MAX_PATH_SEGMENTS) {
            String[] limitedSegments = Arrays.copyOf(segments, MAX_PATH_SEGMENTS);
            sanitized = String.join("/", limitedSegments) + "/...";
        }
        
        return sanitized;
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
        status.append("  - In-flight requests: ").append(inFlightRequests.get()).append("\n");
        status.append("  - Cached counters: ").append(counterCache.size()).append("\n");
        status.append("  - Cached timers: ").append(timerCache.size()).append("\n");
        
        return status.toString();
    }
}