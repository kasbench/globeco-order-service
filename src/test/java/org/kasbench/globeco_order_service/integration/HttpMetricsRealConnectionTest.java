package org.kasbench.globeco_order_service.integration;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.service.HttpMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class HttpMetricsRealConnectionTest {

    @Autowired
    private HttpMetricsService httpMetricsService;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Autowired
    private PoolingHttpClientConnectionManager connectionManager;

    @Test
    void shouldHaveRealConnectionManagerAvailable() {
        // Verify that the connection manager is properly injected
        assertThat(connectionManager).isNotNull();
        assertThat(httpMetricsService).isNotNull();
    }

    @Test
    void shouldRegisterAndUpdateHttpMetricsWithRealConnectionManager() {
        // Given
        String serviceName = "test-service";
        String serviceUrl = "http://test-service:8080";
        
        // When - register metrics
        httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, serviceUrl);
        
        // Force an immediate update to get real values
        httpMetricsService.forceMetricsUpdate();
        
        // Then - verify metrics are registered and have values
        Gauge totalGauge = meterRegistry.find("http_pool_connections_total")
                .tag("service", serviceName)
                .gauge();
        assertThat(totalGauge).isNotNull();
        assertThat(totalGauge.value()).isGreaterThan(0); // Should have real connection pool values
        
        Gauge activeGauge = meterRegistry.find("http_pool_connections_active")
                .tag("service", serviceName)
                .gauge();
        assertThat(activeGauge).isNotNull();
        assertThat(activeGauge.value()).isGreaterThanOrEqualTo(0);
        
        Gauge idleGauge = meterRegistry.find("http_pool_connections_idle")
                .tag("service", serviceName)
                .gauge();
        assertThat(idleGauge).isNotNull();
        assertThat(idleGauge.value()).isGreaterThanOrEqualTo(0);
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