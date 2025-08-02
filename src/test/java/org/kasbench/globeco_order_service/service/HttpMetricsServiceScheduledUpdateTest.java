package org.kasbench.globeco_order_service.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.pool.PoolStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpMetricsServiceScheduledUpdateTest {

    private MeterRegistry meterRegistry;
    private HttpMetricsService httpMetricsService;
    
    @Mock
    private PoolingHttpClientConnectionManager connectionManager;
    
    @Mock
    private PoolStats poolStats;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void shouldUpdateMetricsAutomaticallyWithRealConnectionManager() throws InterruptedException {
        // Given
        when(connectionManager.getTotalStats()).thenReturn(poolStats);
        when(connectionManager.getMaxTotal()).thenReturn(100);
        when(poolStats.getLeased()).thenReturn(5);
        when(poolStats.getAvailable()).thenReturn(15);
        when(poolStats.getPending()).thenReturn(2);
        
        httpMetricsService = new HttpMetricsService(meterRegistry, connectionManager);
        httpMetricsService.registerHttpConnectionPoolMetrics("test-service", "http://test:8080");
        
        // When - force an immediate update
        httpMetricsService.forceMetricsUpdate();
        
        // Then - verify metrics are updated with real values
        Gauge totalGauge = meterRegistry.find("http_pool_connections_total")
                .tag("service", "test-service")
                .gauge();
        assertThat(totalGauge).isNotNull();
        assertThat(totalGauge.value()).isEqualTo(100);
        
        Gauge activeGauge = meterRegistry.find("http_pool_connections_active")
                .tag("service", "test-service")
                .gauge();
        assertThat(activeGauge).isNotNull();
        assertThat(activeGauge.value()).isEqualTo(5);
        
        Gauge idleGauge = meterRegistry.find("http_pool_connections_idle")
                .tag("service", "test-service")
                .gauge();
        assertThat(idleGauge).isNotNull();
        assertThat(idleGauge.value()).isEqualTo(13); // available - pending = 15 - 2 = 13
    }

    @Test
    void shouldUpdateMetricsWithDefaultValuesWhenConnectionManagerUnavailable() {
        // Given
        httpMetricsService = new HttpMetricsService(meterRegistry, null);
        httpMetricsService.registerHttpConnectionPoolMetrics("test-service", "http://test:8080");
        
        // When - force an immediate update
        httpMetricsService.forceMetricsUpdate();
        
        // Then - verify metrics are updated with default values
        Gauge totalGauge = meterRegistry.find("http_pool_connections_total")
                .tag("service", "test-service")
                .gauge();
        assertThat(totalGauge).isNotNull();
        assertThat(totalGauge.value()).isEqualTo(20); // Default max per route
        
        Gauge activeGauge = meterRegistry.find("http_pool_connections_active")
                .tag("service", "test-service")
                .gauge();
        assertThat(activeGauge).isNotNull();
        assertThat(activeGauge.value()).isEqualTo(0); // No active when manager unavailable
        
        Gauge idleGauge = meterRegistry.find("http_pool_connections_idle")
                .tag("service", "test-service")
                .gauge();
        assertThat(idleGauge).isNotNull();
        assertThat(idleGauge.value()).isEqualTo(0); // No idle when manager unavailable
    }

    @Test
    void shouldProvideConnectionPoolStatus() {
        // Given
        when(connectionManager.getTotalStats()).thenReturn(poolStats);
        when(connectionManager.getMaxTotal()).thenReturn(100);
        when(connectionManager.getDefaultMaxPerRoute()).thenReturn(20);
        when(poolStats.getLeased()).thenReturn(5);
        when(poolStats.getAvailable()).thenReturn(15);
        when(poolStats.getPending()).thenReturn(2);
        when(poolStats.getMax()).thenReturn(100);
        
        httpMetricsService = new HttpMetricsService(meterRegistry, connectionManager);
        httpMetricsService.registerHttpConnectionPoolMetrics("test-service", "http://test:8080");
        
        // When
        String status = httpMetricsService.getConnectionPoolStatus();
        
        // Then
        assertThat(status).contains("HTTP Connection Pool Status:");
        assertThat(status).contains("Total Max: 100");
        assertThat(status).contains("Default Max Per Route: 20");
        assertThat(status).contains("Total Leased: 5");
        assertThat(status).contains("Total Available: 15");
        assertThat(status).contains("Total Pending: 2");
        assertThat(status).contains("Registered Services: 1");
    }

    @Test
    void shouldHandleConnectionPoolStatusWhenManagerUnavailable() {
        // Given
        httpMetricsService = new HttpMetricsService(meterRegistry, null);
        
        // When
        String status = httpMetricsService.getConnectionPoolStatus();
        
        // Then
        assertThat(status).isEqualTo("Connection manager not available");
    }
}