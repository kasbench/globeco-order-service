package org.kasbench.globeco_order_service.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for configuration properties classes.
 * These tests verify the basic functionality of the configuration properties
 * without requiring a full Spring context.
 */
class ConfigurationPropertiesUnitTest {

    @Test
    void shouldCreateErrorHandlingPropertiesWithDefaults() {
        ErrorHandlingProperties properties = new ErrorHandlingProperties();

        assertThat(properties.getEnabled()).isTrue();
        assertThat(properties.getStructuredResponses()).isTrue();
        assertThat(properties.getIncludeRetryAfter()).isTrue();
        assertThat(properties.getIncludeErrorDetails()).isFalse();
        assertThat(properties.getBackwardCompatibility()).isTrue();
        assertThat(properties.getFeatureFlagEnabled()).isTrue();
    }

    @Test
    void shouldAllowErrorHandlingPropertiesModification() {
        ErrorHandlingProperties properties = new ErrorHandlingProperties();

        properties.setEnabled(false);
        properties.setStructuredResponses(false);
        properties.setIncludeRetryAfter(false);
        properties.setIncludeErrorDetails(true);
        properties.setBackwardCompatibility(false);
        properties.setFeatureFlagEnabled(false);

        assertThat(properties.getEnabled()).isFalse();
        assertThat(properties.getStructuredResponses()).isFalse();
        assertThat(properties.getIncludeRetryAfter()).isFalse();
        assertThat(properties.getIncludeErrorDetails()).isTrue();
        assertThat(properties.getBackwardCompatibility()).isFalse();
        assertThat(properties.getFeatureFlagEnabled()).isFalse();
    }

    @Test
    void shouldCreateSystemOverloadPropertiesWithDefaults() {
        SystemOverloadProperties properties = new SystemOverloadProperties();

        // Detection properties
        assertThat(properties.getDetection()).isNotNull();
        assertThat(properties.getDetection().getEnabled()).isTrue();
        assertThat(properties.getDetection().getCheckIntervalSeconds()).isEqualTo(30);
        assertThat(properties.getDetection().getConsecutiveThreshold()).isEqualTo(2);

        // Thread pool properties
        assertThat(properties.getThreadPool()).isNotNull();
        assertThat(properties.getThreadPool().getEnabled()).isTrue();
        assertThat(properties.getThreadPool().getThreshold()).isEqualTo(0.9);

        // Database properties
        assertThat(properties.getDatabase()).isNotNull();
        assertThat(properties.getDatabase().getEnabled()).isTrue();
        assertThat(properties.getDatabase().getThreshold()).isEqualTo(0.95);

        // Memory properties
        assertThat(properties.getMemory()).isNotNull();
        assertThat(properties.getMemory().getEnabled()).isTrue();
        assertThat(properties.getMemory().getThreshold()).isEqualTo(0.85);

        // Request ratio properties
        assertThat(properties.getRequestRatio()).isNotNull();
        assertThat(properties.getRequestRatio().getEnabled()).isTrue();
        assertThat(properties.getRequestRatio().getThreshold()).isEqualTo(0.9);

        // Retry properties
        assertThat(properties.getRetry()).isNotNull();
        assertThat(properties.getRetry().getBaseDelaySeconds()).isEqualTo(60);
        assertThat(properties.getRetry().getMaxDelaySeconds()).isEqualTo(300);
        assertThat(properties.getRetry().getAdaptiveCalculation()).isTrue();
        assertThat(properties.getRetry().getDelayMultiplier()).isEqualTo(2.0);
    }

    @Test
    void shouldAllowSystemOverloadPropertiesModification() {
        SystemOverloadProperties properties = new SystemOverloadProperties();

        // Modify detection properties
        properties.getDetection().setEnabled(false);
        properties.getDetection().setCheckIntervalSeconds(60);
        properties.getDetection().setConsecutiveThreshold(5);

        // Modify thread pool properties
        properties.getThreadPool().setEnabled(false);
        properties.getThreadPool().setThreshold(0.8);

        // Modify database properties
        properties.getDatabase().setEnabled(false);
        properties.getDatabase().setThreshold(0.9);

        // Modify memory properties
        properties.getMemory().setEnabled(false);
        properties.getMemory().setThreshold(0.7);

        // Modify request ratio properties
        properties.getRequestRatio().setEnabled(false);
        properties.getRequestRatio().setThreshold(0.8);

        // Modify retry properties
        properties.getRetry().setBaseDelaySeconds(30);
        properties.getRetry().setMaxDelaySeconds(600);
        properties.getRetry().setAdaptiveCalculation(false);
        properties.getRetry().setDelayMultiplier(3.0);

        // Verify changes
        assertThat(properties.getDetection().getEnabled()).isFalse();
        assertThat(properties.getDetection().getCheckIntervalSeconds()).isEqualTo(60);
        assertThat(properties.getDetection().getConsecutiveThreshold()).isEqualTo(5);
        assertThat(properties.getThreadPool().getEnabled()).isFalse();
        assertThat(properties.getThreadPool().getThreshold()).isEqualTo(0.8);
        assertThat(properties.getDatabase().getEnabled()).isFalse();
        assertThat(properties.getDatabase().getThreshold()).isEqualTo(0.9);
        assertThat(properties.getMemory().getEnabled()).isFalse();
        assertThat(properties.getMemory().getThreshold()).isEqualTo(0.7);
        assertThat(properties.getRequestRatio().getEnabled()).isFalse();
        assertThat(properties.getRequestRatio().getThreshold()).isEqualTo(0.8);
        assertThat(properties.getRetry().getBaseDelaySeconds()).isEqualTo(30);
        assertThat(properties.getRetry().getMaxDelaySeconds()).isEqualTo(600);
        assertThat(properties.getRetry().getAdaptiveCalculation()).isFalse();
        assertThat(properties.getRetry().getDelayMultiplier()).isEqualTo(3.0);
    }

    @Test
    void shouldCreateFeatureFlagsCorrectly() {
        ErrorHandlingProperties errorProperties = new ErrorHandlingProperties();
        SystemOverloadProperties overloadProperties = new SystemOverloadProperties();

        ErrorHandlingConfiguration.ErrorHandlingFeatureFlag errorFlag = 
            new ErrorHandlingConfiguration.ErrorHandlingFeatureFlag(errorProperties);
        ErrorHandlingConfiguration.SystemOverloadFeatureFlag overloadFlag = 
            new ErrorHandlingConfiguration.SystemOverloadFeatureFlag(overloadProperties);

        // Test enabled feature flags
        assertThat(errorFlag.isEnabled()).isTrue();
        assertThat(errorFlag.isStructuredResponsesEnabled()).isTrue();
        assertThat(errorFlag.isRetryAfterEnabled()).isTrue();
        assertThat(errorFlag.isErrorDetailsEnabled()).isFalse();
        assertThat(errorFlag.isBackwardCompatibilityEnabled()).isTrue();
        assertThat(errorFlag.isFeatureFlagEnabled()).isTrue();
        assertThat(errorFlag.getProperties()).isSameAs(errorProperties);

        assertThat(overloadFlag.isEnabled()).isTrue();
        assertThat(overloadFlag.isThreadPoolMonitoringEnabled()).isTrue();
        assertThat(overloadFlag.isDatabaseMonitoringEnabled()).isTrue();
        assertThat(overloadFlag.isMemoryMonitoringEnabled()).isTrue();
        assertThat(overloadFlag.isRequestRatioMonitoringEnabled()).isTrue();
        assertThat(overloadFlag.getProperties()).isSameAs(overloadProperties);
    }

    @Test
    void shouldCreateDisabledFeatureFlagsCorrectly() {
        ErrorHandlingConfiguration.ErrorHandlingFeatureFlag disabledErrorFlag = 
            new ErrorHandlingConfiguration.ErrorHandlingFeatureFlag(false);
        ErrorHandlingConfiguration.SystemOverloadFeatureFlag disabledOverloadFlag = 
            new ErrorHandlingConfiguration.SystemOverloadFeatureFlag(false);

        // Test disabled feature flags
        assertThat(disabledErrorFlag.isEnabled()).isFalse();
        assertThat(disabledErrorFlag.isStructuredResponsesEnabled()).isFalse();
        assertThat(disabledErrorFlag.isRetryAfterEnabled()).isFalse();
        assertThat(disabledErrorFlag.isErrorDetailsEnabled()).isFalse();
        assertThat(disabledErrorFlag.isBackwardCompatibilityEnabled()).isFalse();
        assertThat(disabledErrorFlag.isFeatureFlagEnabled()).isFalse();
        assertThat(disabledErrorFlag.getProperties()).isNull();

        assertThat(disabledOverloadFlag.isEnabled()).isFalse();
        assertThat(disabledOverloadFlag.isThreadPoolMonitoringEnabled()).isFalse();
        assertThat(disabledOverloadFlag.isDatabaseMonitoringEnabled()).isFalse();
        assertThat(disabledOverloadFlag.isMemoryMonitoringEnabled()).isFalse();
        assertThat(disabledOverloadFlag.isRequestRatioMonitoringEnabled()).isFalse();
        assertThat(disabledOverloadFlag.getProperties()).isNull();
    }

    @Test
    void shouldHandlePartiallyDisabledProperties() {
        ErrorHandlingProperties errorProperties = new ErrorHandlingProperties();
        errorProperties.setEnabled(false);

        SystemOverloadProperties overloadProperties = new SystemOverloadProperties();
        overloadProperties.getDetection().setEnabled(false);

        ErrorHandlingConfiguration.ErrorHandlingFeatureFlag errorFlag = 
            new ErrorHandlingConfiguration.ErrorHandlingFeatureFlag(errorProperties);
        ErrorHandlingConfiguration.SystemOverloadFeatureFlag overloadFlag = 
            new ErrorHandlingConfiguration.SystemOverloadFeatureFlag(overloadProperties);

        // When main property is disabled, feature flag should be disabled
        assertThat(errorFlag.isEnabled()).isFalse();
        assertThat(overloadFlag.isEnabled()).isFalse();
    }
}