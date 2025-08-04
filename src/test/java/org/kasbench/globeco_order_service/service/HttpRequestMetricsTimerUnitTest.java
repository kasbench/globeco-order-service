package org.kasbench.globeco_order_service.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.kasbench.globeco_order_service.config.MetricsProperties;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests to verify that HTTP request timer metrics are recorded with correct units.
 * This test specifically addresses the issue where timer values were being recorded
 * in nanoseconds but the metric name indicated seconds, causing unit conversion issues
 * in the metrics pipeline.
 */
class HttpRequestMetricsTimerUnitTest {

    private MeterRegistry meterRegistry;
    private MetricsProperties metricsProperties;
    private HttpRequestMetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsProperties = createDefaultMetricsProperties();
        metricsService = new HttpRequestMetricsService(meterRegistry, metricsProperties);
    }

    @Test
    @DisplayName("Should record timer values in correct units matching metric name")
    void shouldRecordTimerValuesInCorrectUnits() {
        // Given - a request that takes 500 milliseconds (500,000,000 nanoseconds)
        String method = "GET";
        String path = "/api/test";
        int statusCode = 200;
        long durationNanos = 500_000_000L; // 500ms in nanoseconds

        // When
        metricsService.recordRequest(method, path, statusCode, durationNanos);

        // Then
        Timer timer = meterRegistry.find("http_request_duration_seconds")
                .tag("method", method)
                .tag("path", path)
                .tag("status", "200")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        
        // The timer should show the duration in seconds (0.5 seconds)
        double totalTimeSeconds = timer.totalTime(TimeUnit.SECONDS);
        assertThat(totalTimeSeconds).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
        
        // Mean should also be in seconds
        double meanSeconds = timer.mean(TimeUnit.SECONDS);
        assertThat(meanSeconds).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("Should handle very short durations correctly")
    void shouldHandleVeryShortDurationsCorrectly() {
        // Given - a very short request (1 millisecond = 1,000,000 nanoseconds)
        String method = "GET";
        String path = "/api/fast";
        int statusCode = 200;
        long durationNanos = 1_000_000L; // 1ms in nanoseconds

        // When
        metricsService.recordRequest(method, path, statusCode, durationNanos);

        // Then
        Timer timer = meterRegistry.find("http_request_duration_seconds")
                .tag("method", method)
                .tag("path", path)
                .tag("status", "200")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        
        // The timer should show the duration in seconds (0.001 seconds)
        double totalTimeSeconds = timer.totalTime(TimeUnit.SECONDS);
        assertThat(totalTimeSeconds).isCloseTo(0.001, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("Should handle long durations correctly")
    void shouldHandleLongDurationsCorrectly() {
        // Given - a long request (5 seconds = 5,000,000,000 nanoseconds)
        String method = "POST";
        String path = "/api/slow";
        int statusCode = 200;
        long durationNanos = 5_000_000_000L; // 5s in nanoseconds

        // When
        metricsService.recordRequest(method, path, statusCode, durationNanos);

        // Then
        Timer timer = meterRegistry.find("http_request_duration_seconds")
                .tag("method", method)
                .tag("path", path)
                .tag("status", "200")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        
        // The timer should show the duration in seconds (5.0 seconds)
        double totalTimeSeconds = timer.totalTime(TimeUnit.SECONDS);
        assertThat(totalTimeSeconds).isCloseTo(5.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("Should accumulate multiple requests correctly")
    void shouldAccumulateMultipleRequestsCorrectly() {
        // Given - multiple requests with different durations
        String method = "GET";
        String path = "/api/test";
        int statusCode = 200;

        // When - record three requests: 100ms, 200ms, 300ms
        metricsService.recordRequest(method, path, statusCode, 100_000_000L); // 100ms
        metricsService.recordRequest(method, path, statusCode, 200_000_000L); // 200ms
        metricsService.recordRequest(method, path, statusCode, 300_000_000L); // 300ms

        // Then
        Timer timer = meterRegistry.find("http_request_duration_seconds")
                .tag("method", method)
                .tag("path", path)
                .tag("status", "200")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(3);
        
        // Total time should be 0.6 seconds (0.1 + 0.2 + 0.3)
        double totalTimeSeconds = timer.totalTime(TimeUnit.SECONDS);
        assertThat(totalTimeSeconds).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.001));
        
        // Mean should be 0.2 seconds (0.6 / 3)
        double meanSeconds = timer.mean(TimeUnit.SECONDS);
        assertThat(meanSeconds).isCloseTo(0.2, org.assertj.core.data.Offset.offset(0.001));
    }

    private MetricsProperties createDefaultMetricsProperties() {
        MetricsProperties properties = new MetricsProperties();
        properties.setEnabled(true);
        properties.getHttp().setEnabled(true);
        properties.getHttp().getRequest().setEnabled(true);
        properties.getHttp().getRequest().setRouteSanitizationEnabled(true);
        properties.getHttp().getRequest().setMaxPathSegments(10);
        properties.getHttp().getRequest().setMetricCachingEnabled(true);
        properties.getHttp().getRequest().setMaxCacheSize(1000);
        properties.getHttp().getRequest().setDetailedLoggingEnabled(false);
        return properties;
    }
}