package org.kasbench.globeco_order_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for bulk order submission functionality.
 * Provides comprehensive configuration options for enabling/disabling bulk submission,
 * setting timeouts, batch sizes, and fallback behavior.
 * 
 * This class supports feature flags and monitoring configuration to ensure
 * safe deployment and operation of the bulk submission feature.
 */
@Data
@Component
@ConfigurationProperties(prefix = "bulk.submission")
public class BulkSubmissionProperties {

    /**
     * Master switch to enable/disable bulk submission functionality.
     * When false, the system falls back to individual order submission.
     */
    private boolean enabled = true;

    /**
     * Trade service configuration for bulk operations.
     */
    private TradeService tradeService = new TradeService();

    /**
     * Fallback behavior configuration.
     */
    private Fallback fallback = new Fallback();

    /**
     * Monitoring and alerting configuration.
     */
    private Monitoring monitoring = new Monitoring();

    /**
     * Performance optimization configuration.
     */
    private Performance performance = new Performance();

    @Data
    public static class TradeService {
        /**
         * Timeout for bulk trade service calls (in milliseconds).
         * Should be higher than individual submission timeout due to bulk processing.
         */
        private long timeoutMs = 60000; // 60 seconds

        /**
         * Maximum batch size for bulk submissions.
         * Should not exceed trade service limits.
         */
        private int maxBatchSize = 100;

        /**
         * Connection timeout for trade service HTTP client (in milliseconds).
         */
        private long connectionTimeoutMs = 10000; // 10 seconds

        /**
         * Read timeout for trade service HTTP client (in milliseconds).
         */
        private long readTimeoutMs = 60000; // 60 seconds

        /**
         * Maximum number of retry attempts for failed bulk submissions.
         */
        private int maxRetries = 2;

        /**
         * Delay between retry attempts (in milliseconds).
         */
        private long retryDelayMs = 1000;

        /**
         * Enable exponential backoff for retry attempts.
         */
        private boolean exponentialBackoff = true;
    }

    @Data
    public static class Fallback {
        /**
         * Enable fallback to individual submission when bulk submission fails.
         * When true, failed bulk submissions are retried using individual calls.
         */
        private boolean enabled = false;

        /**
         * Maximum number of orders to process individually in fallback mode.
         * Prevents excessive individual calls for large batches.
         */
        private int maxIndividualOrders = 10;

        /**
         * Timeout for individual order submissions in fallback mode (in milliseconds).
         */
        private long individualTimeoutMs = 30000; // 30 seconds

        /**
         * Enable automatic fallback on specific error conditions.
         */
        private boolean autoFallbackOnErrors = true;

        /**
         * HTTP status codes that trigger automatic fallback.
         * Common codes: 503 (Service Unavailable), 429 (Too Many Requests)
         */
        private int[] fallbackTriggerStatusCodes = {503, 429, 502, 504};
    }

    @Data
    public static class Monitoring {
        /**
         * Enable detailed performance monitoring for bulk submissions.
         */
        private boolean performanceMonitoringEnabled = true;

        /**
         * Enable success rate monitoring and alerting.
         */
        private boolean successRateMonitoringEnabled = true;

        /**
         * Minimum success rate threshold for alerting (percentage).
         * When success rate falls below this threshold, alerts are triggered.
         */
        private double successRateThreshold = 0.95; // 95%

        /**
         * Time window for success rate calculation (in minutes).
         */
        private int successRateWindowMinutes = 15;

        /**
         * Enable latency monitoring and alerting.
         */
        private boolean latencyMonitoringEnabled = true;

        /**
         * Maximum acceptable latency for bulk submissions (in milliseconds).
         * When exceeded, alerts are triggered.
         */
        private long maxAcceptableLatencyMs = 30000; // 30 seconds

        /**
         * Enable error rate monitoring.
         */
        private boolean errorRateMonitoringEnabled = true;

        /**
         * Maximum acceptable error rate (percentage).
         */
        private double maxErrorRate = 0.05; // 5%

        /**
         * Enable detailed logging of bulk submission operations.
         */
        private boolean detailedLoggingEnabled = true;

        /**
         * Log performance metrics for each bulk submission.
         */
        private boolean logPerformanceMetrics = true;
    }

    @Data
    public static class Performance {
        /**
         * Enable database connection pooling optimizations for bulk operations.
         */
        private boolean dbPoolOptimizationEnabled = true;

        /**
         * Maximum number of database connections to use for bulk operations.
         */
        private int maxDbConnections = 5;

        /**
         * Enable batch database updates for order status changes.
         */
        private boolean batchDbUpdatesEnabled = true;

        /**
         * Batch size for database update operations.
         */
        private int dbUpdateBatchSize = 50;

        /**
         * Enable asynchronous processing for non-critical operations.
         */
        private boolean asyncProcessingEnabled = true;

        /**
         * Thread pool size for asynchronous operations.
         */
        private int asyncThreadPoolSize = 3;

        /**
         * Enable memory optimization for large batch processing.
         */
        private boolean memoryOptimizationEnabled = true;

        /**
         * Maximum memory usage threshold for batch processing (in MB).
         */
        private long maxMemoryUsageMb = 256;
    }

    /**
     * Validates the configuration properties and applies defaults where necessary.
     * This method is called after properties are bound to ensure consistency.
     */
    public void validate() {
        // Ensure timeouts are reasonable
        if (tradeService.timeoutMs < 1000) {
            tradeService.timeoutMs = 1000; // Minimum 1 second
        }
        
        if (tradeService.connectionTimeoutMs < 1000) {
            tradeService.connectionTimeoutMs = 1000;
        }
        
        if (tradeService.readTimeoutMs < tradeService.timeoutMs) {
            tradeService.readTimeoutMs = tradeService.timeoutMs;
        }

        // Ensure batch sizes are within reasonable bounds
        if (tradeService.maxBatchSize < 1) {
            tradeService.maxBatchSize = 1;
        } else if (tradeService.maxBatchSize > 1000) {
            tradeService.maxBatchSize = 1000; // Trade service limit
        }

        // Ensure retry configuration is reasonable
        if (tradeService.maxRetries < 0) {
            tradeService.maxRetries = 0;
        } else if (tradeService.maxRetries > 5) {
            tradeService.maxRetries = 5; // Prevent excessive retries
        }

        // Ensure fallback configuration is reasonable
        if (fallback.maxIndividualOrders < 0) {
            fallback.maxIndividualOrders = 0;
        } else if (fallback.maxIndividualOrders > tradeService.maxBatchSize) {
            fallback.maxIndividualOrders = tradeService.maxBatchSize;
        }

        // Ensure monitoring thresholds are within valid ranges
        if (monitoring.successRateThreshold < 0.0 || monitoring.successRateThreshold > 1.0) {
            monitoring.successRateThreshold = 0.95;
        }
        
        if (monitoring.maxErrorRate < 0.0 || monitoring.maxErrorRate > 1.0) {
            monitoring.maxErrorRate = 0.05;
        }

        // Ensure performance configuration is reasonable
        if (performance.maxDbConnections < 1) {
            performance.maxDbConnections = 1;
        }
        
        if (performance.dbUpdateBatchSize < 1) {
            performance.dbUpdateBatchSize = 1;
        }
        
        if (performance.asyncThreadPoolSize < 1) {
            performance.asyncThreadPoolSize = 1;
        }
    }

    /**
     * Checks if bulk submission is fully enabled and configured.
     * 
     * @return true if bulk submission should be used
     */
    public boolean isBulkSubmissionEnabled() {
        return enabled && tradeService.maxBatchSize > 0;
    }

    /**
     * Checks if fallback to individual submission is enabled.
     * 
     * @return true if fallback is enabled
     */
    public boolean isFallbackEnabled() {
        return fallback.enabled && fallback.maxIndividualOrders > 0;
    }

    /**
     * Checks if a given HTTP status code should trigger fallback.
     * 
     * @param statusCode the HTTP status code to check
     * @return true if the status code should trigger fallback
     */
    public boolean shouldTriggerFallback(int statusCode) {
        if (!fallback.autoFallbackOnErrors || fallback.fallbackTriggerStatusCodes == null) {
            return false;
        }
        
        for (int triggerCode : fallback.fallbackTriggerStatusCodes) {
            if (triggerCode == statusCode) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the effective timeout for bulk operations including retries.
     * 
     * @return total timeout including all retry attempts
     */
    public long getEffectiveTotalTimeoutMs() {
        long baseTimeout = tradeService.timeoutMs;
        long retryDelay = tradeService.retryDelayMs * tradeService.maxRetries;
        
        if (tradeService.exponentialBackoff) {
            // Calculate exponential backoff total delay
            long totalRetryDelay = 0;
            for (int i = 0; i < tradeService.maxRetries; i++) {
                totalRetryDelay += tradeService.retryDelayMs * Math.pow(2, i);
            }
            retryDelay = totalRetryDelay;
        }
        
        return baseTimeout + retryDelay;
    }

    /**
     * Gets the retry delay for a specific attempt with exponential backoff if enabled.
     * 
     * @param attempt the retry attempt number (0-based)
     * @return the delay in milliseconds for this attempt
     */
    public long getRetryDelayMs(int attempt) {
        if (!tradeService.exponentialBackoff) {
            return tradeService.retryDelayMs;
        }
        
        return (long) (tradeService.retryDelayMs * Math.pow(2, attempt));
    }

    /**
     * Checks if performance monitoring is enabled.
     * 
     * @return true if any performance monitoring is enabled
     */
    public boolean isPerformanceMonitoringEnabled() {
        return monitoring.performanceMonitoringEnabled || 
               monitoring.successRateMonitoringEnabled || 
               monitoring.latencyMonitoringEnabled ||
               monitoring.errorRateMonitoringEnabled;
    }

    /**
     * Checks if detailed logging should be enabled for bulk operations.
     * 
     * @return true if detailed logging is enabled
     */
    public boolean isDetailedLoggingEnabled() {
        return monitoring.detailedLoggingEnabled;
    }

    /**
     * Gets the maximum batch size considering both configuration and performance limits.
     * 
     * @return the effective maximum batch size
     */
    public int getEffectiveMaxBatchSize() {
        int configuredMax = tradeService.maxBatchSize;
        
        // Apply memory-based limits if memory optimization is enabled
        if (performance.memoryOptimizationEnabled) {
            // Estimate memory usage per order (rough estimate: 1KB per order)
            long estimatedMemoryPerOrder = 1024; // 1KB
            long maxOrdersForMemory = (performance.maxMemoryUsageMb * 1024 * 1024) / estimatedMemoryPerOrder;
            
            if (maxOrdersForMemory < configuredMax) {
                return (int) Math.max(1, maxOrdersForMemory);
            }
        }
        
        return configuredMax;
    }
}