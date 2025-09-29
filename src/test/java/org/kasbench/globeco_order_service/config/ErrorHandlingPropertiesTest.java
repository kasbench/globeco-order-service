package org.kasbench.globeco_order_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ErrorHandlingProperties configuration binding and validation.
 */
@SpringBootTest(classes = {ErrorHandlingProperties.class})
@EnableConfigurationProperties(ErrorHandlingProperties.class)
class ErrorHandlingPropertiesTest {

    @Autowired
    private ErrorHandlingProperties properties;

    @Test
    void shouldBindDefaultProperties() {
        assertThat(properties.getEnabled()).isTrue();
        assertThat(properties.getStructuredResponses()).isTrue();
        assertThat(properties.getIncludeRetryAfter()).isTrue();
        assertThat(properties.getIncludeErrorDetails()).isFalse();
        assertThat(properties.getBackwardCompatibility()).isTrue();
        assertThat(properties.getFeatureFlagEnabled()).isTrue();
    }

    @SpringBootTest(classes = {ErrorHandlingProperties.class})
    @EnableConfigurationProperties(ErrorHandlingProperties.class)
    @TestPropertySource(properties = {
        "error.handling.enabled=false",
        "error.handling.structured-responses=false",
        "error.handling.include-retry-after=false",
        "error.handling.include-error-details=true",
        "error.handling.backward-compatibility=false",
        "error.handling.feature-flag-enabled=false"
    })
    static class CustomPropertiesTest {

        @Autowired
        private ErrorHandlingProperties properties;

        @Test
        void shouldBindCustomProperties() {
            assertThat(properties.getEnabled()).isFalse();
            assertThat(properties.getStructuredResponses()).isFalse();
            assertThat(properties.getIncludeRetryAfter()).isFalse();
            assertThat(properties.getIncludeErrorDetails()).isTrue();
            assertThat(properties.getBackwardCompatibility()).isFalse();
            assertThat(properties.getFeatureFlagEnabled()).isFalse();
        }
    }

    @Test
    void shouldAllowPropertyModification() {
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
    void shouldHandleNullValues() {
        properties.setEnabled(null);
        properties.setStructuredResponses(null);
        properties.setIncludeRetryAfter(null);
        properties.setIncludeErrorDetails(null);
        properties.setBackwardCompatibility(null);
        properties.setFeatureFlagEnabled(null);

        assertThat(properties.getEnabled()).isNull();
        assertThat(properties.getStructuredResponses()).isNull();
        assertThat(properties.getIncludeRetryAfter()).isNull();
        assertThat(properties.getIncludeErrorDetails()).isNull();
        assertThat(properties.getBackwardCompatibility()).isNull();
        assertThat(properties.getFeatureFlagEnabled()).isNull();
    }
}