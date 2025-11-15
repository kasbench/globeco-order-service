package org.kasbench.globeco_order_service.controller;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.kasbench.globeco_order_service.model.ConnectionPoolHealth;
import org.kasbench.globeco_order_service.service.BatchProcessingService;
import org.kasbench.globeco_order_service.service.ConnectionPoolCircuitBreaker;
import org.kasbench.globeco_order_service.service.ConnectionPoolMonitoringService;
import org.kasbench.globeco_order_service.service.ValidationCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    
    @Autowired
    private ConnectionPoolMonitoringService connectionPoolMonitoringService;
    
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
    
    /**
     * Get connection pool health status with recommendations.
     * Endpoint: GET /api/v1/health/connection-pool
     */
    @GetMapping("/health/connection-pool")
    public ResponseEntity<Map<String, Object>> getConnectionPoolHealth() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get connection pool health from monitoring service
            ConnectionPoolHealth health = connectionPoolMonitoringService.getHealth();
            
            // Build response with health metrics
            response.put("active", health.getActive());
            response.put("idle", health.getIdle());
            response.put("waiting", health.getWaiting());
            response.put("total", health.getTotal());
            response.put("utilization", Math.round(health.getUtilization() * 1000.0) / 10.0); // Round to 1 decimal
            response.put("status", health.getStatus());
            response.put("healthy", health.isHealthy());
            response.put("timestamp", health.getTimestamp());
            
            // Add recommendations based on current state
            List<String> recommendations = generateRecommendations(health);
            response.put("recommendations", recommendations);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to get connection pool health: {}", e.getMessage(), e);
            response.put("status", "ERROR");
            response.put("error", e.getMessage());
            response.put("recommendations", List.of("Unable to retrieve connection pool metrics. Check system logs."));
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Generate recommendations based on connection pool health state.
     */
    private List<String> generateRecommendations(ConnectionPoolHealth health) {
        List<String> recommendations = new ArrayList<>();
        
        String status = health.getStatus();
        int waiting = health.getWaiting();
        double utilization = health.getUtilization();
        int idle = health.getIdle();
        
        switch (status) {
            case "CRITICAL":
                recommendations.add("CRITICAL: Connection pool is at or near capacity.");
                if (waiting > 0) {
                    recommendations.add("Action required: " + waiting + " thread(s) are waiting for connections.");
                }
                recommendations.add("Immediate actions: Review active queries, check for connection leaks, consider scaling.");
                recommendations.add("Check for long-running transactions or external service delays.");
                break;
                
            case "WARNING":
                recommendations.add("WARNING: Connection pool utilization is high (" + 
                                  Math.round(utilization * 100) + "%).");
                recommendations.add("Monitor closely: System is approaching capacity limits.");
                if (waiting > 0) {
                    recommendations.add("Some threads are waiting for connections (" + waiting + ").");
                }
                recommendations.add("Consider: Review batch sizes, optimize query performance, or increase pool size.");
                break;
                
            case "HEALTHY":
                recommendations.add("Connection pool is operating normally.");
                if (utilization > 0.5) {
                    recommendations.add("Utilization is moderate (" + Math.round(utilization * 100) + 
                                      "%). Continue monitoring during peak hours.");
                }
                if (idle < 5 && health.getTotal() > 10) {
                    recommendations.add("Low idle connections (" + idle + "). Consider increasing minimum-idle setting.");
                }
                break;
                
            default:
                recommendations.add("Connection pool status is unknown. Check configuration.");
        }
        
        // Additional context-specific recommendations
        if (health.getTotal() == 0) {
            recommendations.add("WARNING: No connections in pool. Check database connectivity.");
        }
        
        if (utilization < 0.3 && health.getTotal() > 40) {
            recommendations.add("INFO: Low utilization (" + Math.round(utilization * 100) + 
                              "%). Pool size may be larger than needed.");
        }
        
        return recommendations;
    }
}