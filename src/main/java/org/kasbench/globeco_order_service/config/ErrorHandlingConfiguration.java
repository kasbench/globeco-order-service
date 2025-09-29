package org.kasbench.globeco_order_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration class for error handling system.
 * Enables configuration properties and provides conditional bean creation.
 */
@Configuration
@EnableConfigurationProperties({ErrorHandlingProperties.class, SystemOverloadProperties.class})
public class ErrorHandlingConfiguration {

    /**
     * Creates a feature flag bean for error handling system.
     * This allows for conditional enabling/disabling of error handling features.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(
        prefix = "error.handling", 
        name = "enabled", 
        havingValue = "true", 
        matchIfMissing = true
    )
    public ErrorHandlingFeatureFlag errorHandlingFeatureFlag(ErrorHandlingProperties properties) {
        return new ErrorHandlingFeatureFlag(properties);
    }

    /**
     * Creates a disabled feature flag when error handling is disabled.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "error.handling", 
        name = "enabled", 
        havingValue = "false"
    )
    public ErrorHandlingFeatureFlag disabledErrorHandlingFeatureFlag() {
        return new ErrorHandlingFeatureFlag(false);
    }

    /**
     * Creates a feature flag bean for system overload detection.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(
        prefix = "system.overload.detection", 
        name = "enabled", 
        havingValue = "true", 
        matchIfMissing = true
    )
    public SystemOverloadFeatureFlag systemOverloadFeatureFlag(SystemOverloadProperties properties) {
        return new SystemOverloadFeatureFlag(properties);
    }

    /**
     * Creates a disabled feature flag when system overload detection is disabled.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "system.overload.detection", 
        name = "enabled", 
        havingValue = "false"
    )
    public SystemOverloadFeatureFlag disabledSystemOverloadFeatureFlag() {
        return new SystemOverloadFeatureFlag(false);
    }

    /**
     * Feature flag class for error handling functionality.
     */
    public static class ErrorHandlingFeatureFlag {
        private final boolean enabled;
        private final ErrorHandlingProperties properties;

        public ErrorHandlingFeatureFlag(ErrorHandlingProperties properties) {
            this.enabled = true;
            this.properties = properties;
        }

        public ErrorHandlingFeatureFlag(boolean enabled) {
            this.enabled = enabled;
            this.properties = null;
        }

        public boolean isEnabled() {
            return enabled && (properties == null || properties.getEnabled());
        }

        public boolean isStructuredResponsesEnabled() {
            return enabled && properties != null && properties.getStructuredResponses();
        }

        public boolean isRetryAfterEnabled() {
            return enabled && properties != null && properties.getIncludeRetryAfter();
        }

        public boolean isErrorDetailsEnabled() {
            return enabled && properties != null && properties.getIncludeErrorDetails();
        }

        public boolean isBackwardCompatibilityEnabled() {
            return enabled && properties != null && properties.getBackwardCompatibility();
        }

        public boolean isFeatureFlagEnabled() {
            return enabled && properties != null && properties.getFeatureFlagEnabled();
        }

        public ErrorHandlingProperties getProperties() {
            return properties;
        }
    }

    /**
     * Feature flag class for system overload detection functionality.
     */
    public static class SystemOverloadFeatureFlag {
        private final boolean enabled;
        private final SystemOverloadProperties properties;

        public SystemOverloadFeatureFlag(SystemOverloadProperties properties) {
            this.enabled = true;
            this.properties = properties;
        }

        public SystemOverloadFeatureFlag(boolean enabled) {
            this.enabled = enabled;
            this.properties = null;
        }

        public boolean isEnabled() {
            return enabled && (properties == null || properties.getDetection().getEnabled());
        }

        public boolean isThreadPoolMonitoringEnabled() {
            return enabled && properties != null && properties.getThreadPool().getEnabled();
        }

        public boolean isDatabaseMonitoringEnabled() {
            return enabled && properties != null && properties.getDatabase().getEnabled();
        }

        public boolean isMemoryMonitoringEnabled() {
            return enabled && properties != null && properties.getMemory().getEnabled();
        }

        public boolean isRequestRatioMonitoringEnabled() {
            return enabled && properties != null && properties.getRequestRatio().getEnabled();
        }

        public SystemOverloadProperties getProperties() {
            return properties;
        }
    }
}