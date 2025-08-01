package org.kasbench.globeco_order_service.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interceptor for database connections that captures metrics without modifying existing repository code.
 * This component wraps the DataSource to intercept connection acquisition calls and record metrics
 * for connection acquisition duration, pool exhaustion events, and acquisition failures.
 * 
 * The interceptor uses a proxy pattern to transparently capture metrics while maintaining
 * full compatibility with existing data access code.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "metrics.database.enabled", havingValue = "true", matchIfMissing = false)
public class DatabaseConnectionInterceptor {

    private final DatabaseMetricsService databaseMetricsService;
    private DataSource originalDataSource;
    private HikariDataSource hikariDataSource;
    private HikariPoolMXBean hikariPoolMXBean;

    @Autowired
    public DatabaseConnectionInterceptor(DatabaseMetricsService databaseMetricsService) {
        this.databaseMetricsService = databaseMetricsService;
    }

    /**
     * Initializes the interceptor with the actual DataSource.
     * This method should be called after the DataSource is fully configured.
     * 
     * @param dataSource The DataSource to intercept
     */
    public void initialize(DataSource dataSource) {
        this.originalDataSource = dataSource;
        
        if (dataSource instanceof HikariDataSource) {
            this.hikariDataSource = (HikariDataSource) dataSource;
            this.hikariPoolMXBean = hikariDataSource.getHikariPoolMXBean();
            log.info("Database connection interceptor initialized with HikariDataSource");
        } else {
            log.warn("DataSource is not HikariDataSource, some metrics may not be available: {}", 
                    dataSource.getClass().getSimpleName());
        }
    }

    /**
     * Creates a proxy DataSource that intercepts connection acquisition calls.
     * The proxy transparently captures metrics while delegating all calls to the original DataSource.
     * 
     * @param originalDataSource The original DataSource to wrap
     * @return A proxy DataSource with metrics collection
     */
    public DataSource createMetricsProxy(DataSource originalDataSource) {
        this.originalDataSource = originalDataSource;
        initialize(originalDataSource);
        
        return (DataSource) Proxy.newProxyInstance(
                originalDataSource.getClass().getClassLoader(),
                new Class<?>[]{DataSource.class},
                new DataSourceInvocationHandler(originalDataSource)
        );
    }

    /**
     * InvocationHandler that intercepts DataSource method calls to capture metrics.
     */
    private class DataSourceInvocationHandler implements InvocationHandler {
        private final DataSource target;

        public DataSourceInvocationHandler(DataSource target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Intercept getConnection() calls to capture metrics
            if ("getConnection".equals(method.getName())) {
                return getConnectionWithMetrics(method, args);
            }
            
            // For all other methods, delegate directly to the target
            return method.invoke(target, args);
        }

        /**
         * Intercepts connection acquisition to capture timing and failure metrics.
         */
        private Connection getConnectionWithMetrics(Method method, Object[] args) throws Throwable {
            Timer.Sample acquisitionTimer = databaseMetricsService.startConnectionAcquisitionTimer();
            
            try {
                // Check for potential pool exhaustion before attempting connection
                checkForPoolExhaustion();
                
                // Attempt to get connection from the original DataSource
                Connection connection = (Connection) method.invoke(target, args);
                
                // Record successful acquisition duration
                databaseMetricsService.recordConnectionAcquisitionDuration(acquisitionTimer);
                
                log.debug("Database connection acquired successfully");
                return connection;
                
            } catch (Exception e) {
                // Record acquisition failure
                databaseMetricsService.recordAcquisitionFailure();
                
                // Check if this was due to pool exhaustion
                if (isPoolExhaustionException(e)) {
                    databaseMetricsService.recordPoolExhaustionEvent();
                    log.warn("Database connection acquisition failed due to pool exhaustion: {}", e.getMessage());
                } else {
                    log.warn("Database connection acquisition failed: {}", e.getMessage());
                }
                
                // Still record the duration even for failed attempts
                databaseMetricsService.recordConnectionAcquisitionDuration(acquisitionTimer);
                
                // Re-throw the original exception
                throw e;
            }
        }

        /**
         * Checks if the connection pool is approaching exhaustion and records the event.
         */
        private void checkForPoolExhaustion() {
            if (hikariPoolMXBean == null) {
                return;
            }
            
            try {
                int activeConnections = hikariPoolMXBean.getActiveConnections();
                int totalConnections = hikariPoolMXBean.getTotalConnections();
                int threadsAwaitingConnection = hikariPoolMXBean.getThreadsAwaitingConnection();
                
                // Record pool exhaustion if we're at maximum capacity with threads waiting
                if (activeConnections >= totalConnections && threadsAwaitingConnection > 0) {
                    databaseMetricsService.recordPoolExhaustionEvent();
                    log.debug("Pool exhaustion detected - Active: {}, Total: {}, Waiting: {}", 
                            activeConnections, totalConnections, threadsAwaitingConnection);
                }
                
            } catch (Exception e) {
                log.debug("Failed to check pool exhaustion status: {}", e.getMessage());
            }
        }

        /**
         * Determines if an exception was caused by connection pool exhaustion.
         */
        private boolean isPoolExhaustionException(Throwable throwable) {
            if (throwable == null) {
                return false;
            }
            
            String message = throwable.getMessage();
            if (message != null) {
                String lowerMessage = message.toLowerCase();
                return lowerMessage.contains("connection is not available") ||
                       lowerMessage.contains("timeout") ||
                       lowerMessage.contains("pool") ||
                       lowerMessage.contains("exhausted") ||
                       lowerMessage.contains("unable to obtain connection");
            }
            
            // Check the cause recursively
            return isPoolExhaustionException(throwable.getCause());
        }
    }

    /**
     * Gets the current connection pool statistics for monitoring.
     * 
     * @return Formatted string with current pool statistics
     */
    public String getCurrentPoolStatistics() {
        if (hikariPoolMXBean == null) {
            return "Pool statistics not available (not using HikariCP)";
        }
        
        try {
            return String.format(
                "Pool Statistics - Active: %d, Idle: %d, Total: %d, Waiting: %d",
                hikariPoolMXBean.getActiveConnections(),
                hikariPoolMXBean.getIdleConnections(),
                hikariPoolMXBean.getTotalConnections(),
                hikariPoolMXBean.getThreadsAwaitingConnection()
            );
        } catch (Exception e) {
            return "Failed to retrieve pool statistics: " + e.getMessage();
        }
    }

    /**
     * Checks if the interceptor is properly initialized and ready to capture metrics.
     * 
     * @return true if the interceptor is ready, false otherwise
     */
    public boolean isInitialized() {
        return originalDataSource != null && databaseMetricsService.isInitialized();
    }

    /**
     * Gets the original DataSource that is being intercepted.
     * 
     * @return The original DataSource
     */
    public DataSource getOriginalDataSource() {
        return originalDataSource;
    }

    /**
     * Validates that the interceptor is working correctly by testing basic functionality.
     * 
     * @return true if validation passes, false otherwise
     */
    public boolean validateInterceptor() {
        try {
            if (!isInitialized()) {
                log.warn("Database connection interceptor is not properly initialized");
                return false;
            }
            
            // Test that we can access pool statistics
            String stats = getCurrentPoolStatistics();
            log.debug("Interceptor validation - Pool stats: {}", stats);
            
            // Test that metrics service is working
            if (!databaseMetricsService.isInitialized()) {
                log.warn("DatabaseMetricsService is not properly initialized");
                return false;
            }
            
            log.info("Database connection interceptor validation passed");
            return true;
            
        } catch (Exception e) {
            log.error("Database connection interceptor validation failed: {}", e.getMessage(), e);
            return false;
        }
    }
}