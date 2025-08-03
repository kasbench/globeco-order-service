package org.kasbench.globeco_order_service.integration;

import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.service.HttpMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify that HttpMetricsService initializes correctly with real configuration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "metrics.custom.enabled=true",
    "metrics.custom.http.enabled=true",
    "metrics.custom.database.enabled=true",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "logging.level.org.kasbench.globeco_order_service.service.HttpMetricsService=INFO"
})
public class HttpMetricsServiceRealInitializationTest {

    @Autowired
    private HttpMetricsService httpMetricsService;

    @Test
    public void testHttpMetricsServiceInitializes() {
        assertNotNull(httpMetricsService, "HttpMetricsService should be initialized");
        
        // Test that the service can register metrics
        httpMetricsService.registerHttpConnectionPoolMetrics("test-service", "http://test:8000");
        
        assertTrue(httpMetricsService.getRegisteredServices().contains("test-service"),
                  "Service should be registered");
        
        // Test that metrics update works
        assertDoesNotThrow(() -> {
            httpMetricsService.updateConnectionPoolMetricsFromManager();
        }, "Should be able to update metrics without errors");
        
        // Test connection pool status
        String status = httpMetricsService.getConnectionPoolStatus();
        assertNotNull(status, "Connection pool status should not be null");
        assertTrue(status.contains("HTTP Connection Pool Status"), 
                  "Status should contain connection pool information");
    }

    @Test
    public void testHttpMetricsServiceCanForceUpdate() {
        assertNotNull(httpMetricsService, "HttpMetricsService should be initialized");
        
        // Test force update functionality
        assertDoesNotThrow(() -> {
            httpMetricsService.forceMetricsUpdate();
        }, "Should be able to force metrics update without errors");
    }
}