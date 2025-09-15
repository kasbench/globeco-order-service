package org.kasbench.globeco_order_service.controller;

import org.kasbench.globeco_order_service.service.HttpMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test controller to verify metrics services are working properly.
 * This controller provides endpoints to test and debug metrics functionality.
 */
@RestController
@RequestMapping("/api/test/metrics")
@ConditionalOnProperty(name = "metrics.custom.enabled", havingValue = "true", matchIfMissing = false)
public class MetricsTestController {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsTestController.class);
    
    private final HttpMetricsService httpMetricsService;
    
    @Autowired
    public MetricsTestController(@Autowired(required = false) HttpMetricsService httpMetricsService) {
        logger.debug("=== MetricsTestController constructor called ===");
        logger.info("HttpMetricsService available: {}", httpMetricsService != null);
        this.httpMetricsService = httpMetricsService;
        logger.debug("=== MetricsTestController constructor completed ===");
    }
    
    /**
     * Test endpoint to check if HttpMetricsService is available and working.
     */
    @GetMapping("/http-status")
    public ResponseEntity<String> getHttpMetricsStatus() {
        logger.debug("=== HTTP metrics status endpoint called ===");
        
        if (httpMetricsService == null) {
            logger.error("HttpMetricsService is NULL in test controller");
            return ResponseEntity.ok("HttpMetricsService is not available");
        }
        
        try {
            // Force a metrics update
            httpMetricsService.forceMetricsUpdate();
            
            // Get connection pool status
            String status = httpMetricsService.getConnectionPoolStatus();
            
            // Get registered services
            int registeredServices = httpMetricsService.getRegisteredServices().size();
            
            String response = String.format(
                "HttpMetricsService is available and working!\n" +
                "Registered services: %d\n" +
                "Connection pool status:\n%s",
                registeredServices, status
            );
            
            logger.info("HTTP metrics status response: {}", response);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting HTTP metrics status: {}", e.getMessage(), e);
            return ResponseEntity.ok("Error: " + e.getMessage());
        }
    }
    
    /**
     * Test endpoint to manually register HTTP metrics for testing.
     */
    @GetMapping("/register-test-service")
    public ResponseEntity<String> registerTestService() {
        logger.debug("=== Register test service endpoint called ===");
        
        if (httpMetricsService == null) {
            logger.error("HttpMetricsService is NULL in test controller");
            return ResponseEntity.ok("HttpMetricsService is not available");
        }
        
        try {
            // Register a test service
            httpMetricsService.registerHttpConnectionPoolMetrics("test-service", "http://test-service:8080");
            
            // Force an update
            httpMetricsService.forceMetricsUpdate();
            
            String response = "Test service registered successfully!\n" +
                            "Registered services: " + httpMetricsService.getRegisteredServices().size();
            
            logger.info("Test service registration response: {}", response);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error registering test service: {}", e.getMessage(), e);
            return ResponseEntity.ok("Error: " + e.getMessage());
        }
    }
}