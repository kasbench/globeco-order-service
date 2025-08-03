package org.kasbench.globeco_order_service.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HttpRequestMetricsService.
 * Tests metric registration, recording, route pattern sanitization, and error handling.
 */
class HttpRequestMetricsServiceTest {

    private MeterRegistry meterRegistry;
    private HttpRequestMetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new HttpRequestMetricsService(meterRegistry);
    }

    @Test
    @DisplayName("Should initialize service with in-flight gauge registered")
    void shouldInitializeServiceWithInFlightGauge() {
        // Then
        assertThat(metricsService.isInitialized()).isTrue();
        
        Gauge inFlightGauge = meterRegistry.find("http_requests_in_flight").gauge();
        assertThat(inFlightGauge).isNotNull();
        assertThat(inFlightGauge.value()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should record HTTP request counter metric")
    void shouldRecordHttpRequestCounter() {
        // When
        metricsService.recordRequest("GET", "/api/v1/orders", 200, 1000000L);

        // Then
        Counter counter = meterRegistry.find("http_requests_total")
                .tag("method", "GET")
                .tag("path", "/api/v1/orders")
                .tag("status", "200")
                .counter();
        
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should record HTTP request duration histogram")
    void shouldRecordHttpRequestDuration() {
        // Given
        long durationNanos = 50_000_000L; // 50ms

        // When
        metricsService.recordRequest("POST", "/api/v1/orders", 201, durationNanos);

        // Then
        Timer timer = meterRegistry.find("http_request_duration_seconds")
                .tag("method", "POST")
                .tag("path", "/api/v1/orders")
                .tag("status", "201")
                .timer();
        
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(durationNanos);
    }

    @Test
    @DisplayName("Should increment and decrement in-flight requests")
    void shouldManageInFlightRequests() {
        // Given
        assertThat(metricsService.getInFlightRequests()).isEqualTo(0);

        // When
        metricsService.incrementInFlightRequests();
        
        // Then
        assertThat(metricsService.getInFlightRequests()).isEqualTo(1);
        
        Gauge inFlightGauge = meterRegistry.find("http_requests_in_flight").gauge();
        assertThat(inFlightGauge.value()).isEqualTo(1);

        // When
        metricsService.decrementInFlightRequests();
        
        // Then
        assertThat(metricsService.getInFlightRequests()).isEqualTo(0);
        assertThat(inFlightGauge.value()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should sanitize HTTP method to uppercase")
    void shouldSanitizeHttpMethod() {
        // Test normal cases
        assertThat(HttpRequestMetricsService.sanitizeHttpMethod("get")).isEqualTo("GET");
        assertThat(HttpRequestMetricsService.sanitizeHttpMethod("POST")).isEqualTo("POST");
        assertThat(HttpRequestMetricsService.sanitizeHttpMethod("  put  ")).isEqualTo("PUT");
        
        // Test edge cases
        assertThat(HttpRequestMetricsService.sanitizeHttpMethod(null)).isEqualTo("UNKNOWN");
        assertThat(HttpRequestMetricsService.sanitizeHttpMethod("")).isEqualTo("UNKNOWN");
        assertThat(HttpRequestMetricsService.sanitizeHttpMethod("   ")).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("Should sanitize route paths to prevent high cardinality")
    void shouldSanitizeRoutePaths() {
        // Test normal paths
        assertThat(HttpRequestMetricsService.sanitizeRoutePath("/api/v1/orders")).isEqualTo("/api/v1/orders");
        assertThat(HttpRequestMetricsService.sanitizeRoutePath("/api/users/{id}")).isEqualTo("/api/users/{id}");
        
        // Test query parameter removal
        assertThat(HttpRequestMetricsService.sanitizeRoutePath("/api/orders?page=1&size=10")).isEqualTo("/api/orders");
        
        // Test fragment removal
        assertThat(HttpRequestMetricsService.sanitizeRoutePath("/api/orders#section")).isEqualTo("/api/orders");
        
        // Test path without leading slash
        assertThat(HttpRequestMetricsService.sanitizeRoutePath("api/orders")).isEqualTo("/api/orders");
        
        // Test edge cases
        assertThat(HttpRequestMetricsService.sanitizeRoutePath(null)).isEqualTo("/unknown");
        assertThat(HttpRequestMetricsService.sanitizeRoutePath("")).isEqualTo("/unknown");
        assertThat(HttpRequestMetricsService.sanitizeRoutePath("   ")).isEqualTo("/unknown");
    }

    @Test
    @DisplayName("Should limit path segments to prevent high cardinality")
    void shouldLimitPathSegments() {
        // Create a path with more than MAX_PATH_SEGMENTS (10)
        String longPath = "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p";
        String sanitized = HttpRequestMetricsService.sanitizeRoutePath(longPath);
        
        // Should be truncated with "..." at the end
        assertThat(sanitized).endsWith("/...");
        
        // Count segments (excluding empty first element from split)
        String[] segments = sanitized.split("/");
        assertThat(segments.length).isLessThanOrEqualTo(11); // 10 segments + "..."
    }

    @Test
    @DisplayName("Should handle multiple requests with same parameters")
    void shouldHandleMultipleRequestsWithSameParameters() {
        // When
        metricsService.recordRequest("GET", "/api/v1/orders", 200, 1000000L);
        metricsService.recordRequest("GET", "/api/v1/orders", 200, 2000000L);
        metricsService.recordRequest("GET", "/api/v1/orders", 200, 3000000L);

        // Then
        Counter counter = meterRegistry.find("http_requests_total")
                .tag("method", "GET")
                .tag("path", "/api/v1/orders")
                .tag("status", "200")
                .counter();
        
        assertThat(counter.count()).isEqualTo(3);

        Timer timer = meterRegistry.find("http_request_duration_seconds")
                .tag("method", "GET")
                .tag("path", "/api/v1/orders")
                .tag("status", "200")
                .timer();
        
        assertThat(timer.count()).isEqualTo(3);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(6000000L);
    }

    @Test
    @DisplayName("Should handle different HTTP methods and status codes")
    void shouldHandleDifferentMethodsAndStatusCodes() {
        // When
        metricsService.recordRequest("GET", "/api/v1/orders", 200, 1000000L);
        metricsService.recordRequest("POST", "/api/v1/orders", 201, 2000000L);
        metricsService.recordRequest("GET", "/api/v1/orders", 404, 500000L);
        metricsService.recordRequest("DELETE", "/api/v1/orders/{id}", 204, 300000L);

        // Then - Each combination should create separate metrics
        assertThat(meterRegistry.find("http_requests_total")
                .tag("method", "GET")
                .tag("status", "200")
                .counter().count()).isEqualTo(1);
        
        assertThat(meterRegistry.find("http_requests_total")
                .tag("method", "POST")
                .tag("status", "201")
                .counter().count()).isEqualTo(1);
        
        assertThat(meterRegistry.find("http_requests_total")
                .tag("method", "GET")
                .tag("status", "404")
                .counter().count()).isEqualTo(1);
        
        assertThat(meterRegistry.find("http_requests_total")
                .tag("method", "DELETE")
                .tag("status", "204")
                .counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should provide cache statistics")
    void shouldProvideCacheStatistics() {
        // When
        metricsService.recordRequest("GET", "/api/v1/orders", 200, 1000000L);
        metricsService.recordRequest("POST", "/api/v1/orders", 201, 2000000L);

        // Then
        String stats = metricsService.getCacheStatistics();
        assertThat(stats).contains("Counters: 2");
        assertThat(stats).contains("Timers: 2");
        assertThat(stats).contains("InFlight: 0");
    }

    @Test
    @DisplayName("Should clear caches when requested")
    void shouldClearCaches() {
        // Given
        metricsService.recordRequest("GET", "/api/v1/orders", 200, 1000000L);
        assertThat(metricsService.getCacheStatistics()).contains("Counters: 1");

        // When
        metricsService.clearCaches();

        // Then
        assertThat(metricsService.getCacheStatistics()).contains("Counters: 0");
        assertThat(metricsService.getCacheStatistics()).contains("Timers: 0");
    }

    @Test
    @DisplayName("Should provide service status information")
    void shouldProvideServiceStatus() {
        // When
        String status = metricsService.getServiceStatus();

        // Then
        assertThat(status).contains("HttpRequestMetricsService Status:");
        assertThat(status).contains("Initialized: true");
        assertThat(status).contains("MeterRegistry: SimpleMeterRegistry");
        assertThat(status).contains("In-flight requests: 0");
        assertThat(status).contains("Cached counters: 0");
        assertThat(status).contains("Cached timers: 0");
    }

    @Test
    @DisplayName("Should handle concurrent requests safely")
    void shouldHandleConcurrentRequestsSafely() throws InterruptedException {
        // Given
        int threadCount = 10;
        int requestsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        // When
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    metricsService.incrementInFlightRequests();
                    metricsService.recordRequest("GET", "/api/v1/orders", 200, 1000000L);
                    metricsService.decrementInFlightRequests();
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then
        Counter counter = meterRegistry.find("http_requests_total")
                .tag("method", "GET")
                .tag("path", "/api/v1/orders")
                .tag("status", "200")
                .counter();
        
        assertThat(counter.count()).isEqualTo(threadCount * requestsPerThread);
        assertThat(metricsService.getInFlightRequests()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle timer with standard buckets")
    void shouldHandleTimerWithStandardBuckets() {
        // When
        metricsService.recordRequest("GET", "/api/v1/orders", 200, 25_000_000L); // 25ms

        // Then
        Timer timer = meterRegistry.find("http_request_duration_seconds")
                .tag("method", "GET")
                .tag("path", "/api/v1/orders")
                .tag("status", "200")
                .timer();
        
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        
        // Verify the timer has the expected duration
        double totalTimeSeconds = timer.totalTime(TimeUnit.SECONDS);
        assertThat(totalTimeSeconds).isCloseTo(0.025, org.assertj.core.data.Offset.offset(0.001));
    }
}