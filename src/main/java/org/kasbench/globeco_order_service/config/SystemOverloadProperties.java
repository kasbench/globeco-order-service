package org.kasbench.globeco_order_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for system overload detection.
 * Binds properties with prefix 'system.overload'.
 */
@ConfigurationProperties(prefix = "system.overload")
@Validated
public class SystemOverloadProperties {

    /**
     * Detection configuration for overload monitoring.
     */
    private Detection detection = new Detection();

    /**
     * Thread pool monitoring configuration.
     */
    private ThreadPool threadPool = new ThreadPool();

    /**
     * Database connection pool monitoring configuration.
     */
    private Database database = new Database();

    /**
     * Memory monitoring configuration.
     */
    private Memory memory = new Memory();

    /**
     * Request ratio monitoring configuration.
     */
    private RequestRatio requestRatio = new RequestRatio();

    /**
     * Retry configuration for overload responses.
     */
    private Retry retry = new Retry();

    public static class Detection {
        /**
         * Enable or disable system overload detection.
         */
        @NotNull
        private Boolean enabled = true;

        /**
         * Interval in seconds for checking system overload.
         */
        @Min(1)
        @Max(300)
        private Integer checkIntervalSeconds = 30;

        /**
         * Number of consecutive overload detections before triggering overload state.
         */
        @Min(1)
        @Max(10)
        private Integer consecutiveThreshold = 2;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getCheckIntervalSeconds() {
            return checkIntervalSeconds;
        }

        public void setCheckIntervalSeconds(Integer checkIntervalSeconds) {
            this.checkIntervalSeconds = checkIntervalSeconds;
        }

        public Integer getConsecutiveThreshold() {
            return consecutiveThreshold;
        }

        public void setConsecutiveThreshold(Integer consecutiveThreshold) {
            this.consecutiveThreshold = consecutiveThreshold;
        }
    }

    public static class ThreadPool {
        /**
         * Thread pool utilization threshold (0.0 to 1.0).
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private Double threshold = 0.9;

        /**
         * Enable thread pool monitoring.
         */
        @NotNull
        private Boolean enabled = true;

        public Double getThreshold() {
            return threshold;
        }

        public void setThreshold(Double threshold) {
            this.threshold = threshold;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Database {
        /**
         * Database connection pool utilization threshold (0.0 to 1.0).
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private Double threshold = 0.95;

        /**
         * Enable database connection pool monitoring.
         */
        @NotNull
        private Boolean enabled = true;

        public Double getThreshold() {
            return threshold;
        }

        public void setThreshold(Double threshold) {
            this.threshold = threshold;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Memory {
        /**
         * Memory utilization threshold (0.0 to 1.0).
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private Double threshold = 0.85;

        /**
         * Enable memory monitoring.
         */
        @NotNull
        private Boolean enabled = true;

        public Double getThreshold() {
            return threshold;
        }

        public void setThreshold(Double threshold) {
            this.threshold = threshold;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class RequestRatio {
        /**
         * Active request ratio threshold (0.0 to 1.0).
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private Double threshold = 0.9;

        /**
         * Enable request ratio monitoring.
         */
        @NotNull
        private Boolean enabled = true;

        public Double getThreshold() {
            return threshold;
        }

        public void setThreshold(Double threshold) {
            this.threshold = threshold;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Retry {
        /**
         * Base delay in seconds for retry recommendations.
         */
        @Min(1)
        @Max(3600)
        private Integer baseDelaySeconds = 60;

        /**
         * Maximum delay in seconds for retry recommendations.
         */
        @Min(1)
        @Max(3600)
        private Integer maxDelaySeconds = 300;

        /**
         * Enable adaptive retry delay calculation based on system load.
         */
        @NotNull
        private Boolean adaptiveCalculation = true;

        /**
         * Multiplier for retry delay calculation.
         */
        @DecimalMin("1.0")
        @DecimalMax("10.0")
        private Double delayMultiplier = 2.0;

        public Integer getBaseDelaySeconds() {
            return baseDelaySeconds;
        }

        public void setBaseDelaySeconds(Integer baseDelaySeconds) {
            this.baseDelaySeconds = baseDelaySeconds;
        }

        public Integer getMaxDelaySeconds() {
            return maxDelaySeconds;
        }

        public void setMaxDelaySeconds(Integer maxDelaySeconds) {
            this.maxDelaySeconds = maxDelaySeconds;
        }

        public Boolean getAdaptiveCalculation() {
            return adaptiveCalculation;
        }

        public void setAdaptiveCalculation(Boolean adaptiveCalculation) {
            this.adaptiveCalculation = adaptiveCalculation;
        }

        public Double getDelayMultiplier() {
            return delayMultiplier;
        }

        public void setDelayMultiplier(Double delayMultiplier) {
            this.delayMultiplier = delayMultiplier;
        }
    }

    // Main class getters and setters
    public Detection getDetection() {
        return detection;
    }

    public void setDetection(Detection detection) {
        this.detection = detection;
    }

    public ThreadPool getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public RequestRatio getRequestRatio() {
        return requestRatio;
    }

    public void setRequestRatio(RequestRatio requestRatio) {
        this.requestRatio = requestRatio;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }
}