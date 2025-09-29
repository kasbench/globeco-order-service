package org.kasbench.globeco_order_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ErrorHandlingConfiguration and feature flag functionality.
 */
@SpringBootTest(classes = {ErrorHandlingConfiguration.class, ErrorHandlingProperties.class, SystemOverloadProperties.class})
@EnableConfigurationProperties({ErrorHandlingProperties.class, SystemOverloadProperties.class})
class ErrorHandlingConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldCreateEnabledFeatureFlagsByDefault() {
        ErrorHandlingConfiguration.ErrorHandlingFeatureFlag errorHandlingFlag = 
            applicationContext.getBean(ErrorHandlingConfiguration.ErrorHandlingFeatureFlag.class);
        ErrorHandlingConfiguration.SystemOverloadFeatureFlag systemOverloadFlag = 
            applicationContext.getBean(ErrorHandlingConfiguration.SystemOverloadFeatureFlag.class);

        assertThat(errorHandlingFlag.isEnabled()).isTrue();
        assertThat(errorHandlingFlag.isStructuredResponsesEnabled()).isTrue();
        assertThat(errorHandlingFlag.isRetryAfterEnabled()).isTrue();
        assertThat(errorHandlingFlag.isErrorDetailsEnabled()).isFalse();
        assertThat(errorHandlingFlag.isBackwardCompatibilityEnabled()).isTrue();
        assertThat(errorHandlingFlag.isFeatureFlagEnabled()).isTrue();

        assertThat(systemOverloadFlag.isEnabled()).isTrue();
        assertThat(systemOverloadFlag.isThreadPoolMonitoringEnabled()).isTrue();
        assertThat(systemOverloadFlag.isDatabaseMonitoringEnabled()).isTrue();
        assertThat(systemOverloadFlag.isMemoryMonitoringEnabled()).isTrue();
        assertThat(systemOverloadFlag.isRequestRatioMonitoringEnabled()).isTrue();
    }

    @SpringBootTest(classes = {ErrorHandlingConfiguration.class, ErrorHandlingProperties.class, SystemOverloadProperties.class})
    @EnableConfigurationProperties({ErrorHandlingProperties.class, SystemOverloadProperties.class})
    @TestPropertySource(properties = {
        "error.handling.enabled=false",
        "system.overload.detection.enabled=false"
    })
    static class DisabledConfigurationTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        void shouldCreateDisabledFeatureFlagsWhenDisabled() {
            ErrorHandlingConfiguration.ErrorHandlingFeatureFlag errorHandlingFlag = 
                applicationContext.getBean(ErrorHandlingConfiguration.ErrorHandlingFeatureFlag.class);
            ErrorHandlingConfiguration.SystemOverloadFeatureFlag systemOverloadFlag = 
                applicationContext.getBean(ErrorHandlingConfiguration.SystemOverloadFeatureFlag.class);

            assertThat(errorHandlingFlag.isEnabled()).isFalse();
            assertThat(errorHandlingFlag.isStructuredResponsesEnabled()).isFalse();
            assertThat(errorHandlingFlag.isRetryAfterEnabled()).isFalse();
            assertThat(errorHandlingFlag.isErrorDetailsEnabled()).isFalse();
            assertThat(errorHandlingFlag.isBackwardCompatibilityEnabled()).isFalse();
            assertThat(errorHandlingFlag.isFeatureFlagEnabled()).isFalse();

            assertThat(systemOverloadFlag.isEnabled()).isFalse();
            assertThat(systemOverloadFlag.isThreadPoolMonitoringEnabled()).isFalse();
            assertThat(systemOverloadFlag.isDatabaseMonitoringEnabled()).isFalse();
            assertThat(systemOverloadFlag.isMemoryMonitoringEnabled()).isFalse();
            assertThat(systemOverloadFlag.isRequestRatioMonitoringEnabled()).isFalse();
        }
    }

    @SpringBootTest(classes = {ErrorHandlingConfiguration.class, ErrorHandlingProperties.class, SystemOverloadProperties.class})
    @EnableConfigurationProperties({ErrorHandlingProperties.class, SystemOverloadProperties.class})
    @TestPropertySource(properties = {
        "error.handling.enabled=true",
        "error.handling.structured-responses=false",
        "error.handling.include-retry-after=false",
        "error.handling.include-error-details=true",
        "error.handling.backward-compatibility=false",
        "error.handling.feature-flag-enabled=false",
        "system.overload.detection.enabled=true",
        "system.overload.thread-pool.enabled=false",
        "system.overload.database.enabled=false",
        "system.overload.memory.enabled=false",
        "system.overload.request-ratio.enabled=false"
    })
    static class CustomConfigurationTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        void shouldRespectCustomPropertyValues() {
            ErrorHandlingConfiguration.ErrorHandlingFeatureFlag errorHandlingFlag = 
                applicationContext.getBean(ErrorHandlingConfiguration.ErrorHandlingFeatureFlag.class);
            ErrorHandlingConfiguration.SystemOverloadFeatureFlag systemOverloadFlag = 
                applicationContext.getBean(ErrorHandlingConfiguration.SystemOverloadFeatureFlag.class);

            assertThat(errorHandlingFlag.isEnabled()).isTrue();
            assertThat(errorHandlingFlag.isStructuredResponsesEnabled()).isFalse();
            assertThat(errorHandlingFlag.isRetryAfterEnabled()).isFalse();
            assertThat(errorHandlingFlag.isErrorDetailsEnabled()).isTrue();
            assertThat(errorHandlingFlag.isBackwardCompatibilityEnabled()).isFalse();
            assertThat(errorHandlingFlag.isFeatureFlagEnabled()).isFalse();

            assertThat(systemOverloadFlag.isEnabled()).isTrue();
            assertThat(systemOverloadFlag.isThreadPoolMonitoringEnabled()).isFalse();
            assertThat(systemOverloadFlag.isDatabaseMonitoringEnabled()).isFalse();
            assertThat(systemOverloadFlag.isMemoryMonitoringEnabled()).isFalse();
            assertThat(systemOverloadFlag.isRequestRatioMonitoringEnabled()).isFalse();
        }
    }

    @Test
    void shouldProvideAccessToUnderlyingProperties() {
        ErrorHandlingConfiguration.ErrorHandlingFeatureFlag errorHandlingFlag = 
            applicationContext.getBean(ErrorHandlingConfiguration.ErrorHandlingFeatureFlag.class);
        ErrorHandlingConfiguration.SystemOverloadFeatureFlag systemOverloadFlag = 
            applicationContext.getBean(ErrorHandlingConfiguration.SystemOverloadFeatureFlag.class);

        assertThat(errorHandlingFlag.getProperties()).isNotNull();
        assertThat(errorHandlingFlag.getProperties()).isInstanceOf(ErrorHandlingProperties.class);

        assertThat(systemOverloadFlag.getProperties()).isNotNull();
        assertThat(systemOverloadFlag.getProperties()).isInstanceOf(SystemOverloadProperties.class);
    }

    @Test
    void shouldHandleDisabledFeatureFlagCorrectly() {
        ErrorHandlingConfiguration.ErrorHandlingFeatureFlag disabledFlag = 
            new ErrorHandlingConfiguration.ErrorHandlingFeatureFlag(false);
        ErrorHandlingConfiguration.SystemOverloadFeatureFlag disabledOverloadFlag = 
            new ErrorHandlingConfiguration.SystemOverloadFeatureFlag(false);

        assertThat(disabledFlag.isEnabled()).isFalse();
        assertThat(disabledFlag.isStructuredResponsesEnabled()).isFalse();
        assertThat(disabledFlag.isRetryAfterEnabled()).isFalse();
        assertThat(disabledFlag.isErrorDetailsEnabled()).isFalse();
        assertThat(disabledFlag.isBackwardCompatibilityEnabled()).isFalse();
        assertThat(disabledFlag.isFeatureFlagEnabled()).isFalse();
        assertThat(disabledFlag.getProperties()).isNull();

        assertThat(disabledOverloadFlag.isEnabled()).isFalse();
        assertThat(disabledOverloadFlag.isThreadPoolMonitoringEnabled()).isFalse();
        assertThat(disabledOverloadFlag.isDatabaseMonitoringEnabled()).isFalse();
        assertThat(disabledOverloadFlag.isMemoryMonitoringEnabled()).isFalse();
        assertThat(disabledOverloadFlag.isRequestRatioMonitoringEnabled()).isFalse();
        assertThat(disabledOverloadFlag.getProperties()).isNull();
    }
}