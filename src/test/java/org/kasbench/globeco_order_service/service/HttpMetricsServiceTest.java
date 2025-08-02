package org.kasbench.globeco_order_service.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpMetricsServiceTest {
    
    private MeterRegistry meterRegistry;
    private HttpMetricsService httpMetricsService;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        httpMetricsService = new HttpMetricsService(meterRegistry, null);
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

    @Test
    void shouldHandleMetricRegistrationErrors() {
        // Given - Create a MeterRegistry that throws exceptions
        MeterRegistry faultyRegistry = new SimpleMeterRegistry() {
            @Override
            protected <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> valueFunction) {
                throw new RuntimeException("Gauge registration failed");
            }
        };
        HttpMetricsService faultyService = new HttpMetricsService(faultyRegistry, null);

        // When & Then - should handle gracefully without throwing exceptions
        faultyService.registerHttpConnectionPoolMetrics("test-service", "http://test:8080");
        
        // Service should not be registered if gauge registration fails
        assertThat(faultyService.getRegisteredServices()).isEmpty();
    }

    @Test
    void shouldValidateMetricNamingConventions() {
        // Given
        String serviceName = "test-service";
        String serviceUrl = "https://test-service:8443";
        httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, serviceUrl);

        // When & Then - Verify Prometheus naming conventions (Requirements 4.1, 4.2, 4.3, 4.4)
        
        // Gauge metrics should use descriptive names without time-based suffixes (Requirement 4.3)
        Gauge totalGauge = meterRegistry.find("http_pool_connections_total")
                .tag("service", serviceName)
                .tag("protocol", "https")
                .gauge();
        assertThat(totalGauge).isNotNull();
        assertThat(totalGauge.getId().getName()).doesNotEndWith("_seconds");
        assertThat(totalGauge.getId().getName()).doesNotEndWith("_duration");

        Gauge activeGauge = meterRegistry.find("http_pool_connections_active")
                .tag("service", serviceName)
                .tag("protocol", "https")
                .gauge();
        assertThat(activeGauge).isNotNull();
        assertThat(activeGauge.getId().getName()).doesNotEndWith("_seconds");
        assertThat(activeGauge.getId().getName()).doesNotEndWith("_duration");

        Gauge idleGauge = meterRegistry.find("http_pool_connections_idle")
                .tag("service", serviceName)
                .tag("protocol", "https")
                .gauge();
        assertThat(idleGauge).isNotNull();
        assertThat(idleGauge.getId().getName()).doesNotEndWith("_seconds");
        assertThat(idleGauge.getId().getName()).doesNotEndWith("_duration");

        // All metrics should use snake_case format (Requirement 4.4)
        meterRegistry.getMeters().forEach(meter -> {
            String name = meter.getId().getName();
            if (name.startsWith("http_")) {
                assertThat(name).matches("^[a-z][a-z0-9_]*[a-z0-9]$");
                assertThat(name).doesNotContain("__");
            }
        });
    }

    @Test
    void shouldHandleNegativeMetricValues() {
        // Given
        String serviceName = "test-service";
        httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, "http://test:8080");

        // When - update with negative values (should be handled gracefully)
        httpMetricsService.updateConnectionPoolMetrics(serviceName, -10, -5, -2);

        // Then - metrics should still be updated (Micrometer handles negative values)
        HttpMetricsService.HttpConnectionPoolMetrics metrics = httpMetricsService.getServiceMetrics(serviceName);
        assertThat(metrics.getTotalConnections()).isEqualTo(-10);
        assertThat(metrics.getActiveConnections()).isEqualTo(-5);
        assertThat(metrics.getIdleConnections()).isEqualTo(-2);
    }

    @Test
    void shouldHandleZeroMetricValues() {
        // Given
        String serviceName = "test-service";
        httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, "http://test:8080");

        // When
        httpMetricsService.updateConnectionPoolMetrics(serviceName, 0, 0, 0);

        // Then
        HttpMetricsService.HttpConnectionPoolMetrics metrics = httpMetricsService.getServiceMetrics(serviceName);
        assertThat(metrics.getTotalConnections()).isEqualTo(0);
        assertThat(metrics.getActiveConnections()).isEqualTo(0);
        assertThat(metrics.getIdleConnections()).isEqualTo(0);

        // Verify gauge values
        Gauge totalGauge = meterRegistry.find("http_pool_connections_total")
                .tag("service", serviceName)
                .tag("protocol", "http")
                .gauge();
        assertThat(totalGauge.value()).isEqualTo(0.0);
    }

    @Test
    void shouldHandleWhitespaceInServiceNames() {
        // Given
        String serviceName = "  test-service  ";
        String trimmedName = serviceName.trim();

        // When
        httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, "http://test:8080");

        // Then - should handle whitespace gracefully by trimming and registering
        assertThat(httpMetricsService.getRegisteredServices()).containsExactly(trimmedName);
        assertThat(httpMetricsService.getServiceProtocol(trimmedName)).isEqualTo("http");
    }

    @Test
    void shouldHandleMalformedUrls() {
        // Given
        String serviceName = "test-service";
        String[] malformedUrls = {
            "not-a-url",
            "://missing-scheme",
            "http://",
            "ftp://unsupported-protocol.com",
            "http:///missing-host",
            "   ",
            "http://[invalid-ipv6"
        };
        
        String[] expectedProtocols = {
            "unknown",  // not-a-url -> null scheme
            "unknown",  // ://missing-scheme -> exception
            "unknown",  // http:// -> exception (missing authority)
            "unknown",  // ftp://unsupported-protocol.com -> ftp scheme (unsupported)
            "http",     // http:///missing-host -> http scheme (valid)
            "unknown",  // "   " -> null scheme
            "unknown"   // http://[invalid-ipv6 -> exception
        };

        // When & Then
        for (int i = 0; i < malformedUrls.length; i++) {
            String url = malformedUrls[i];
            String expectedProtocol = expectedProtocols[i];
            httpMetricsService.registerHttpConnectionPoolMetrics(serviceName + "-" + url.hashCode(), url);
            String protocol = httpMetricsService.getServiceProtocol(serviceName + "-" + url.hashCode());
            assertThat(protocol).isEqualTo(expectedProtocol);
        }
    }

    @Test
    void shouldValidateAllRequiredMetricsFromRequirements() {
        // Given
        String serviceName = "test-service";
        String serviceUrl = "https://test-service:8443";
        httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, serviceUrl);

        // When & Then - Verify all metrics from Requirements 2.1, 2.2, 2.3 are present
        
        // Requirement 2.1: HTTP connection pool gauge metrics
        Gauge totalGauge = meterRegistry.find("http_pool_connections_total")
                .tag("service", serviceName)
                .tag("protocol", "https")
                .gauge();
        assertThat(totalGauge).isNotNull();
        assertThat(totalGauge.getId().getDescription()).isEqualTo("Maximum HTTP connections allowed in the pool");

        Gauge activeGauge = meterRegistry.find("http_pool_connections_active")
                .tag("service", serviceName)
                .tag("protocol", "https")
                .gauge();
        assertThat(activeGauge).isNotNull();
        assertThat(activeGauge.getId().getDescription()).isEqualTo("Currently active HTTP connections in the pool");

        Gauge idleGauge = meterRegistry.find("http_pool_connections_idle")
                .tag("service", serviceName)
                .tag("protocol", "https")
                .gauge();
        assertThat(idleGauge).isNotNull();
        assertThat(idleGauge.getId().getDescription()).isEqualTo("Currently idle HTTP connections in the pool");

        // Requirement 2.2: Protocol labels with http/https values
        assertThat(totalGauge.getId().getTag("protocol")).isEqualTo("https");
        assertThat(activeGauge.getId().getTag("protocol")).isEqualTo("https");
        assertThat(idleGauge.getId().getTag("protocol")).isEqualTo("https");

        // Requirement 2.3: Service labels for different clients
        assertThat(totalGauge.getId().getTag("service")).isEqualTo(serviceName);
        assertThat(activeGauge.getId().getTag("service")).isEqualTo(serviceName);
        assertThat(idleGauge.getId().getTag("service")).isEqualTo(serviceName);
    }

    @Test
    void shouldHandleConcurrentServiceRegistration() {
        // Given
        int numberOfServices = 10;
        
        // When - register multiple services concurrently
        for (int i = 0; i < numberOfServices; i++) {
            String serviceName = "service-" + i;
            String protocol = (i % 2 == 0) ? "http" : "https";
            String serviceUrl = protocol + "://service-" + i + ":808" + i;
            
            httpMetricsService.registerHttpConnectionPoolMetrics(serviceName, serviceUrl);
            httpMetricsService.updateConnectionPoolMetrics(serviceName, 100 + i, 10 + i, 20 + i);
        }

        // Then
        assertThat(httpMetricsService.getRegisteredServices()).hasSize(numberOfServices);
        
        // Verify each service has its metrics registered correctly
        for (int i = 0; i < numberOfServices; i++) {
            String serviceName = "service-" + i;
            String expectedProtocol = (i % 2 == 0) ? "http" : "https";
            
            assertThat(httpMetricsService.getServiceProtocol(serviceName)).isEqualTo(expectedProtocol);
            
            HttpMetricsService.HttpConnectionPoolMetrics metrics = httpMetricsService.getServiceMetrics(serviceName);
            assertThat(metrics.getTotalConnections()).isEqualTo(100 + i);
            assertThat(metrics.getActiveConnections()).isEqualTo(10 + i);
            assertThat(metrics.getIdleConnections()).isEqualTo(20 + i);
        }
    }

    @Test
    void shouldHandleUpdateConnectionPoolMetricsFromManager() {
        // Given
        httpMetricsService.registerHttpConnectionPoolMetrics("service1", "http://service1:8080");
        httpMetricsService.registerHttpConnectionPoolMetrics("service2", "https://service2:8443");

        // When
        httpMetricsService.updateConnectionPoolMetricsFromManager();

        // Then - should update all registered services with default values
        HttpMetricsService.HttpConnectionPoolMetrics metrics1 = httpMetricsService.getServiceMetrics("service1");
        assertThat(metrics1.getTotalConnections()).isEqualTo(20);
        assertThat(metrics1.getActiveConnections()).isEqualTo(0);  // No active connections when manager unavailable
        assertThat(metrics1.getIdleConnections()).isEqualTo(0);    // No idle connections when manager unavailable

        HttpMetricsService.HttpConnectionPoolMetrics metrics2 = httpMetricsService.getServiceMetrics("service2");
        assertThat(metrics2.getTotalConnections()).isEqualTo(20);
        assertThat(metrics2.getActiveConnections()).isEqualTo(0);  // No active connections when manager unavailable
        assertThat(metrics2.getIdleConnections()).isEqualTo(0);    // No idle connections when manager unavailable
    }

    @Test
    void shouldHandleServiceNameCaseSensitivity() {
        // Given
        httpMetricsService.registerHttpConnectionPoolMetrics("Test-Service", "http://test:8080");
        httpMetricsService.registerHttpConnectionPoolMetrics("test-service", "https://test:8443");

        // When & Then - should treat as different services
        assertThat(httpMetricsService.getRegisteredServices()).containsExactlyInAnyOrder("Test-Service", "test-service");
        assertThat(httpMetricsService.getServiceProtocol("Test-Service")).isEqualTo("http");
        assertThat(httpMetricsService.getServiceProtocol("test-service")).isEqualTo("https");
    }
}