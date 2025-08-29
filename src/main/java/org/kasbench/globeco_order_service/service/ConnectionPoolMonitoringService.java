package org.kasbench.globeco_order_service.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/**
 * Service to monitor HikariCP connection pool health and log warnings
 * when connection pool utilization is high.
 */
@Service
public class ConnectionPoolMonitoringService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolMonitoringService.class);
    private static final double HIGH_UTILIZATION_THRESHOLD = 0.8; // 80%
    private static final double CRITICAL_UTILIZATION_THRESHOLD = 0.95; // 95%
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * Monitor connection pool every 30 seconds and log warnings if utilization is high.
     */
    @Scheduled(fixedRate = 30000)
    public void monitorConnectionPool() {
        if (!(dataSource instanceof HikariDataSource)) {
            return; // Only works with HikariCP
        }
        
        try {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
            
            if (poolBean == null) {
                return; // Pool not initialized yet
            }
            
            int totalConnections = poolBean.getTotalConnections();
            int activeConnections = poolBean.getActiveConnections();
            int idleConnections = poolBean.getIdleConnections();
            int threadsAwaitingConnection = poolBean.getThreadsAwaitingConnection();
            
            double utilization = totalConnections > 0 ? (double) activeConnections / totalConnections : 0;
            
            // Log critical situations
            if (utilization >= CRITICAL_UTILIZATION_THRESHOLD || threadsAwaitingConnection > 0) {
                logger.error("CRITICAL: Connection pool utilization at {:.1f}% ({}/{} active), {} threads waiting", 
                        utilization * 100, activeConnections, totalConnections, threadsAwaitingConnection);
            } else if (utilization >= HIGH_UTILIZATION_THRESHOLD) {
                logger.warn("HIGH: Connection pool utilization at {:.1f}% ({}/{} active, {} idle)", 
                        utilization * 100, activeConnections, totalConnections, idleConnections);
            } else {
                logger.debug("Connection pool status: {:.1f}% utilization ({}/{} active, {} idle)", 
                        utilization * 100, activeConnections, totalConnections, idleConnections);
            }
            
        } catch (Exception e) {
            logger.debug("Failed to monitor connection pool: {}", e.getMessage());
        }
    }
}