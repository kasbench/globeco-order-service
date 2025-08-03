package org.kasbench.globeco_order_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for custom OpenTelemetry metrics.
 * Provides comprehensive configuration options for enabling/disabling metrics,
 * setting collection intervals, thresholds, and other customization options.
 * 
 * This class supports backward compatibility with existing configuration
 * while providing enhanced customization capabilities.
 */
@Data
@Component
@ConfigurationProperties(prefix = "metrics.custom")
public class MetricsProperties {

    /**
     * Master switch to enable/disable all custom metrics.
     * When false, no custom metrics will be registered or collected.
     */
    private boolean enabled = true;

    /**
     * Database metrics configuration.
     */
    private DatabaseMetrics database = new DatabaseMetrics();

    /**
     * HTTP metrics configuration.
     */
    private HttpMetrics http = new HttpMetrics();

    /**
     * General metrics collection configuration.
     */
    private Collection collection = new Collection();

    /**
     * Initialization configuration.
     */
    private Initialization initialization = new Initialization();

    @Data
    public static class DatabaseMetrics {
        /**
         * Enable/disable database connection pool metrics.
         */
        private boolean enabled = true;

        /**
         * Collection interval for database metrics in seconds.
         * Controls how frequently database pool statistics are updated.
         */
        private Duration collectionInterval = Duration.ofSeconds(30);

        /**
         * Threshold for connection acquisition duration warning (in milliseconds).
         * When exceeded, additional logging or alerting may be triggered.
         */
        private long acquisitionWarningThresholdMs = 5000;

        /**
         * Threshold for pool exhaustion events before triggering alerts.
         * Number of exhaustion events within the collection interval.
         */
        private int exhaustionAlertThreshold = 5;

        /**
         * Enable detailed connection acquisition timing metrics.
         * When disabled, only basic pool state metrics are collected.
         */
        private boolean detailedTimingEnabled = true;

        /**
         * Enable connection leak detection metrics.
         * Requires HikariCP leak detection to be enabled.
         */
        private boolean leakDetectionEnabled = true;

        /**
         * Minimum pool utilization percentage to start collecting detailed metrics.
         * Helps reduce overhead when pool usage is low.
         */
        private double minUtilizationForDetailedMetrics = 0.1; // 10%
    }

    @Data
    public static class HttpMetrics {
        /**
         * Enable/disable HTTP connection pool metrics.
         */
        private boolean enabled = true;

        /**
         * Collection interval for HTTP metrics in seconds.
         */
        private Duration collectionInterval = Duration.ofSeconds(30);

        /**
         * Threshold for HTTP connection pool utilization warning (percentage).
         * When exceeded, additional monitoring may be triggered.
         */
        private double utilizationWarningThreshold = 0.8; // 80%

        /**
         * Maximum number of services to monitor for HTTP metrics.
         * Prevents excessive metric creation for dynamic service discovery.
         */
        private int maxMonitoredServices = 10;

        /**
         * Enable per-route HTTP connection metrics.
         * When enabled, metrics are collected per HTTP route/endpoint.
         */
        private boolean perRouteMetricsEnabled = false;

        /**
         * Timeout for HTTP connection pool statistics collection (in milliseconds).
         */
        private long statisticsTimeoutMs = 1000;

        /**
         * Enable HTTP connection establishment timing metrics.
         */
        private boolean connectionTimingEnabled = true;

        /**
         * HTTP Request Metrics Configuration
         */
        private HttpRequestMetrics request = new HttpRequestMetrics();
    }

    @Data
    public static class HttpRequestMetrics {
        /**
         * Enable/disable HTTP request metrics collection.
         */
        private boolean enabled = true;

        /**
         * Enable route pattern sanitization to prevent high cardinality.
         */
        private boolean routeSanitizationEnabled = true;

        /**
         * Maximum number of path segments to include in route patterns.
         * Helps prevent metric explosion from deeply nested paths.
         */
        private int maxPathSegments = 10;

        /**
         * Enable caching of metric instances to improve performance.
         */
        private boolean metricCachingEnabled = true;

        /**
         * Maximum size of the metric instance cache.
         */
        private int maxCacheSize = 1000;

        /**
         * Enable detailed logging of HTTP request metrics collection.
         */
        private boolean detailedLoggingEnabled = false;
    }

    @Data
    public static class Collection {
        /**
         * Global collection interval for all metrics in seconds.
         * Individual metric types can override this setting.
         */
        private Duration interval = Duration.ofSeconds(30);

        /**
         * Enable batch collection of metrics to improve performance.
         */
        private boolean batchEnabled = true;

        /**
         * Batch size for metric collection operations.
         */
        private int batchSize = 100;

        /**
         * Timeout for metric collection operations (in milliseconds).
         */
        private long timeoutMs = 5000;

        /**
         * Enable asynchronous metric collection to avoid blocking application threads.
         */
        private boolean asyncEnabled = true;

        /**
         * Number of threads for asynchronous metric collection.
         */
        private int asyncThreads = 2;

        /**
         * Enable metric collection only when certain thresholds are met.
         * Helps reduce overhead during low-activity periods.
         */
        private boolean thresholdBasedCollection = false;
    }

    @Data
    public static class Initialization {
        /**
         * Timeout for metrics initialization during application startup (in seconds).
         */
        private Duration timeout = Duration.ofSeconds(30);

        /**
         * Enable retry mechanism for failed metric registrations.
         */
        private boolean retryEnabled = true;

        /**
         * Maximum number of retry attempts for metric registration.
         */
        private int maxRetries = 3;

        /**
         * Delay between retry attempts (in milliseconds).
         */
        private long retryDelayMs = 1000;

        /**
         * Fail application startup if critical metrics cannot be initialized.
         * When false, application continues even if metrics initialization fails.
         */
        private boolean failOnError = false;

        /**
         * Enable validation of metric registration after initialization.
         */
        private boolean validationEnabled = true;

        /**
         * Enable detailed logging during metrics initialization.
         */
        private boolean verboseLogging = false;
    }

    /**
     * Validates the configuration properties and applies defaults where necessary.
     * This method is called after properties are bound to ensure consistency.
     */
    public void validate() {
        // Ensure collection intervals are reasonable
        if (collection.interval.toSeconds() < 1) {
            collection.interval = Duration.ofSeconds(1);
        }
        
        if (database.collectionInterval.toSeconds() < 1) {
            database.collectionInterval = Duration.ofSeconds(1);
        }
        
        if (http.collectionInterval.toSeconds() < 1) {
            http.collectionInterval = Duration.ofSeconds(1);
        }

        // Ensure thresholds are within reasonable bounds
        if (database.minUtilizationForDetailedMetrics < 0.0 || database.minUtilizationForDetailedMetrics > 1.0) {
            database.minUtilizationForDetailedMetrics = 0.1;
        }
        
        if (http.utilizationWarningThreshold < 0.0 || http.utilizationWarningThreshold > 1.0) {
            http.utilizationWarningThreshold = 0.8;
        }

        // Ensure reasonable limits
        if (http.maxMonitoredServices < 1) {
            http.maxMonitoredServices = 1;
        }
        
        if (collection.batchSize < 1) {
            collection.batchSize = 1;
        }
        
        if (collection.asyncThreads < 1) {
            collection.asyncThreads = 1;
        }
        
        if (initialization.maxRetries < 0) {
            initialization.maxRetries = 0;
        }
    }

    /**
     * Checks if any metrics are enabled.
     * 
     * @return true if at least one metric type is enabled
     */
    public boolean isAnyMetricEnabled() {
        return enabled && (database.enabled || http.enabled);
    }

    /**
     * Gets the effective collection interval for database metrics.
     * Falls back to global collection interval if not specifically configured.
     * 
     * @return the collection interval for database metrics
     */
    public Duration getEffectiveDatabaseCollectionInterval() {
        return database.collectionInterval != null ? database.collectionInterval : collection.interval;
    }

    /**
     * Gets the effective collection interval for HTTP metrics.
     * Falls back to global collection interval if not specifically configured.
     * 
     * @return the collection interval for HTTP metrics
     */
    public Duration getEffectiveHttpCollectionInterval() {
        return http.collectionInterval != null ? http.collectionInterval : collection.interval;
    }
}