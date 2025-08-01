package org.kasbench.globeco_order_service.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

/**
 * Service for collecting and exposing database connection pool metrics from HikariCP.
 * Provides comprehensive monitoring of database connection pool performance including
 * connection acquisition timing, pool exhaustion events, and current pool state.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "metrics.database.enabled", havingValue = "true", matchIfMissing = false)
public class DatabaseMetricsService {

    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;
    
    // Metrics instances
    private Timer connectionAcquisitionTimer;
    private Counter poolExhaustionCounter;
    private Counter acquisitionFailureCounter;
    
    // HikariCP specific components
    private HikariDataSource hikariDataSource;
    private HikariPoolMXBean hikariPoolMXBean;

    @Autowired
    public DatabaseMetricsService(MeterRegistry meterRegistry, DataSource dataSource) {
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
    }

    /**
     * Initialize database metrics on application startup.
     * Registers all custom metrics with the MeterRegistry and sets up HikariCP integration.
     */
    @PostConstruct
    public void initializeDatabaseMetrics() {
        try {
            setupHikariIntegration();
            registerDatabaseMetrics();
            log.info("Database metrics successfully registered");
        } catch (Exception e) {
            log.warn("Failed to register database metrics: {}", e.getMessage(), e);
            // Continue application startup even if metrics registration fails
        }
    }

    /**
     * Sets up integration with HikariCP for accessing pool statistics.
     */
    private void setupHikariIntegration() {
        if (dataSource instanceof HikariDataSource) {
            this.hikariDataSource = (HikariDataSource) dataSource;
            this.hikariPoolMXBean = hikariDataSource.getHikariPoolMXBean();
            log.debug("HikariCP integration established successfully");
        } else {
            throw new IllegalStateException("DataSource is not a HikariDataSource. Current type: " + 
                dataSource.getClass().getSimpleName());
        }
    }

    /**
     * Registers all database-related metrics with the MeterRegistry.
     */
    private void registerDatabaseMetrics() {
        registerConnectionAcquisitionTimer();
        registerPoolExhaustionCounter();
        registerAcquisitionFailureCounter();
        registerConnectionPoolGauges();
    }

    /**
     * Registers the connection acquisition duration histogram.
     */
    private void registerConnectionAcquisitionTimer() {
        this.connectionAcquisitionTimer = Timer.builder("db_connection_acquisition_duration_seconds")
            .description("Time taken to acquire a database connection")
            .register(meterRegistry);
    }

    /**
     * Registers the pool exhaustion events counter.
     */
    private void registerPoolExhaustionCounter() {
        this.poolExhaustionCounter = Counter.builder("db_pool_exhaustion_events_total")
            .description("Number of times the database pool reached capacity")
            .register(meterRegistry);
    }

    /**
     * Registers the connection acquisition failures counter.
     */
    private void registerAcquisitionFailureCounter() {
        this.acquisitionFailureCounter = Counter.builder("db_connection_acquisition_failures_total")
            .description("Failed attempts to acquire a database connection")
            .register(meterRegistry);
    }

    /**
     * Registers gauge metrics for current database pool state.
     */
    private void registerConnectionPoolGauges() {
        // Total connections gauge
        Gauge.builder("db_pool_connections_total", this, DatabaseMetricsService::getTotalConnections)
            .description("Maximum connections the pool can handle")
            .register(meterRegistry);

        // Active connections gauge
        Gauge.builder("db_pool_connections_active", this, DatabaseMetricsService::getActiveConnections)
            .description("Currently active database connections")
            .register(meterRegistry);

        // Idle connections gauge
        Gauge.builder("db_pool_connections_idle", this, DatabaseMetricsService::getIdleConnections)
            .description("Currently idle database connections")
            .register(meterRegistry);
    }

    /**
     * Records the duration of a database connection acquisition.
     * 
     * @param duration the acquisition duration
     * @param timeUnit the time unit of the duration
     */
    public void recordConnectionAcquisitionDuration(long duration, TimeUnit timeUnit) {
        if (connectionAcquisitionTimer != null) {
            connectionAcquisitionTimer.record(duration, timeUnit);
        }
    }

    /**
     * Records a database connection acquisition duration using a Timer.Sample.
     * 
     * @param sample the timer sample to stop and record
     */
    public void recordConnectionAcquisitionDuration(Timer.Sample sample) {
        if (connectionAcquisitionTimer != null && sample != null) {
            sample.stop(connectionAcquisitionTimer);
        }
    }

    /**
     * Increments the pool exhaustion counter when the pool reaches maximum capacity.
     */
    public void recordPoolExhaustionEvent() {
        if (poolExhaustionCounter != null) {
            poolExhaustionCounter.increment();
            log.debug("Database pool exhaustion event recorded");
        }
    }

    /**
     * Increments the acquisition failure counter when connection acquisition fails.
     */
    public void recordAcquisitionFailure() {
        if (acquisitionFailureCounter != null) {
            acquisitionFailureCounter.increment();
            log.debug("Database connection acquisition failure recorded");
        }
    }

    /**
     * Creates a Timer.Sample for measuring connection acquisition duration.
     * 
     * @return a Timer.Sample that can be stopped to record the duration
     */
    public Timer.Sample startConnectionAcquisitionTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Gets the total number of connections the pool can handle.
     * 
     * @return the maximum pool size
     */
    private double getTotalConnections() {
        try {
            return hikariDataSource != null ? hikariDataSource.getMaximumPoolSize() : 0;
        } catch (Exception e) {
            log.debug("Failed to get total connections: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Gets the number of currently active database connections.
     * 
     * @return the number of active connections
     */
    private double getActiveConnections() {
        try {
            return hikariPoolMXBean != null ? hikariPoolMXBean.getActiveConnections() : 0;
        } catch (Exception e) {
            log.debug("Failed to get active connections: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Gets the number of currently idle database connections.
     * 
     * @return the number of idle connections
     */
    private double getIdleConnections() {
        try {
            return hikariPoolMXBean != null ? hikariPoolMXBean.getIdleConnections() : 0;
        } catch (Exception e) {
            log.debug("Failed to get idle connections: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Checks if the database metrics service is properly initialized.
     * 
     * @return true if metrics are initialized and ready to use
     */
    public boolean isInitialized() {
        return hikariDataSource != null && 
               hikariPoolMXBean != null && 
               connectionAcquisitionTimer != null &&
               poolExhaustionCounter != null &&
               acquisitionFailureCounter != null;
    }

    /**
     * Gets the current pool statistics for monitoring purposes.
     * 
     * @return a formatted string with current pool statistics
     */
    public String getPoolStatistics() {
        if (!isInitialized()) {
            return "Database metrics not initialized";
        }
        
        try {
            return String.format(
                "Pool Statistics - Total: %d, Active: %d, Idle: %d, Waiting: %d",
                hikariDataSource.getMaximumPoolSize(),
                hikariPoolMXBean.getActiveConnections(),
                hikariPoolMXBean.getIdleConnections(),
                hikariPoolMXBean.getThreadsAwaitingConnection()
            );
        } catch (Exception e) {
            return "Failed to retrieve pool statistics: " + e.getMessage();
        }
    }
}