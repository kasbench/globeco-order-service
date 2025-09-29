package org.kasbench.globeco_order_service.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

/**
 * Service for tracking error metrics and system overload conditions.
 * Integrates with the existing MeterRegistry to provide comprehensive error monitoring
 * and system health metrics for proper alerting and capacity planning.
 */
@Service
@ConditionalOnProperty(name = "metrics.custom.enabled", havingValue = "true", matchIfMissing = false)
public class ErrorMetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(ErrorMetricsService.class);
    
    private final MeterRegistry meterRegistry;
    private final SystemOverloadDetector systemOverloadDetector;
    
    // Error counters by type
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();
    
    // System overload metrics
    private final AtomicInteger currentOverloadEvents = new AtomicInteger(0);
    private final AtomicLong totalOverloadEvents = new AtomicLong(0);
    private final AtomicInteger currentRetryDelay = new AtomicInteger(0);
    private final AtomicLong overloadStartTime = new AtomicLong(0);
    
    // Resource utilization gauges
    private Gauge threadPoolUtilizationGauge;
    private Gauge databasePoolUtilizationGauge;
    private Gauge memoryUtilizationGauge;
    
    // Error rate metrics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    
    @Autowired
    public ErrorMetricsService(MeterRegistry meterRegistry, 
                              @Autowired(required = false) SystemOverloadDetector systemOverloadDetector) {
        this.meterRegistry = meterRegistry;
        this.systemOverloadDetector = systemOverloadDetector;
        logger.debug("ErrorMetricsService initialized with MeterRegistry: {}, SystemOverloadDetector: {}", 
                    meterRegistry != null ? meterRegistry.getClass().getSimpleName() : "null",
                    systemOverloadDetector != null ? "available" : "null");
    }
    
    @PostConstruct
    public void initializeErrorMetrics() {
        logger.info("Initializing error metrics and system overload monitoring");
        
        try {
            registerSystemOverloadMetrics();
            registerResourceUtilizationMetrics();
            registerErrorRateMetrics();
            
            logger.info("Error metrics successfully initialized and registered");
        } catch (Exception e) {
            logger.error("Failed to initialize error metrics: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Records an error occurrence with the specified error code and HTTP status.
     * 
     * @param errorCode the error classification code
     * @param httpStatus the HTTP status code
     * @param severity the error severity level
     */
    public void recordError(String errorCode, int httpStatus, String severity) {
        if (errorCode == null || errorCode.trim().isEmpty()) {
            logger.warn("Cannot record error: errorCode is null or empty");
            return;
        }
        
        try {
            // Increment total error count
            totalErrors.incrementAndGet();
            
            // Get or create counter for this specific error type
            Counter errorCounter = getOrCreateErrorCounter(errorCode, httpStatus, severity);
            errorCounter.increment();
            
            logger.debug("Recorded error: code={}, status={}, severity={}", errorCode, httpStatus, severity);
            
        } catch (Exception e) {
            logger.error("Failed to record error metrics for code '{}': {}", errorCode, e.getMessage());
        }
    }
    
    /**
     * Records a system overload event with retry delay information.
     * 
     * @param overloadReason the reason for the overload
     * @param retryDelaySeconds the recommended retry delay
     */
    public void recordSystemOverload(String overloadReason, int retryDelaySeconds) {
        try {
            // Increment overload counters
            currentOverloadEvents.incrementAndGet();
            totalOverloadEvents.incrementAndGet();
            currentRetryDelay.set(retryDelaySeconds);
            
            // Record overload start time if this is the first event
            if (overloadStartTime.get() == 0) {
                overloadStartTime.set(System.currentTimeMillis());
            }
            
            // Record overload event with reason
            Counter overloadCounter = getOrCreateOverloadCounter(overloadReason);
            overloadCounter.increment();
            
            logger.info("Recorded system overload event: reason={}, retryDelay={}s", 
                       overloadReason, retryDelaySeconds);
            
        } catch (Exception e) {
            logger.error("Failed to record system overload metrics: {}", e.getMessage());
        }
    }
    
    /**
     * Records the end of a system overload condition.
     */
    public void recordOverloadRecovery() {
        try {
            long overloadDuration = 0;
            if (overloadStartTime.get() > 0) {
                overloadDuration = System.currentTimeMillis() - overloadStartTime.get();
                overloadStartTime.set(0);
            }
            
            currentOverloadEvents.set(0);
            currentRetryDelay.set(0);
            
            // Record overload duration
            if (overloadDuration > 0) {
                Timer overloadDurationTimer = Timer.builder("system_overload_duration_seconds")
                    .description("Duration of system overload events")
                    .register(meterRegistry);
                overloadDurationTimer.record(overloadDuration, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            
            logger.info("Recorded system overload recovery, duration: {}ms", overloadDuration);
            
        } catch (Exception e) {
            logger.error("Failed to record overload recovery metrics: {}", e.getMessage());
        }
    }
    
    /**
     * Records a successful request (for error rate calculation).
     */
    public void recordRequest() {
        totalRequests.incrementAndGet();
    }
    
    /**
     * Gets the current error rate as a percentage.
     * 
     * @return error rate percentage (0.0 to 100.0)
     */
    public double getCurrentErrorRate() {
        long requests = totalRequests.get();
        long errors = totalErrors.get();
        
        if (requests == 0) {
            return 0.0;
        }
        
        return (errors * 100.0) / requests;
    }
    
    /**
     * Gets or creates an error counter for the specified error type.
     */
    private Counter getOrCreateErrorCounter(String errorCode, int httpStatus, String severity) {
        String counterKey = String.format("%s_%d_%s", errorCode, httpStatus, severity);
        
        return errorCounters.computeIfAbsent(counterKey, key -> 
            Counter.builder("http_errors_total")
                .description("Total number of HTTP errors by type")
                .tag("error_code", errorCode)
                .tag("status_code", String.valueOf(httpStatus))
                .tag("severity", severity)
                .tag("retryable", String.valueOf(ErrorClassification.isRetryable(errorCode)))
                .register(meterRegistry)
        );
    }
    
    /**
     * Gets or creates an overload counter for the specified reason.
     */
    private Counter getOrCreateOverloadCounter(String overloadReason) {
        String counterKey = "overload_" + overloadReason;
        
        return errorCounters.computeIfAbsent(counterKey, key ->
            Counter.builder("system_overload_events_total")
                .description("Total number of system overload events by reason")
                .tag("reason", overloadReason)
                .register(meterRegistry)
        );
    }
    
    /**
     * Registers system overload related metrics.
     */
    private void registerSystemOverloadMetrics() {
        // Current overload events gauge
        Gauge.builder("system_overload_current", currentOverloadEvents, AtomicInteger::get)
            .description("Current number of active overload conditions")
            .register(meterRegistry);
        
        // Total overload events counter (already handled by recordSystemOverload)
        
        // Current retry delay gauge
        Gauge.builder("system_overload_retry_after_seconds", currentRetryDelay, AtomicInteger::get)
            .description("Current recommended retry delay in seconds")
            .register(meterRegistry);
        
        logger.debug("System overload metrics registered successfully");
    }
    
    /**
     * Registers resource utilization metrics.
     */
    private void registerResourceUtilizationMetrics() {
        if (systemOverloadDetector == null) {
            logger.warn("SystemOverloadDetector not available, skipping resource utilization metrics");
            return;
        }
        
        // Thread pool utilization
        threadPoolUtilizationGauge = Gauge.builder("system_resource_utilization", this, service -> {
                try {
                    return systemOverloadDetector.getThreadPoolUtilization();
                } catch (Exception e) {
                    logger.debug("Failed to get thread pool utilization: {}", e.getMessage());
                    return 0.0;
                }
            })
            .description("Current system resource utilization")
            .tag("resource_type", "thread_pool")
            .register(meterRegistry);
        
        // Database connection pool utilization
        databasePoolUtilizationGauge = Gauge.builder("system_resource_utilization", this, service -> {
                try {
                    return systemOverloadDetector.getDatabaseConnectionUtilization();
                } catch (Exception e) {
                    logger.debug("Failed to get database pool utilization: {}", e.getMessage());
                    return 0.0;
                }
            })
            .description("Current system resource utilization")
            .tag("resource_type", "database_pool")
            .register(meterRegistry);
        
        // Memory utilization
        memoryUtilizationGauge = Gauge.builder("system_resource_utilization", this, service -> {
                try {
                    return systemOverloadDetector.getMemoryUtilization();
                } catch (Exception e) {
                    logger.debug("Failed to get memory utilization: {}", e.getMessage());
                    return 0.0;
                }
            })
            .description("Current system resource utilization")
            .tag("resource_type", "memory")
            .register(meterRegistry);
        
        logger.debug("Resource utilization metrics registered successfully");
    }
    
    /**
     * Registers error rate and request metrics.
     */
    private void registerErrorRateMetrics() {
        // Total requests counter
        Gauge.builder("http_requests_total", totalRequests, AtomicLong::get)
            .description("Total number of HTTP requests processed")
            .register(meterRegistry);
        
        // Total errors counter
        Gauge.builder("http_errors_total_count", totalErrors, AtomicLong::get)
            .description("Total number of HTTP errors across all types")
            .register(meterRegistry);
        
        // Error rate gauge
        Gauge.builder("http_error_rate_percent", this, ErrorMetricsService::getCurrentErrorRate)
            .description("Current HTTP error rate as percentage")
            .register(meterRegistry);
        
        logger.debug("Error rate metrics registered successfully");
    }
    
    /**
     * Gets the current metrics status for debugging and monitoring.
     * 
     * @return status string with current metrics information
     */
    public String getMetricsStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Error Metrics Status:\n");
        status.append("  - Total requests: ").append(totalRequests.get()).append("\n");
        status.append("  - Total errors: ").append(totalErrors.get()).append("\n");
        status.append("  - Error rate: ").append(String.format("%.2f%%", getCurrentErrorRate())).append("\n");
        status.append("  - Current overload events: ").append(currentOverloadEvents.get()).append("\n");
        status.append("  - Total overload events: ").append(totalOverloadEvents.get()).append("\n");
        status.append("  - Current retry delay: ").append(currentRetryDelay.get()).append("s\n");
        status.append("  - Registered error counters: ").append(errorCounters.size());
        
        return status.toString();
    }
    
    /**
     * Checks if the error metrics service is properly initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return meterRegistry != null && 
               threadPoolUtilizationGauge != null && 
               databasePoolUtilizationGauge != null && 
               memoryUtilizationGauge != null;
    }
    
    /**
     * Forces an update of all resource utilization metrics.
     * Useful for testing and debugging purposes.
     */
    public void forceMetricsUpdate() {
        logger.debug("Forcing error metrics update");
        
        if (systemOverloadDetector != null && systemOverloadDetector.isInitialized()) {
            try {
                // The gauges will automatically pull the latest values
                double threadUtil = systemOverloadDetector.getThreadPoolUtilization();
                double dbUtil = systemOverloadDetector.getDatabaseConnectionUtilization();
                double memUtil = systemOverloadDetector.getMemoryUtilization();
                
                logger.debug("Updated resource utilization - Thread: {:.1f}%, DB: {:.1f}%, Memory: {:.1f}%",
                           threadUtil * 100, dbUtil * 100, memUtil * 100);
            } catch (Exception e) {
                logger.warn("Failed to force metrics update: {}", e.getMessage());
            }
        }
    }
}