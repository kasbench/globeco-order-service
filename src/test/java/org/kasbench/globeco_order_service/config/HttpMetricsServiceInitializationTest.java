package org.kasbench.globeco_order_service.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.service.HttpMetricsService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that HttpMetricsService is properly initialized when metrics are enabled.
 */
public class HttpMetricsServiceInitializationTest {

    private MeterRegistry meterRegistry;
    private PoolingHttpClientConnectionManager connectionManager;
    private HttpMetricsService httpMetricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(10);
        
        // Create HttpMetricsService with the connection manager
        httpMetricsService = new HttpMetricsService(meterRegistry, connectionManager);
    }

    @Test
    public void testHttpMetricsServiceCanBeCreated() {
        assertNotNull(httpMetricsService, "HttpMetricsService should be created successfully");
    }

    @Test
    public void testHttpMetricsServiceCanBeCreatedWithoutConnectionManager() {
        // Test that HttpMetricsService can be created even without a connection manager
        HttpMetricsService serviceWithoutManager = new HttpMetricsService(meterRegistry, null);
        assertNotNull(serviceWithoutManager, "HttpMetricsService should be created even without connection manager");
    }

    @Test
    public void testHttpMetricsServiceCanRegisterMetrics() {
        assertNotNull(httpMetricsService, "HttpMetricsService should be available");
        
        // Test that we can register metrics without errors
        assertDoesNotThrow(() -> {
            httpMetricsService.registerHttpConnectionPoolMetrics("test-service", "http://test-service:8000");
        }, "Should be able to register HTTP metrics without errors");
        
        // Verify the service was registered
        assertTrue(httpMetricsService.getRegisteredServices().contains("test-service"), 
                  "Test service should be registered");
    }

    @Test
    public void testHttpMetricsServiceInitializesCorrectly() {
        // Test that the @PostConstruct method can be called without errors
        assertDoesNotThrow(() -> {
            httpMetricsService.initializeHttpMetrics();
        }, "HttpMetricsService should initialize without errors");
    }

    @Test
    public void testHttpMetricsServiceHandlesNullConnectionManager() {
        HttpMetricsService serviceWithoutManager = new HttpMetricsService(meterRegistry, null);
        
        // Test that it can still register metrics
        assertDoesNotThrow(() -> {
            serviceWithoutManager.registerHttpConnectionPoolMetrics("test-service", "http://test-service:8000");
        }, "Should be able to register metrics even without connection manager");
        
        // Test that it can handle metrics updates
        assertDoesNotThrow(() -> {
            serviceWithoutManager.updateConnectionPoolMetricsFromManager();
        }, "Should handle metrics updates gracefully without connection manager");
    }

    @Test
    public void testHttpMetricsServiceProtocolDetection() {
        httpMetricsService.registerHttpConnectionPoolMetrics("https-service", "https://secure-service:8443");
        httpMetricsService.registerHttpConnectionPoolMetrics("http-service", "http://plain-service:8080");
        
        assertEquals("https", httpMetricsService.getServiceProtocol("https-service"), 
                    "Should detect HTTPS protocol correctly");
        assertEquals("http", httpMetricsService.getServiceProtocol("http-service"), 
                    "Should detect HTTP protocol correctly");
    }
}