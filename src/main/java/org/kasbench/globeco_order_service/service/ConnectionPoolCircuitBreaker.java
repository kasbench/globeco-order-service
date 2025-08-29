package org.kasbench.globeco_order_service.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit breaker to prevent system overload when connection pool is exhausted.
 */
@Service
public class ConnectionPoolCircuitBreaker {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolCircuitBreaker.class);
    
    private static final double CRITICAL_UTILIZATION_THRESHOLD = 0.9; // 90%
    private static final int FAILURE_THRESHOLD = 5;
    private static final long RECOVERY_TIME_MS = 30000; // 30 seconds
    
    @Autowired
    private DataSource dataSource;
    
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private volatile boolean circuitOpen = false;
    
    /**
     * Check if operations should be allowed based on connection pool health.
     */
    public boolean allowOperation() {
        // Check if circuit should be closed (recovered)
        if (circuitOpen && System.currentTimeMillis() - lastFailureTime.get() > RECOVERY_TIME_MS) {
            logger.info("Circuit breaker attempting recovery...");
            if (isConnectionPoolHealthy()) {
                circuitOpen = false;
                failureCount.set(0);
                logger.info("Circuit breaker closed - system recovered");
            }
        }
        
        if (circuitOpen) {
            logger.warn("Circuit breaker OPEN - rejecting operation to protect system");
            return false;
        }
        
        // Check current pool health
        if (!isConnectionPoolHealthy()) {
            recordFailure();
            return false;
        }
        
        return true;
    }
    
    /**
     * Record a failure and potentially open the circuit.
     */
    public void recordFailure() {
        int failures = failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        if (failures >= FAILURE_THRESHOLD && !circuitOpen) {
            circuitOpen = true;
            logger.error("Circuit breaker OPENED after {} failures - system protection activated", failures);
        }
    }
    
    /**
     * Record a success and potentially reset failure count.
     */
    public void recordSuccess() {
        if (failureCount.get() > 0) {
            failureCount.decrementAndGet();
        }
    }
    
    /**
     * Check if connection pool is healthy.
     */
    private boolean isConnectionPoolHealthy() {
        if (!(dataSource instanceof HikariDataSource)) {
            return true; // Assume healthy if not HikariCP
        }
        
        try {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
            
            if (poolBean == null) {
                return true; // Pool not initialized yet
            }
            
            int totalConnections = poolBean.getTotalConnections();
            int activeConnections = poolBean.getActiveConnections();
            int threadsAwaitingConnection = poolBean.getThreadsAwaitingConnection();
            
            double utilization = totalConnections > 0 ? (double) activeConnections / totalConnections : 0;
            
            // Consider unhealthy if utilization is too high or threads are waiting
            boolean healthy = utilization < CRITICAL_UTILIZATION_THRESHOLD && threadsAwaitingConnection == 0;
            
            if (!healthy) {
                logger.warn("Connection pool unhealthy: {:.1f}% utilization, {} threads waiting", 
                        utilization * 100, threadsAwaitingConnection);
            }
            
            return healthy;
            
        } catch (Exception e) {
            logger.debug("Failed to check connection pool health: {}", e.getMessage());
            return true; // Assume healthy if can't check
        }
    }
    
    /**
     * Get current circuit breaker status.
     */
    public boolean isCircuitOpen() {
        return circuitOpen;
    }
    
    /**
     * Get current failure count.
     */
    public int getFailureCount() {
        return failureCount.get();
    }
}