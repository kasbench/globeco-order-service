package org.kasbench.globeco_order_service.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpMetricsServiceTest {
    
    private MeterRegistry meterRegistry;
    private HttpMetricsService httpMetricsService;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        httpMetricsService = new HttpMetricsService(meterRegistry);
    }
    
    @Test
    void testRegisterHttpConnectionPoolMetrics_WithHttpUrl() {
        // Given
        String serviceName = "test-service";
        String serviceUrl = "http://test-service:8080";
        
        // When
        httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, serviceUrl);
        
        // Then
        assertThat(httpMetricsService.getRegisteredServices()).contains(serviceName);
        assertThat(httpMetricsService.getServiceProtocol(serviceName)).isEqualTo("http");
        assertThat(httpMetricsService.getServiceMetrics(serviceName)).isNotNull();
        
        // Verify metrics are registered with initial values
        Gauge totalGauge = meterRegistry.find("http_pool_connections_total")
                .tag("service", serviceName)
                .tag("protocol", "http")
                .gauge();
        assertThat(totalGauge).isNotNull();
        assertThat(totalGauge.value()).isEqualTo(0.0);
        
        Gauge activeGauge = meterRegistry.find("http_pool_connections_active")
                .tag("service", serviceName)
                .tag("protocol", "http")
                .gauge();
        assertThat(activeGauge).isNotNull();
        assertThat(activeGauge.value()).isEqualTo(0.0);
        
        Gauge idleGauge = meterRegistry.find("http_pool_connections_idle")
                .tag("service", serviceName)
                .tag("protocol", "http")
                .gauge();
        assertThat(idleGauge).isNotNull();
        assertThat(idleGauge.value()).isEqualTo(0.0);
    }
    
    @Test
    void testRegisterHttpConnectionPoolMetrics_WithHttpsUrl() {
        // Given
        String serviceName = "secure-service";
        String serviceUrl = "https://secure-service:8443";
        
        // When
        httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, serviceUrl);
        httpMetricsService.updateConnectionPoolMetrics(serviceName, 50, 3, 7);
        
        // Then
        assertThat(httpMetricsService.getServiceProtocol(serviceName)).isEqualTo("https");
        
        // Verify metrics are registered with https protocol
        Gauge totalGauge = meterRegistry.find("http_pool_connections_total")
                .tag("service", serviceName)
                .tag("protocol", "https")
                .gauge();
        assertThat(totalGauge).isNotNull();
        assertThat(totalGauge.value()).isEqualTo(50.0);
        
        Gauge activeGauge = meterRegistry.find("http_pool_connections_active")
                .tag("service", serviceName)
                .tag("protocol", "https")
                .gauge();
        assertThat(activeGauge).isNotNull();
        assertThat(activeGauge.value()).isEqualTo(3.0);
        
        Gauge idleGauge = meterRegistry.find("http_pool_connections_idle")
                .tag("service", serviceName)
                .tag("protocol", "https")
                .gauge();
        assertThat(idleGauge).isNotNull();
        assertThat(idleGauge.value()).isEqualTo(7.0);
    }
    
    @Test
    void testRegisterHttpConnectionPoolMetrics_WithInvalidUrl() {
        // Given
        String serviceName = "invalid-service";
        String serviceUrl = "invalid-url";
        
        // When
        httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, serviceUrl);
        httpMetricsService.updateConnectionPoolMetrics(serviceName, 25, 1, 2);
        
        // Then
        assertThat(httpMetricsService.getServiceProtocol(serviceName)).isEqualTo("unknown");
        
        // Verify metrics are still registered with unknown protocol
        Gauge totalGauge = meterRegistry.find("http_pool_connections_total")
                .tag("service", serviceName)
                .tag("protocol", "unknown")
                .gauge();
        assertThat(totalGauge).isNotNull();
        assertThat(totalGauge.value()).isEqualTo(25.0);
        
        Gauge activeGauge = meterRegistry.find("http_pool_connections_active")
                .tag("service", serviceName)
                .tag("protocol", "unknown")
                .gauge();
        assertThat(activeGauge).isNotNull();
        assertThat(activeGauge.value()).isEqualTo(1.0);
        
        Gauge idleGauge = meterRegistry.find("http_pool_connections_idle")
                .tag("service", serviceName)
                .tag("protocol", "unknown")
                .gauge();
        assertThat(idleGauge).isNotNull();
        assertThat(idleGauge.value()).isEqualTo(2.0);
    }
    
    @Test
    void testRegisterHttpConnectionPoolMetrics_WithNullServiceName() {
        // Given
        String serviceName = null;
        String serviceUrl = "http://test-service:8080";
        
        // When
        httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, serviceUrl);
        
        // Then
        assertThat(httpMetricsService.getRegisteredServices()).isEmpty();
    }
    
    @Test
    void testRegisterHttpConnectionPoolMetrics_WithEmptyServiceName() {
        // Given
        String serviceName = "";
        String serviceUrl = "http://test-service:8080";
        
        // When
        httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, serviceUrl);
        
        // Then
        assertThat(httpMetricsService.getRegisteredServices()).isEmpty();
    }
    
    @Test
    void testRegisterHttpConnectionPoolMetrics_WithNullUrl() {
        // Given
        String serviceName = "test-service";
        String serviceUrl = null;
        
        // When
        httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, serviceUrl);
        httpMetricsService.updateConnectionPoolMetrics(serviceName, 100, 5, 10);
        
        // Then
        assertThat(httpMetricsService.getServiceProtocol(serviceName)).isEqualTo("unknown");
        
        // Verify metrics are registered with unknown protocol
        Gauge totalGauge = meterRegistry.find("http_pool_connections_total")
                .tag("service", serviceName)
                .tag("protocol", "unknown")
                .gauge();
        assertThat(totalGauge).isNotNull();
        assertThat(totalGauge.value()).isEqualTo(100.0);
        
        Gauge activeGauge = meterRegistry.find("http_pool_connections_active")
                .tag("service", serviceName)
                .tag("protocol", "unknown")
                .gauge();
        assertThat(activeGauge).isNotNull();
        assertThat(activeGauge.value()).isEqualTo(5.0);
        
        Gauge idleGauge = meterRegistry.find("http_pool_connections_idle")
                .tag("service", serviceName)
                .tag("protocol", "unknown")
                .gauge();
        assertThat(idleGauge).isNotNull();
        assertThat(idleGauge.value()).isEqualTo(10.0);
    }
    
    @Test
    void testUpdateConnectionPoolMetrics() {
        // Given
        String serviceName = "test-service";
        String serviceUrl = "http://test-service:8080";
        httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, serviceUrl);
        
        // When
        httpMetricsService.updateConnectionPoolMetrics(serviceName, 50, 10, 15);
        
        // Then
        HttpMetricsService.HttpConnectionPoolMetrics metrics = httpMetricsService.getServiceMetrics(serviceName);
        assertThat(metrics.getTotalConnections()).isEqualTo(50);
        assertThat(metrics.getActiveConnections()).isEqualTo(10);
        assertThat(metrics.getIdleConnections()).isEqualTo(15);
        
        // Verify gauge values are updated
        Gauge totalGauge = meterRegistry.find("http_pool_connections_total")
                .tag("service", serviceName)
                .tag("protocol", "http")
                .gauge();
        assertThat(totalGauge.value()).isEqualTo(50.0);
        
        Gauge activeGauge = meterRegistry.find("http_pool_connections_active")
                .tag("service", serviceName)
                .tag("protocol", "http")
                .gauge();
        assertThat(activeGauge.value()).isEqualTo(10.0);
        
        Gauge idleGauge = meterRegistry.find("http_pool_connections_idle")
                .tag("service", serviceName)
                .tag("protocol", "http")
                .gauge();
        assertThat(idleGauge.value()).isEqualTo(15.0);
    }
    
    @Test
    void testUpdateConnectionPoolMetrics_UnregisteredService() {
        // Given
        String serviceName = "unregistered-service";
        
        // When
        httpMetricsService.updateConnectionPoolMetrics(serviceName, 50, 10, 15);
        
        // Then - should not throw exception, just log and ignore
        assertThat(httpMetricsService.getServiceMetrics(serviceName)).isNull();
    }
    
    @Test
    void testMultipleServiceRegistration() {
        // When
        httpMetricsService.registerHttpConnectionPoolMetrics("service1", "http://service1:8080");
        httpMetricsService.registerHttpConnectionPoolMetrics("service2", "https://service2:8443");
        
        httpMetricsService.updateConnectionPoolMetrics("service1", 100, 5, 10);
        httpMetricsService.updateConnectionPoolMetrics("service2", 200, 15, 25);
        
        // Then
        assertThat(httpMetricsService.getRegisteredServices()).containsExactlyInAnyOrder("service1", "service2");
        assertThat(httpMetricsService.getServiceProtocol("service1")).isEqualTo("http");
        assertThat(httpMetricsService.getServiceProtocol("service2")).isEqualTo("https");
        
        // Verify both services have their metrics registered
        Gauge service1Total = meterRegistry.find("http_pool_connections_total")
                .tag("service", "service1")
                .tag("protocol", "http")
                .gauge();
        assertThat(service1Total.value()).isEqualTo(100.0);
        
        Gauge service1Active = meterRegistry.find("http_pool_connections_active")
                .tag("service", "service1")
                .tag("protocol", "http")
                .gauge();
        assertThat(service1Active.value()).isEqualTo(5.0);
        
        Gauge service1Idle = meterRegistry.find("http_pool_connections_idle")
                .tag("service", "service1")
                .tag("protocol", "http")
                .gauge();
        assertThat(service1Idle.value()).isEqualTo(10.0);
        
        Gauge service2Total = meterRegistry.find("http_pool_connections_total")
                .tag("service", "service2")
                .tag("protocol", "https")
                .gauge();
        assertThat(service2Total.value()).isEqualTo(200.0);
        
        Gauge service2Active = meterRegistry.find("http_pool_connections_active")
                .tag("service", "service2")
                .tag("protocol", "https")
                .gauge();
        assertThat(service2Active.value()).isEqualTo(15.0);
        
        Gauge service2Idle = meterRegistry.find("http_pool_connections_idle")
                .tag("service", "service2")
                .tag("protocol", "https")
                .gauge();
        assertThat(service2Idle.value()).isEqualTo(25.0);
    }
    
    @Test
    void testProtocolDetection() {
        // Test various URL formats through registerHttpConnectionPoolMetrics
        httpMetricsService.registerHttpConnectionPoolMetrics("http-service", "http://example.com");
        assertThat(httpMetricsService.getServiceProtocol("http-service")).isEqualTo("http");
        
        httpMetricsService.registerHttpConnectionPoolMetrics("https-service", "https://example.com");
        assertThat(httpMetricsService.getServiceProtocol("https-service")).isEqualTo("https");
        
        httpMetricsService.registerHttpConnectionPoolMetrics("no-protocol-service", "example.com");
        assertThat(httpMetricsService.getServiceProtocol("no-protocol-service")).isEqualTo("unknown");
        
        httpMetricsService.registerHttpConnectionPoolMetrics("empty-url-service", "");
        assertThat(httpMetricsService.getServiceProtocol("empty-url-service")).isEqualTo("unknown");
    }
}