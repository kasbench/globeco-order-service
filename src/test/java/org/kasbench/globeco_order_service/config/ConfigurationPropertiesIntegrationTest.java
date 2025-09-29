package org.kasbench.globeco_order_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for configuration properties binding and validation.
 * This test verifies that the configuration properties are properly bound
 * and that feature flags work correctly.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "error.handling.enabled=true",
    "error.handling.structured-responses=true",
    "error.handling.include-retry-after=true",
    "error.handling.include-error-details=false",
    "error.handling.backward-compatibility=true",
    "error.handling.feature-flag-enabled=true",
    "system.overload.detection.enabled=true",
    "system.overload.detection.check-interval-seconds=30",
    "system.overload.detection.consecutive-threshold=2",
    "system.overload.thread-pool.enabled=true",
    "system.overload.thread-pool.threshold=0.9",
    "system.overload.database.enabled=true",
    "system.overload.database.threshold=0.95",
    "system.overload.memory.enabled=true",
    "system.overload.memory.threshold=0.85",
    "system.overload.request-ratio.enabled=true",
    "system.overload.request-ratio.threshold=0.9",
    "system.overload.retry.base-delay-seconds=60",
    "system.overload.retry.max-delay-seconds=300",
    "system.overload.retry.adaptive-calculation=true",
    "system.overload.retry.delay-multiplier=2.0"
})
class ConfigurationPropertiesIntegrationTest {

    @Autowired
    private ErrorHandlingProperties errorHandlingProperties;

    @Autowired
    private SystemOverloadProperties systemOverloadProperties;

    @Autowired
    private ErrorHandlingConfiguration.ErrorHandlingFeatureFlag errorHandlingFeatureFlag;

    @Autowired
    private ErrorHandlingConfiguration.SystemOverloadFeatureFlag systemOverloadFeatureFlag;

    @Test
    void shouldBindErrorHandlingPropertiesCorrectly() {
        assertThat(errorHandlingProperties).isNotNull();
        assertThat(errorHandlingProperties.getEnabled()).isTrue();
        assertThat(errorHandlingProperties.getStructuredResponses()).isTrue();
        assertThat(errorHandlingProperties.getIncludeRetryAfter()).isTrue();
        assertThat(errorHandlingProperties.getIncludeErrorDetails()).isFalse();
        assertThat(errorHandlingProperties.getBackwardCompatibility()).isTrue();
        assertThat(errorHandlingProperties.getFeatureFlagEnabled()).isTrue();
    }

    @Test
    void shouldBindSystemOverloadPropertiesCorrectly() {
        assertThat(systemOverloadProperties).isNotNull();
        
        // Detection properties
        assertThat(systemOverloadProperties.getDetection().getEnabled()).isTrue();
        assertThat(systemOverloadProperties.getDetection().getCheckIntervalSeconds()).isEqualTo(30);
        assertThat(systemOverloadProperties.getDetection().getConsecutiveThreshold()).isEqualTo(2);

        // Thread pool properties
        assertThat(systemOverloadProperties.getThreadPool().getEnabled()).isTrue();
        assertThat(systemOverloadProperties.getThreadPool().getThreshold()).isEqualTo(0.9);

        // Database properties
        assertThat(systemOverloadProperties.getDatabase().getEnabled()).isTrue();
        assertThat(systemOverloadProperties.getDatabase().getThreshold()).isEqualTo(0.95);

        // Memory properties
        assertThat(systemOverloadProperties.getMemory().getEnabled()).isTrue();
        assertThat(systemOverloadProperties.getMemory().getThreshold()).isEqualTo(0.85);

        // Request ratio properties
        assertThat(systemOverloadProperties.getRequestRatio().getEnabled()).isTrue();
        assertThat(systemOverloadProperties.getRequestRatio().getThreshold()).isEqualTo(0.9);

        // Retry properties
        assertThat(systemOverloadProperties.getRetry().getBaseDelaySeconds()).isEqualTo(60);
        assertThat(systemOverloadProperties.getRetry().getMaxDelaySeconds()).isEqualTo(300);
        assertThat(systemOverloadProperties.getRetry().getAdaptiveCalculation()).isTrue();
        assertThat(systemOverloadProperties.getRetry().getDelayMultiplier()).isEqualTo(2.0);
    }

    @Test
    void shouldCreateFeatureFlagsCorrectly() {
        assertThat(errorHandlingFeatureFlag).isNotNull();
        assertThat(errorHandlingFeatureFlag.isEnabled()).isTrue();
        assertThat(errorHandlingFeatureFlag.isStructuredResponsesEnabled()).isTrue();
        assertThat(errorHandlingFeatureFlag.isRetryAfterEnabled()).isTrue();
        assertThat(errorHandlingFeatureFlag.isErrorDetailsEnabled()).isFalse();
        assertThat(errorHandlingFeatureFlag.isBackwardCompatibilityEnabled()).isTrue();
        assertThat(errorHandlingFeatureFlag.isFeatureFlagEnabled()).isTrue();

        assertThat(systemOverloadFeatureFlag).isNotNull();
        assertThat(systemOverloadFeatureFlag.isEnabled()).isTrue();
        assertThat(systemOverloadFeatureFlag.isThreadPoolMonitoringEnabled()).isTrue();
        assertThat(systemOverloadFeatureFlag.isDatabaseMonitoringEnabled()).isTrue();
        assertThat(systemOverloadFeatureFlag.isMemoryMonitoringEnabled()).isTrue();
        assertThat(systemOverloadFeatureFlag.isRequestRatioMonitoringEnabled()).isTrue();
    }

    @Test
    void shouldProvideAccessToUnderlyingProperties() {
        assertThat(errorHandlingFeatureFlag.getProperties()).isNotNull();
        assertThat(errorHandlingFeatureFlag.getProperties()).isSameAs(errorHandlingProperties);

        assertThat(systemOverloadFeatureFlag.getProperties()).isNotNull();
        assertThat(systemOverloadFeatureFlag.getProperties()).isSameAs(systemOverloadProperties);
    }
}