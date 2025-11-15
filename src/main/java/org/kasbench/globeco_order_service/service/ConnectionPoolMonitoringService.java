package org.kasbench.globeco_order_service.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.kasbench.globeco_order_service.model.ConnectionPoolHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class ConnectionPoolMonitoringService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolMonitoringService.class);
    private static final double HIGH_UTILIZATION_THRESHOLD = 0.75;
    
    @Autowired
    private HikariDataSource hikariDataSource;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    private Counter highUtilizationCounter;
    
    @PostConstruct
    public void init() {
        // Initialize counter for high utilization events
        highUtilizationCounter = Counter.builder("db.pool.high_utilization")
                .description("Count of times connection pool utilization exceeded 75%")
                .register(meterRegistry);
        
        // Register gauges for real-time monitoring
        registerGauges();
    }
    
    private void registerGauges() {
        meterRegistry.gauge("db.pool.active", hikariDataSource, ds -> {
            HikariPoolMXBean poolBean = ds.getHikariPoolMXBean();
            return poolBean != null ? poolBean.getActiveConnections() : 0;
        });
        
        meterRegistry.gauge("db.pool.idle", hikariDataSource, ds -> {
            HikariPoolMXBean poolBean = ds.getHikariPoolMXBean();
            return poolBean != null ? poolBean.getIdleConnections() : 0;
        });
        
        meterRegistry.gauge("db.pool.waiting", hikariDataSource, ds -> {
            HikariPoolMXBean poolBean = ds.getHikariPoolMXBean();
            return poolBean != null ? poolBean.getThreadsAwaitingConnection() : 0;
        });
        
        meterRegistry.gauge("db.pool.total", hikariDataSource, ds -> {
            HikariPoolMXBean poolBean = ds.getHikariPoolMXBean();
            return poolBean != null ? poolBean.getTotalConnections() : 0;
        });
        
        meterRegistry.gauge("db.pool.utilization", hikariDataSource, ds -> {
            HikariPoolMXBean poolBean = ds.getHikariPoolMXBean();
            if (poolBean == null) {
                return 0.0;
            }
            int total = poolBean.getTotalConnections();
            if (total == 0) {
                return 0.0;
            }
            return (double) poolBean.getActiveConnections() / total;
        });
    }
    
    @Scheduled(fixedRate = 5000) // Every 5 seconds
    public void recordConnectionPoolMetrics() {
        try {
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
            
            if (poolBean == null) {
                logger.warn("HikariPoolMXBean is not available");
                return;
            }
            
            int active = poolBean.getActiveConnections();
            int idle = poolBean.getIdleConnections();
            int waiting = poolBean.getThreadsAwaitingConnection();
            int total = poolBean.getTotalConnections();
            double utilization = total > 0 ? (double) active / total : 0.0;
            
            // Log metrics at debug level
            if (logger.isDebugEnabled()) {
                logger.debug("Connection pool metrics - Active: {}, Idle: {}, Waiting: {}, Total: {}, Utilization: {:.2f}%",
                        active, idle, waiting, total, utilization * 100);
            }
            
            // Alert on high utilization
            if (utilization > HIGH_UTILIZATION_THRESHOLD) {
                highUtilizationCounter.increment();
                logger.warn("High connection pool utilization: {:.2f}% ({}/{}), Threads waiting: {}",
                        utilization * 100, active, total, waiting);
            }
            
        } catch (Exception e) {
            logger.error("Error recording connection pool metrics", e);
        }
    }
    
    public ConnectionPoolHealth getHealth() {
        try {
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
            
            if (poolBean == null) {
                return ConnectionPoolHealth.builder()
                        .active(0)
                        .idle(0)
                        .waiting(0)
                        .total(0)
                        .utilization(0.0)
                        .timestamp(System.currentTimeMillis())
                        .build();
            }
            
            int active = poolBean.getActiveConnections();
            int idle = poolBean.getIdleConnections();
            int waiting = poolBean.getThreadsAwaitingConnection();
            int total = poolBean.getTotalConnections();
            
            return ConnectionPoolHealth.fromMetrics(active, idle, waiting, total);
            
        } catch (Exception e) {
            logger.error("Error getting connection pool health", e);
            return ConnectionPoolHealth.builder()
                    .active(0)
                    .idle(0)
                    .waiting(0)
                    .total(0)
                    .utilization(0.0)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }
    }
}
