package org.kasbench.globeco_order_service.controller;

import lombok.extern.slf4j.Slf4j;
import org.kasbench.globeco_order_service.service.SystemOverloadDetector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Test controller for verifying SystemOverloadDetector functionality.
 * This controller is only enabled when error handling is enabled.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test")
@ConditionalOnProperty(name = "error.handling.enabled", havingValue = "true", matchIfMissing = false)
public class SystemOverloadTestController {

    private final SystemOverloadDetector systemOverloadDetector;

    @Autowired
    public SystemOverloadTestController(@Autowired(required = false) SystemOverloadDetector systemOverloadDetector) {
        this.systemOverloadDetector = systemOverloadDetector;
        log.info("SystemOverloadTestController initialized with detector: {}", 
                systemOverloadDetector != null ? "available" : "not available");
    }

    /**
     * Test endpoint to check system overload status.
     * 
     * @return system overload status and metrics
     */
    @GetMapping("/system-overload-status")
    public ResponseEntity<Map<String, Object>> getSystemOverloadStatus() {
        Map<String, Object> response = new HashMap<>();
        
        if (systemOverloadDetector == null) {
            response.put("error", "SystemOverloadDetector not available");
            response.put("initialized", false);
            return ResponseEntity.ok(response);
        }
        
        try {
            // Check if detector is initialized
            boolean initialized = systemOverloadDetector.isInitialized();
            response.put("initialized", initialized);
            
            if (!initialized) {
                response.put("error", "SystemOverloadDetector not properly initialized");
                return ResponseEntity.ok(response);
            }
            
            // Get system overload status
            boolean isOverloaded = systemOverloadDetector.isSystemOverloaded();
            response.put("isOverloaded", isOverloaded);
            
            // Get individual resource utilizations
            response.put("threadPoolUtilization", systemOverloadDetector.getThreadPoolUtilization());
            response.put("databaseUtilization", systemOverloadDetector.getDatabaseConnectionUtilization());
            response.put("memoryUtilization", systemOverloadDetector.getMemoryUtilization());
            
            // Get individual overload checks
            response.put("threadPoolOverloaded", systemOverloadDetector.isThreadPoolOverloaded());
            response.put("databaseOverloaded", systemOverloadDetector.isDatabaseConnectionPoolOverloaded());
            response.put("memoryOverloaded", systemOverloadDetector.isMemoryOverloaded());
            
            // Get retry delay if overloaded
            if (isOverloaded) {
                response.put("retryDelaySeconds", systemOverloadDetector.calculateRetryDelay());
            }
            
            // Get detailed system status
            response.put("systemStatus", systemOverloadDetector.getSystemStatus());
            
            log.debug("System overload status check completed: overloaded={}", isOverloaded);
            
        } catch (Exception e) {
            log.error("Error checking system overload status: {}", e.getMessage(), e);
            response.put("error", "Error checking system overload status: " + e.getMessage());
            response.put("exception", e.getClass().getSimpleName());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Test endpoint to check individual resource utilizations.
     * 
     * @return individual resource utilization metrics
     */
    @GetMapping("/resource-utilization")
    public ResponseEntity<Map<String, Object>> getResourceUtilization() {
        Map<String, Object> response = new HashMap<>();
        
        if (systemOverloadDetector == null) {
            response.put("error", "SystemOverloadDetector not available");
            return ResponseEntity.ok(response);
        }
        
        try {
            // Thread pool metrics
            Map<String, Object> threadPool = new HashMap<>();
            threadPool.put("utilization", systemOverloadDetector.getThreadPoolUtilization());
            threadPool.put("overloaded", systemOverloadDetector.isThreadPoolOverloaded());
            response.put("threadPool", threadPool);
            
            // Database pool metrics
            Map<String, Object> database = new HashMap<>();
            database.put("utilization", systemOverloadDetector.getDatabaseConnectionUtilization());
            database.put("overloaded", systemOverloadDetector.isDatabaseConnectionPoolOverloaded());
            response.put("database", database);
            
            // Memory metrics
            Map<String, Object> memory = new HashMap<>();
            memory.put("utilization", systemOverloadDetector.getMemoryUtilization());
            memory.put("overloaded", systemOverloadDetector.isMemoryOverloaded());
            response.put("memory", memory);
            
            // Overall status
            response.put("overallOverloaded", systemOverloadDetector.isSystemOverloaded());
            response.put("retryDelay", systemOverloadDetector.calculateRetryDelay());
            
        } catch (Exception e) {
            log.error("Error getting resource utilization: {}", e.getMessage(), e);
            response.put("error", "Error getting resource utilization: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
}