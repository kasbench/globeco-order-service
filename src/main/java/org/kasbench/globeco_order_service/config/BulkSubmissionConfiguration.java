package org.kasbench.globeco_order_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class for bulk order submission functionality.
 * This class manages the initialization and validation of bulk submission configuration,
 * provides feature flag management, and ensures proper setup of monitoring and alerting.
 * 
 * The configuration supports safe deployment through feature flags and comprehensive
 * monitoring to track performance and reliability of bulk submission operations.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(BulkSubmissionProperties.class)
@ConditionalOnProperty(name = "bulk.submission.enabled", havingValue = "true", matchIfMissing = false)
public class BulkSubmissionConfiguration {

    private final BulkSubmissionProperties bulkSubmissionProperties;
    
    @Autowired
    public BulkSubmissionConfiguration(BulkSubmissionProperties bulkSubmissionProperties) {
        log.info("=== BulkSubmissionConfiguration constructor called ===");
        this.bulkSubmissionProperties = bulkSubmissionProperties;
        log.info("Bulk submission configuration initialized with enabled={}", 
                bulkSubmissionProperties.isEnabled());
    }

    /**
     * Initializes bulk submission configuration on application startup.
     * This method validates configuration properties and logs the current setup.
     */
    @PostConstruct
    public void initializeBulkSubmissionConfiguration() {
        log.info("=== BulkSubmissionConfiguration @PostConstruct called ===");
        log.info("Starting bulk submission configuration initialization");
        
        try {
            // Validate and apply configuration properties
            bulkSubmissionProperties.validate();
            
            validateConfiguration();
            logBulkSubmissionConfiguration();
            
            log.info("Bulk submission configuration initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize bulk submission configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Bulk submission configuration initialization failed", e);
        }
    }

    /**
     * Event listener that logs configuration status after the application context is fully refreshed.
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationContextRefreshed(ContextRefreshedEvent event) {
        log.info("Application context refreshed, bulk submission configuration is ready");
        
        if (bulkSubmissionProperties.isBulkSubmissionEnabled()) {
            log.info("Bulk submission is ENABLED and ready for use");
            
            if (bulkSubmissionProperties.isFallbackEnabled()) {
                log.info("Fallback to individual submission is ENABLED");
            } else {
                log.info("Fallback to individual submission is DISABLED");
            }
            
            if (bulkSubmissionProperties.isPerformanceMonitoringEnabled()) {
                log.info("Performance monitoring is ENABLED");
            }
        } else {
            log.warn("Bulk submission is DISABLED - using individual submission");
        }
    }

    /**
     * Validates the bulk submission configuration for consistency and safety.
     */
    private void validateConfiguration() {
        // Validate trade service configuration
        validateTradeServiceConfiguration();
        
        // Validate fallback configuration
        validateFallbackConfiguration();
        
        // Validate monitoring configuration
        validateMonitoringConfiguration();
        
        // Validate performance configuration
        validatePerformanceConfiguration();
    }

    /**
     * Validates trade service specific configuration.
     */
    private void validateTradeServiceConfiguration() {
        BulkSubmissionProperties.TradeService tradeService = bulkSubmissionProperties.getTradeService();
        
        if (tradeService.getTimeoutMs() < 5000) {
            log.warn("Trade service timeout is very low ({}ms), consider increasing for bulk operations", 
                    tradeService.getTimeoutMs());
        }
        
        if (tradeService.getMaxBatchSize() > 1000) {
            log.warn("Maximum batch size ({}) exceeds recommended limit (1000), may cause performance issues", 
                    tradeService.getMaxBatchSize());
        }
        
        if (tradeService.getReadTimeoutMs() < tradeService.getTimeoutMs()) {
            log.warn("Read timeout ({}ms) is less than general timeout ({}ms), this may cause issues", 
                    tradeService.getReadTimeoutMs(), tradeService.getTimeoutMs());
        }
        
        long totalTimeout = bulkSubmissionProperties.getEffectiveTotalTimeoutMs();
        if (totalTimeout > 300000) { // 5 minutes
            log.warn("Total timeout including retries is very high ({}ms), consider reducing retry attempts", 
                    totalTimeout);
        }
        
        log.debug("Trade service configuration validated successfully");
    }

    /**
     * Validates fallback configuration.
     */
    private void validateFallbackConfiguration() {
        BulkSubmissionProperties.Fallback fallback = bulkSubmissionProperties.getFallback();
        
        if (fallback.isEnabled()) {
            if (fallback.getMaxIndividualOrders() > 50) {
                log.warn("Maximum individual orders for fallback ({}) is high, may cause performance issues", 
                        fallback.getMaxIndividualOrders());
            }
            
            if (fallback.getFallbackTriggerStatusCodes().length == 0) {
                log.warn("No fallback trigger status codes configured, automatic fallback will not work");
            }
            
            log.info("Fallback configuration: enabled with max {} individual orders", 
                    fallback.getMaxIndividualOrders());
        } else {
            log.info("Fallback configuration: disabled");
        }
    }

    /**
     * Validates monitoring configuration.
     */
    private void validateMonitoringConfiguration() {
        BulkSubmissionProperties.Monitoring monitoring = bulkSubmissionProperties.getMonitoring();
        
        if (monitoring.getSuccessRateThreshold() < 0.8) {
            log.warn("Success rate threshold ({}) is very low, consider increasing for better reliability", 
                    monitoring.getSuccessRateThreshold());
        }
        
        if (monitoring.getMaxErrorRate() > 0.1) {
            log.warn("Maximum error rate ({}) is high, consider lowering for better reliability", 
                    monitoring.getMaxErrorRate());
        }
        
        if (monitoring.getMaxAcceptableLatencyMs() < 10000) {
            log.warn("Maximum acceptable latency ({}ms) is low for bulk operations, consider increasing", 
                    monitoring.getMaxAcceptableLatencyMs());
        }
        
        log.debug("Monitoring configuration validated successfully");
    }

    /**
     * Validates performance configuration.
     */
    private void validatePerformanceConfiguration() {
        BulkSubmissionProperties.Performance performance = bulkSubmissionProperties.getPerformance();
        
        if (performance.getMaxDbConnections() > 20) {
            log.warn("Maximum database connections ({}) is high, may exhaust connection pool", 
                    performance.getMaxDbConnections());
        }
        
        if (performance.getAsyncThreadPoolSize() > 10) {
            log.warn("Async thread pool size ({}) is high, may cause resource contention", 
                    performance.getAsyncThreadPoolSize());
        }
        
        if (performance.getMaxMemoryUsageMb() > 1024) {
            log.warn("Maximum memory usage ({}MB) is high, monitor for memory issues", 
                    performance.getMaxMemoryUsageMb());
        }
        
        log.debug("Performance configuration validated successfully");
    }

    /**
     * Logs the current bulk submission configuration for debugging and monitoring.
     */
    private void logBulkSubmissionConfiguration() {
        log.info("Bulk Submission Configuration:");
        log.info("  - Feature enabled: {}", bulkSubmissionProperties.isEnabled());
        log.info("  - Bulk submission enabled: {}", bulkSubmissionProperties.isBulkSubmissionEnabled());
        
        // Trade service configuration
        BulkSubmissionProperties.TradeService tradeService = bulkSubmissionProperties.getTradeService();
        log.info("  - Trade service timeout: {}ms", tradeService.getTimeoutMs());
        log.info("  - Maximum batch size: {}", tradeService.getMaxBatchSize());
        log.info("  - Effective max batch size: {}", bulkSubmissionProperties.getEffectiveMaxBatchSize());
        log.info("  - Connection timeout: {}ms", tradeService.getConnectionTimeoutMs());
        log.info("  - Read timeout: {}ms", tradeService.getReadTimeoutMs());
        log.info("  - Max retries: {}", tradeService.getMaxRetries());
        log.info("  - Retry delay: {}ms", tradeService.getRetryDelayMs());
        log.info("  - Exponential backoff: {}", tradeService.isExponentialBackoff());
        log.info("  - Total timeout (with retries): {}ms", bulkSubmissionProperties.getEffectiveTotalTimeoutMs());
        
        // Fallback configuration
        BulkSubmissionProperties.Fallback fallback = bulkSubmissionProperties.getFallback();
        log.info("  - Fallback enabled: {}", fallback.isEnabled());
        if (fallback.isEnabled()) {
            log.info("  - Max individual orders: {}", fallback.getMaxIndividualOrders());
            log.info("  - Individual timeout: {}ms", fallback.getIndividualTimeoutMs());
            log.info("  - Auto fallback on errors: {}", fallback.isAutoFallbackOnErrors());
            log.info("  - Fallback trigger codes: {}", java.util.Arrays.toString(fallback.getFallbackTriggerStatusCodes()));
        }
        
        // Monitoring configuration
        BulkSubmissionProperties.Monitoring monitoring = bulkSubmissionProperties.getMonitoring();
        log.info("  - Performance monitoring: {}", monitoring.isPerformanceMonitoringEnabled());
        log.info("  - Success rate monitoring: {}", monitoring.isSuccessRateMonitoringEnabled());
        if (monitoring.isSuccessRateMonitoringEnabled()) {
            log.info("  - Success rate threshold: {}%", monitoring.getSuccessRateThreshold() * 100);
            log.info("  - Success rate window: {} minutes", monitoring.getSuccessRateWindowMinutes());
        }
        log.info("  - Latency monitoring: {}", monitoring.isLatencyMonitoringEnabled());
        if (monitoring.isLatencyMonitoringEnabled()) {
            log.info("  - Max acceptable latency: {}ms", monitoring.getMaxAcceptableLatencyMs());
        }
        log.info("  - Error rate monitoring: {}", monitoring.isErrorRateMonitoringEnabled());
        if (monitoring.isErrorRateMonitoringEnabled()) {
            log.info("  - Max error rate: {}%", monitoring.getMaxErrorRate() * 100);
        }
        log.info("  - Detailed logging: {}", monitoring.isDetailedLoggingEnabled());
        log.info("  - Performance metrics logging: {}", monitoring.isLogPerformanceMetrics());
        
        // Performance configuration
        BulkSubmissionProperties.Performance performance = bulkSubmissionProperties.getPerformance();
        log.info("  - DB pool optimization: {}", performance.isDbPoolOptimizationEnabled());
        log.info("  - Max DB connections: {}", performance.getMaxDbConnections());
        log.info("  - Batch DB updates: {}", performance.isBatchDbUpdatesEnabled());
        log.info("  - DB update batch size: {}", performance.getDbUpdateBatchSize());
        log.info("  - Async processing: {}", performance.isAsyncProcessingEnabled());
        log.info("  - Async thread pool size: {}", performance.getAsyncThreadPoolSize());
        log.info("  - Memory optimization: {}", performance.isMemoryOptimizationEnabled());
        log.info("  - Max memory usage: {}MB", performance.getMaxMemoryUsageMb());
    }

    /**
     * Gets the current status of bulk submission configuration.
     * 
     * @return a status string describing the current configuration state
     */
    public String getBulkSubmissionStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Bulk Submission Configuration Status:\n");
        
        status.append("  - Feature Status: ").append(bulkSubmissionProperties.isEnabled() ? "ENABLED" : "DISABLED").append("\n");
        status.append("  - Bulk Submission: ").append(bulkSubmissionProperties.isBulkSubmissionEnabled() ? "ACTIVE" : "INACTIVE").append("\n");
        status.append("  - Fallback: ").append(bulkSubmissionProperties.isFallbackEnabled() ? "ENABLED" : "DISABLED").append("\n");
        status.append("  - Performance Monitoring: ").append(bulkSubmissionProperties.isPerformanceMonitoringEnabled() ? "ENABLED" : "DISABLED").append("\n");
        
        BulkSubmissionProperties.TradeService tradeService = bulkSubmissionProperties.getTradeService();
        status.append("  - Max Batch Size: ").append(tradeService.getMaxBatchSize()).append("\n");
        status.append("  - Effective Max Batch Size: ").append(bulkSubmissionProperties.getEffectiveMaxBatchSize()).append("\n");
        status.append("  - Timeout: ").append(tradeService.getTimeoutMs()).append("ms\n");
        status.append("  - Total Timeout (with retries): ").append(bulkSubmissionProperties.getEffectiveTotalTimeoutMs()).append("ms");
        
        return status.toString();
    }

    /**
     * Checks if bulk submission is ready for use.
     * 
     * @return true if bulk submission is properly configured and enabled
     */
    public boolean isBulkSubmissionReady() {
        return bulkSubmissionProperties.isBulkSubmissionEnabled() && 
               bulkSubmissionProperties.getTradeService().getMaxBatchSize() > 0 &&
               bulkSubmissionProperties.getTradeService().getTimeoutMs() > 0;
    }

    /**
     * Gets the bulk submission properties for use by other components.
     * 
     * @return the bulk submission properties
     */
    public BulkSubmissionProperties getBulkSubmissionProperties() {
        return bulkSubmissionProperties;
    }

    /**
     * Checks if a given batch size is within configured limits.
     * 
     * @param batchSize the batch size to check
     * @return true if the batch size is acceptable
     */
    public boolean isValidBatchSize(int batchSize) {
        return batchSize > 0 && batchSize <= bulkSubmissionProperties.getEffectiveMaxBatchSize();
    }

    /**
     * Gets the recommended batch size based on current configuration and system state.
     * 
     * @param requestedSize the originally requested batch size
     * @return the recommended batch size to use
     */
    public int getRecommendedBatchSize(int requestedSize) {
        int maxSize = bulkSubmissionProperties.getEffectiveMaxBatchSize();
        
        if (requestedSize <= 0) {
            return Math.min(50, maxSize); // Default reasonable size
        }
        
        return Math.min(requestedSize, maxSize);
    }

    /**
     * Checks if fallback should be triggered for a given error condition.
     * 
     * @param statusCode the HTTP status code received
     * @param exception the exception that occurred (can be null)
     * @return true if fallback should be triggered
     */
    public boolean shouldTriggerFallback(int statusCode, Exception exception) {
        if (!bulkSubmissionProperties.isFallbackEnabled()) {
            return false;
        }
        
        // Check status code triggers
        if (bulkSubmissionProperties.shouldTriggerFallback(statusCode)) {
            return true;
        }
        
        // Check exception-based triggers
        if (exception != null) {
            // Trigger fallback for connection-related exceptions
            String exceptionMessage = exception.getMessage();
            if (exceptionMessage != null) {
                String lowerMessage = exceptionMessage.toLowerCase();
                return lowerMessage.contains("connection") || 
                       lowerMessage.contains("timeout") || 
                       lowerMessage.contains("unavailable");
            }
        }
        
        return false;
    }
}