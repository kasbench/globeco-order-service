package org.kasbench.globeco_order_service.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.kasbench.globeco_order_service.service.DatabaseMetricsService;
import org.kasbench.globeco_order_service.service.DatabaseConnectionInterceptor;
import org.kasbench.globeco_order_service.service.HttpMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Central configuration class for custom OpenTelemetry metrics registration and initialization.
 * This class coordinates the automatic registration of all custom metrics on application startup
 * and provides centralized error handling for metric registration failures.
 * 
 * The configuration ensures that metrics use the existing MeterRegistry configuration
 * and integrate seamlessly with the current OpenTelemetry infrastructure.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "metrics.custom.enabled", havingValue = "true", matchIfMissing = false)
public class MetricsConfiguration {

    private final MeterRegistry meterRegistry;
    private final DatabaseMetricsService databaseMetricsService;
    private final DatabaseConnectionInterceptor databaseConnectionInterceptor;
    private final HttpMetricsService httpMetricsService;
    private final DataSource dataSource;
    
    // Configuration properties
    @Value("${metrics.database.enabled:true}")
    private boolean databaseMetricsEnabled;
    
    @Value("${metrics.http.enabled:true}")
    private boolean httpMetricsEnabled;
    
    @Value("${external.services.security.url:}")
    private String securityServiceUrl;
    
    @Value("${external.services.portfolio.url:}")
    private String portfolioServiceUrl;
    
    @Value("${metrics.initialization.timeout:30}")
    private int initializationTimeoutSeconds;

    @Autowired
    public MetricsConfiguration(MeterRegistry meterRegistry,
                               DatabaseMetricsService databaseMetricsService,
                               DatabaseConnectionInterceptor databaseConnectionInterceptor,
                               HttpMetricsService httpMetricsService,
                               DataSource dataSource) {
        this.meterRegistry = meterRegistry;
        this.databaseMetricsService = databaseMetricsService;
        this.databaseConnectionInterceptor = databaseConnectionInterceptor;
        this.httpMetricsService = httpMetricsService;
        this.dataSource = dataSource;
    }

    /**
     * Initializes custom metrics configuration on application startup.
     * This method is called after dependency injection is complete but before
     * the application context is fully refreshed.
     */
    @PostConstruct
    public void initializeMetricsConfiguration() {
        log.info("Starting custom metrics configuration initialization");
        
        try {
            validateMeterRegistry();
            logMetricsConfiguration();
            log.info("Custom metrics configuration initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize custom metrics configuration: {}", e.getMessage(), e);
            // Don't fail application startup for metrics configuration issues
        }
    }

    /**
     * Event listener that triggers metric registration after the application context is fully refreshed.
     * This ensures all beans are properly initialized before attempting metric registration.
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationContextRefreshed(ContextRefreshedEvent event) {
        log.info("Application context refreshed, starting custom metrics registration");
        
        // Use async initialization to avoid blocking application startup
        CompletableFuture.runAsync(this::initializeAllMetrics)
            .orTimeout(initializationTimeoutSeconds, TimeUnit.SECONDS)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Custom metrics initialization timed out or failed: {}", 
                             throwable.getMessage(), throwable);
                } else {
                    log.info("Custom metrics initialization completed successfully");
                }
            });
    }

    /**
     * Initializes all custom metrics in the correct order with proper error handling.
     */
    private void initializeAllMetrics() {
        try {
            initializeDatabaseMetrics();
            initializeHttpMetrics();
            validateMetricsRegistration();
            log.info("All custom metrics have been successfully initialized and registered");
        } catch (Exception e) {
            log.error("Failed to initialize custom metrics: {}", e.getMessage(), e);
            // Continue application startup even if metrics initialization fails
        }
    }

    /**
     * Initializes database metrics if enabled.
     */
    private void initializeDatabaseMetrics() {
        if (!databaseMetricsEnabled) {
            log.info("Database metrics are disabled, skipping initialization");
            return;
        }

        try {
            log.debug("Initializing database metrics...");
            
            // Initialize the connection interceptor
            databaseConnectionInterceptor.initialize(dataSource);
            
            // Validate that the interceptor is working
            if (databaseConnectionInterceptor.validateInterceptor()) {
                log.debug("Database connection interceptor validated successfully");
            } else {
                log.warn("Database connection interceptor validation failed");
            }
            
            // The DatabaseMetricsService already has @PostConstruct initialization
            // We just need to verify it's properly initialized
            if (databaseMetricsService.isInitialized()) {
                log.info("Database metrics successfully initialized and registered");
                
                // Log current pool statistics for verification
                String poolStats = databaseMetricsService.getPoolStatistics();
                log.info("Current database pool status: {}", poolStats);
            } else {
                log.warn("Database metrics service is not properly initialized");
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize database metrics: {}", e.getMessage(), e);
            // Don't propagate the exception to avoid failing application startup
        }
    }

    /**
     * Initializes HTTP metrics if enabled.
     */
    private void initializeHttpMetrics() {
        if (!httpMetricsEnabled) {
            log.info("HTTP metrics are disabled, skipping initialization");
            return;
        }

        try {
            log.debug("Initializing HTTP metrics...");
            
            // Register HTTP metrics for external services
            registerHttpMetricsForServices();
            
            log.info("HTTP metrics successfully initialized and registered");
            
        } catch (Exception e) {
            log.error("Failed to initialize HTTP metrics: {}", e.getMessage(), e);
            // Don't propagate the exception to avoid failing application startup
        }
    }

    /**
     * Registers HTTP connection pool metrics for external services.
     */
    private void registerHttpMetricsForServices() {
        // Register metrics for Security Service
        if (securityServiceUrl != null && !securityServiceUrl.trim().isEmpty()) {
            try {
                httpMetricsService.registerHttpConnectionPoolMetrics("security-service", securityServiceUrl);
                log.debug("Registered HTTP metrics for Security Service: {}", securityServiceUrl);
            } catch (Exception e) {
                log.warn("Failed to register HTTP metrics for Security Service: {}", e.getMessage());
            }
        } else {
            log.warn("Security Service URL not configured, skipping HTTP metrics registration");
        }

        // Register metrics for Portfolio Service
        if (portfolioServiceUrl != null && !portfolioServiceUrl.trim().isEmpty()) {
            try {
                httpMetricsService.registerHttpConnectionPoolMetrics("portfolio-service", portfolioServiceUrl);
                log.debug("Registered HTTP metrics for Portfolio Service: {}", portfolioServiceUrl);
            } catch (Exception e) {
                log.warn("Failed to register HTTP metrics for Portfolio Service: {}", e.getMessage());
            }
        } else {
            log.warn("Portfolio Service URL not configured, skipping HTTP metrics registration");
        }
    }

    /**
     * Validates that the MeterRegistry is properly configured and available.
     */
    private void validateMeterRegistry() {
        if (meterRegistry == null) {
            throw new IllegalStateException("MeterRegistry is not available for custom metrics registration");
        }
        
        // Verify that the MeterRegistry is functional by checking its configuration
        try {
            String registryClass = meterRegistry.getClass().getSimpleName();
            log.debug("Using MeterRegistry implementation: {}", registryClass);
            
            // Test basic functionality
            meterRegistry.counter("metrics.configuration.validation.test").increment();
            log.debug("MeterRegistry validation successful");
            
        } catch (Exception e) {
            log.warn("MeterRegistry validation failed, but continuing with initialization: {}", e.getMessage());
        }
    }

    /**
     * Validates that metrics have been properly registered with the MeterRegistry.
     */
    private void validateMetricsRegistration() {
        try {
            int registeredMeters = meterRegistry.getMeters().size();
            log.info("Total meters registered in MeterRegistry: {}", registeredMeters);
            
            // Check for specific custom metrics
            validateSpecificMetrics();
            
        } catch (Exception e) {
            log.warn("Failed to validate metrics registration: {}", e.getMessage());
        }
    }

    /**
     * Validates that specific custom metrics are properly registered.
     */
    private void validateSpecificMetrics() {
        // Check database metrics
        if (databaseMetricsEnabled) {
            validateDatabaseMetrics();
        }
        
        // Check HTTP metrics
        if (httpMetricsEnabled) {
            validateHttpMetrics();
        }
    }

    /**
     * Validates that database metrics are properly registered.
     */
    private void validateDatabaseMetrics() {
        String[] expectedDatabaseMetrics = {
            "db_connection_acquisition_duration_seconds",
            "db_pool_exhaustion_events_total",
            "db_connection_acquisition_failures_total",
            "db_pool_connections_total",
            "db_pool_connections_active",
            "db_pool_connections_idle"
        };
        
        for (String metricName : expectedDatabaseMetrics) {
            if (meterRegistry.find(metricName).meter() != null) {
                log.debug("Database metric '{}' is properly registered", metricName);
            } else {
                log.warn("Database metric '{}' is not registered", metricName);
            }
        }
    }

    /**
     * Validates that HTTP metrics are properly registered.
     */
    private void validateHttpMetrics() {
        String[] expectedHttpMetrics = {
            "http_pool_connections_total",
            "http_pool_connections_active",
            "http_pool_connections_idle"
        };
        
        for (String metricName : expectedHttpMetrics) {
            if (meterRegistry.find(metricName).meter() != null) {
                log.debug("HTTP metric '{}' is properly registered", metricName);
            } else {
                log.debug("HTTP metric '{}' may not be registered yet (this is normal during startup)", metricName);
            }
        }
    }

    /**
     * Logs the current metrics configuration for debugging purposes.
     */
    private void logMetricsConfiguration() {
        log.info("Custom Metrics Configuration:");
        log.info("  - Database metrics enabled: {}", databaseMetricsEnabled);
        log.info("  - HTTP metrics enabled: {}", httpMetricsEnabled);
        log.info("  - Security Service URL: {}", securityServiceUrl != null ? securityServiceUrl : "not configured");
        log.info("  - Portfolio Service URL: {}", portfolioServiceUrl != null ? portfolioServiceUrl : "not configured");
        log.info("  - Initialization timeout: {} seconds", initializationTimeoutSeconds);
        log.info("  - MeterRegistry class: {}", meterRegistry.getClass().getSimpleName());
    }

    /**
     * Gets the current status of metrics initialization.
     * 
     * @return a status string describing the current state of metrics
     */
    public String getMetricsStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Custom Metrics Status:\n");
        
        if (databaseMetricsEnabled) {
            boolean dbInitialized = databaseMetricsService.isInitialized();
            boolean interceptorInitialized = databaseConnectionInterceptor.isInitialized();
            status.append("  - Database metrics: ").append(dbInitialized ? "INITIALIZED" : "NOT INITIALIZED").append("\n");
            status.append("  - Connection interceptor: ").append(interceptorInitialized ? "INITIALIZED" : "NOT INITIALIZED").append("\n");
            
            if (dbInitialized) {
                status.append("  - Pool statistics: ").append(databaseMetricsService.getPoolStatistics()).append("\n");
            }
        } else {
            status.append("  - Database metrics: DISABLED\n");
        }
        
        if (httpMetricsEnabled) {
            int registeredServices = httpMetricsService.getRegisteredServices().size();
            status.append("  - HTTP metrics: ").append(registeredServices).append(" services registered\n");
        } else {
            status.append("  - HTTP metrics: DISABLED\n");
        }
        
        int totalMeters = meterRegistry.getMeters().size();
        status.append("  - Total registered meters: ").append(totalMeters);
        
        return status.toString();
    }

    /**
     * Checks if all enabled metrics are properly initialized.
     * 
     * @return true if all enabled metrics are initialized, false otherwise
     */
    public boolean areAllMetricsInitialized() {
        boolean allInitialized = true;
        
        if (databaseMetricsEnabled) {
            allInitialized &= databaseMetricsService.isInitialized() && 
                             databaseConnectionInterceptor.isInitialized();
        }
        
        if (httpMetricsEnabled) {
            // HTTP metrics are considered initialized if at least one service is registered
            allInitialized &= !httpMetricsService.getRegisteredServices().isEmpty();
        }
        
        return allInitialized;
    }
}