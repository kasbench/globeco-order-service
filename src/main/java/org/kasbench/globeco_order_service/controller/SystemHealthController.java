package org.kasbench.globeco_order_service.controller;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.kasbench.globeco_order_service.service.BatchProcessingService;
import org.kasbench.globeco_order_service.service.ConnectionPoolCircuitBreaker;
import org.kasbench.globeco_order_service.service.ValidationCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Emergency system health endpoint for monitoring connection pool and system status.
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemHealthController {
    private static final Logger logger = LoggerFactory.getLogger(SystemHealthController.class);
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private BatchProcessingService batchProcessingService;
    
    @Autowired
    private ConnectionPoolCircuitBreaker circuitBreaker;
    
    @Autowired
    private ValidationCacheService validationCacheService;
    
    /**
     * Get detailed system health information including connection pool status.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Connection pool status
            Map<String, Object> connectionPool = getConnectionPoolStatus();
            health.put("connectionPool", connectionPool);
            
            // Circuit breaker status
            Map<String, Object> circuitBreakerStatus = new HashMap<>();
            circuitBreakerStatus.put("open", circuitBreaker.isCircuitOpen());
            circuitBreakerStatus.put("failureCount", circuitBreaker.getFailureCount());
            health.put("circuitBreaker", circuitBreakerStatus);
            
            // Batch processing status
            Map<String, Object> batchProcessing = new HashMap<>();
            batchProcessing.put("availablePermits", batchProcessingService.getAvailablePermits());
            batchProcessing.put("queueLength", batchProcessingService.getQueueLength());
            health.put("batchProcessing", batchProcessing);
            
            // Validation cache status
            Map<String, Object> validationCache = new HashMap<>();
            validationCache.put("ready", validationCacheService.isCacheReady());
            health.put("validationCache", validationCache);
            
            // Overall status
            boolean healthy = isSystemHealthy(connectionPool, circuitBreakerStatus);
            health.put("status", healthy ? "HEALTHY" : "UNHEALTHY");
            health.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            logger.error("Failed to get system health: {}", e.getMessage(), e);
            health.put("status", "ERROR");
            health.put("error", e.getMessage());
            return ResponseEntity.status(500).body(health);
        }
    }
    
    /**
     * Get connection pool status details.
     */
    private Map<String, Object> getConnectionPoolStatus() {
        Map<String, Object> status = new HashMap<>();
        
        if (!(dataSource instanceof HikariDataSource)) {
            status.put("type", "UNKNOWN");
            return status;
        }
        
        try {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
            
            if (poolBean == null) {
                status.put("status", "NOT_INITIALIZED");
                return status;
            }
            
            int totalConnections = poolBean.getTotalConnections();
            int activeConnections = poolBean.getActiveConnections();
            int idleConnections = poolBean.getIdleConnections();
            int threadsAwaitingConnection = poolBean.getThreadsAwaitingConnection();
            
            double utilization = totalConnections > 0 ? (double) activeConnections / totalConnections : 0;
            
            status.put("type", "HIKARI");
            status.put("totalConnections", totalConnections);
            status.put("activeConnections", activeConnections);
            status.put("idleConnections", idleConnections);
            status.put("threadsWaiting", threadsAwaitingConnection);
            status.put("utilization", Math.round(utilization * 1000.0) / 10.0); // Round to 1 decimal
            status.put("poolName", hikariDataSource.getPoolName());
            
            // Determine status
            if (threadsAwaitingConnection > 0) {
                status.put("status", "CRITICAL");
            } else if (utilization >= 0.9) {
                status.put("status", "HIGH_LOAD");
            } else if (utilization >= 0.7) {
                status.put("status", "MODERATE_LOAD");
            } else {
                status.put("status", "HEALTHY");
            }
            
        } catch (Exception e) {
            status.put("status", "ERROR");
            status.put("error", e.getMessage());
        }
        
        return status;
    }
    
    /**
     * Determine if system is overall healthy.
     */
    private boolean isSystemHealthy(Map<String, Object> connectionPool, Map<String, Object> circuitBreaker) {
        String poolStatus = (String) connectionPool.get("status");
        boolean circuitOpen = (Boolean) circuitBreaker.get("open");
        
        return !circuitOpen && 
               !"CRITICAL".equals(poolStatus) && 
               !"ERROR".equals(poolStatus);
    }
}