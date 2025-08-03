package org.kasbench.globeco_order_service.integration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.kasbench.globeco_order_service.config.MetricsProperties;
import org.kasbench.globeco_order_service.service.HttpRequestMetricsService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for HttpRequestMetricsService in Spring context.
 * Verifies that the service integrates properly with Spring Boot and Micrometer.
 */
@SpringJUnitConfig(HttpRequestMetricsServiceIntegrationTest.TestConfig.class)
class HttpRequestMetricsServiceIntegrationTest {

    private HttpRequestMetricsService httpRequestMetricsService;
    private MeterRegistry meterRegistry;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        public MetricsProperties metricsProperties() {
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

        @Bean
        public HttpRequestMetricsService httpRequestMetricsService(MeterRegistry meterRegistry, MetricsProperties metricsProperties) {
            return new HttpRequestMetricsService(meterRegistry, metricsProperties);
        }
    }

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        MetricsProperties metricsProperties = createDefaultMetricsProperties();
        httpRequestMetricsService = new HttpRequestMetricsService(meterRegistry, metricsProperties);
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

    @Test
    @DisplayName("Should be properly initialized in Spring context")
    void shouldBeProperlyInitializedInSpringContext() {
        // Then
        assertThat(httpRequestMetricsService).isNotNull();
        assertThat(httpRequestMetricsService.isInitialized()).isTrue();
    }

    @Test
    @DisplayName("Should register in-flight gauge on startup")
    void shouldRegisterInFlightGaugeOnStartup() {
        // Then
        Gauge inFlightGauge = meterRegistry.find("http_requests_in_flight").gauge();
        assertThat(inFlightGauge).isNotNull();
        assertThat(inFlightGauge.value()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should record metrics through Spring-managed MeterRegistry")
    void shouldRecordMetricsThroughSpringManagedMeterRegistry() {
        // When
        httpRequestMetricsService.recordRequest("GET", "/api/v1/orders", 200, 25_000_000L);

        // Then
        Counter counter = meterRegistry.find("http_requests_total")
                .tag("method", "GET")
                .tag("path", "/api/v1/orders")
                .tag("status", "200")
                .counter();
        
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);

        Timer timer = meterRegistry.find("http_request_duration_seconds")
                .tag("method", "GET")
                .tag("path", "/api/v1/orders")
                .tag("status", "200")
                .timer();
        
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(25_000_000L);
    }

    @Test
    @DisplayName("Should manage in-flight requests correctly")
    void shouldManageInFlightRequestsCorrectly() {
        // Given
        int initialInFlight = httpRequestMetricsService.getInFlightRequests();

        // When
        httpRequestMetricsService.incrementInFlightRequests();
        
        // Then
        assertThat(httpRequestMetricsService.getInFlightRequests()).isEqualTo(initialInFlight + 1);
        
        Gauge inFlightGauge = meterRegistry.find("http_requests_in_flight").gauge();
        assertThat(inFlightGauge.value()).isEqualTo(initialInFlight + 1);

        // When
        httpRequestMetricsService.decrementInFlightRequests();
        
        // Then
        assertThat(httpRequestMetricsService.getInFlightRequests()).isEqualTo(initialInFlight);
        assertThat(inFlightGauge.value()).isEqualTo(initialInFlight);
    }

    @Test
    @DisplayName("Should provide service status information")
    void shouldProvideServiceStatusInformation() {
        // When
        String status = httpRequestMetricsService.getServiceStatus();

        // Then
        assertThat(status).contains("HttpRequestMetricsService Status:");
        assertThat(status).contains("Initialized: true");
        assertThat(status).contains("MeterRegistry:");
    }

    @Test
    @DisplayName("Should handle multiple concurrent requests")
    void shouldHandleMultipleConcurrentRequests() throws InterruptedException {
        // Given
        int threadCount = 5;
        int requestsPerThread = 10;
        Thread[] threads = new Thread[threadCount];

        // When
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    httpRequestMetricsService.incrementInFlightRequests();
                    httpRequestMetricsService.recordRequest("POST", "/api/v1/orders", 201, 1000000L);
                    httpRequestMetricsService.decrementInFlightRequests();
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
                .tag("method", "POST")
                .tag("path", "/api/v1/orders")
                .tag("status", "201")
                .counter();
        
        assertThat(counter.count()).isEqualTo(threadCount * requestsPerThread);
    }
}