package org.kasbench.globeco_order_service.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.kasbench.globeco_order_service.config.MetricsProperties;
import org.kasbench.globeco_order_service.service.HttpRequestMetricsService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MetricsDebugController.
 * Tests the debug endpoints for HTTP request metrics validation.
 */
class MetricsDebugControllerTest {

    private MeterRegistry meterRegistry;
    private MetricsProperties metricsProperties;
    
    @Mock
    private HttpRequestMetricsService httpRequestMetricsService;
    
    private MetricsDebugController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        meterRegistry = new SimpleMeterRegistry();
        metricsProperties = createDefaultMetricsProperties();
        
        controller = new MetricsDebugController(meterRegistry, metricsProperties, httpRequestMetricsService);
    }

    @Test
    @DisplayName("Should return metrics overview with all sections")
    void shouldReturnMetricsOverview() {
        // Given
        when(httpRequestMetricsService.isInitialized()).thenReturn(true);
        when(httpRequestMetricsService.getInFlightRequests()).thenReturn(0);
        when(httpRequestMetricsService.getCacheStatistics()).thenReturn("Test cache stats");
        when(httpRequestMetricsService.getServiceStatus()).thenReturn("Test service status");
        
        // When
        Map<String, Object> overview = controller.getMetricsOverview();
        
        // Then
        assertThat(overview).containsKeys(
            "timestamp",
            "configuration", 
            "httpRequestCounters",
            "httpRequestTimers",
            "inFlightRequests",
            "cacheStatistics",
            "serviceStatus"
        );
        assertThat(overview.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should return configuration information")
    void shouldReturnConfigurationInfo() {
        // When
        Map<String, Object> config = controller.getConfigurationInfo();
        
        // Then
        assertThat(config).containsEntry("globalEnabled", true);
        assertThat(config).containsEntry("httpEnabled", true);
        assertThat(config).containsEntry("httpRequestEnabled", true);
        assertThat(config).containsEntry("routeSanitizationEnabled", true);
        assertThat(config).containsEntry("maxPathSegments", 10);
        assertThat(config).containsEntry("metricCachingEnabled", true);
        assertThat(config).containsEntry("maxCacheSize", 1000);
        assertThat(config).containsEntry("detailedLoggingEnabled", false);
        assertThat(config).containsKey("collectionInterval");
    }

    @Test
    @DisplayName("Should return empty counters when no HTTP request counters exist")
    void shouldReturnEmptyCountersWhenNoneExist() {
        // When
        Map<String, Object> counters = controller.getHttpRequestCounters();
        
        // Then
        assertThat(counters).isEmpty();
    }

    @Test
    @DisplayName("Should return empty timers when no HTTP request timers exist")
    void shouldReturnEmptyTimersWhenNoneExist() {
        // When
        Map<String, Object> timers = controller.getHttpRequestTimers();
        
        // Then
        assertThat(timers).isEmpty();
    }

    @Test
    @DisplayName("Should return in-flight requests information")
    void shouldReturnInFlightRequestsInfo() {
        // When
        Map<String, Object> inFlight = controller.getInFlightRequests();
        
        // Then
        assertThat(inFlight).containsKey("current");
        // Since no gauge is registered in test, it should indicate not found
        assertThat(inFlight.get("current")).isEqualTo("not_found");
        assertThat(inFlight).containsKey("error");
    }

    @Test
    @DisplayName("Should return cache statistics when service is available")
    void shouldReturnCacheStatisticsWhenServiceAvailable() {
        // Given
        when(httpRequestMetricsService.getCacheStatistics()).thenReturn("Counters: 5, Timers: 3, InFlight: 2");
        
        // When
        Map<String, Object> cache = controller.getCacheStatistics();
        
        // Then
        assertThat(cache).containsEntry("serviceAvailable", true);
        assertThat(cache).containsEntry("statistics", "Counters: 5, Timers: 3, InFlight: 2");
    }

    @Test
    @DisplayName("Should return service status when service is available")
    void shouldReturnServiceStatusWhenServiceAvailable() {
        // Given
        when(httpRequestMetricsService.isInitialized()).thenReturn(true);
        when(httpRequestMetricsService.getInFlightRequests()).thenReturn(5);
        when(httpRequestMetricsService.getServiceStatus()).thenReturn("Service operational");
        
        // When
        Map<String, Object> status = controller.getServiceStatus();
        
        // Then
        assertThat(status).containsEntry("serviceAvailable", true);
        assertThat(status).containsEntry("initialized", true);
        assertThat(status).containsEntry("inFlightRequests", 5);
        assertThat(status).containsEntry("serviceStatus", "Service operational");
    }

    @Test
    @DisplayName("Should return all HTTP-related meters")
    void shouldReturnAllHttpMeters() {
        // Given - register some test meters
        meterRegistry.counter("http_requests_total", "method", "GET", "path", "/test", "status", "200");
        meterRegistry.timer("http_request_duration_seconds", "method", "POST", "path", "/api", "status", "201");
        meterRegistry.gauge("http_requests_in_flight", 3);
        
        // When
        Map<String, Object> allMeters = controller.getAllMeters();
        
        // Then
        assertThat(allMeters).isNotEmpty();
        assertThat(allMeters.keySet().stream().anyMatch(key -> key.contains("http_requests_total"))).isTrue();
        assertThat(allMeters.keySet().stream().anyMatch(key -> key.contains("http_request_duration_seconds"))).isTrue();
        assertThat(allMeters.keySet().stream().anyMatch(key -> key.contains("http_requests_in_flight"))).isTrue();
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