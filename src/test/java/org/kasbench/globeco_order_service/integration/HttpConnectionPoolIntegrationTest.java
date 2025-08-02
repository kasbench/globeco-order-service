package org.kasbench.globeco_order_service.integration;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.service.HttpMetricsService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify HTTP connection pool metrics work with actual connection manager.
 */
class HttpConnectionPoolIntegrationTest {

    private MeterRegistry meterRegistry;
    private HttpMetricsService httpMetricsService;
    private PoolingHttpClientConnectionManager connectionManager;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(10);
        httpMetricsService = new HttpMetricsService(meterRegistry, connectionManager);
    }

    @Test
    void shouldCollectRealConnectionPoolMetrics() {
        // Given
        String serviceName = "integration-test-service";
        String serviceUrl = "https://example.com";

        // When
        httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, serviceUrl);
        httpMetricsService.updateConnectionPoolMetricsFromManager();

        // Then - verify metrics are registered
        Gauge totalGauge = meterRegistry.find("http_pool_connections_total")
                .tag("service", serviceName)
                .tag("protocol", "https")
                .gauge();
        assertThat(totalGauge).isNotNull();

        Gauge activeGauge = meterRegistry.find("http_pool_connections_active")
                .tag("service", serviceName)
                .tag("protocol", "https")
                .gauge();
        assertThat(activeGauge).isNotNull();

        Gauge idleGauge = meterRegistry.find("http_pool_connections_idle")
                .tag("service", serviceName)
                .tag("protocol", "https")
                .gauge();
        assertThat(idleGauge).isNotNull();

        // Verify metrics have reasonable values from actual connection manager
        assertThat(totalGauge.value()).isEqualTo(100); // connectionManager.getMaxTotal()
        assertThat(activeGauge.value()).isGreaterThanOrEqualTo(0);
        assertThat(idleGauge.value()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldUpdateServiceSpecificMetrics() {
        // Given
        String serviceName = "service-specific-test";
        String serviceUrl = "http://test-service:8080";

        // When
        httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, serviceUrl);
        httpMetricsService.updateServiceSpecificMetrics(serviceName, serviceUrl);

        // Then - verify service-specific metrics are updated
        Gauge totalGauge = meterRegistry.find("http_pool_connections_total")
                .tag("service", serviceName)
                .tag("protocol", "http")
                .gauge();
        assertThat(totalGauge).isNotNull();
        assertThat(totalGauge.value()).isEqualTo(10); // connectionManager.getDefaultMaxPerRoute()
    }

    @Test
    void shouldProvideConnectionPoolStatus() {
        // When
        String status = httpMetricsService.getConnectionPoolStatus();

        // Then
        assertThat(status).contains("HTTP Connection Pool Status:");
        assertThat(status).contains("Total Max:");
        assertThat(status).contains("Default Max Per Route:");
        assertThat(status).contains("Total Leased:");
        assertThat(status).contains("Total Available:");
        assertThat(status).contains("Total Pending:");
    }
}