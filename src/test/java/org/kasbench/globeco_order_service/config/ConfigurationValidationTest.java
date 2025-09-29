package org.kasbench.globeco_order_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for configuration property validation.
 */
class ConfigurationValidationTest {

    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = validatorFactory.getValidator();

    @Test
    void shouldValidateSystemOverloadPropertiesSuccessfully() {
        SystemOverloadProperties properties = new SystemOverloadProperties();
        
        // Set valid values
        properties.getDetection().setCheckIntervalSeconds(30);
        properties.getDetection().setConsecutiveThreshold(2);
        properties.getThreadPool().setThreshold(0.9);
        properties.getDatabase().setThreshold(0.95);
        properties.getMemory().setThreshold(0.85);
        properties.getRequestRatio().setThreshold(0.9);
        properties.getRetry().setBaseDelaySeconds(60);
        properties.getRetry().setMaxDelaySeconds(300);
        properties.getRetry().setDelayMultiplier(2.0);

        Set<ConstraintViolation<SystemOverloadProperties>> violations = validator.validate(properties);
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFailValidationForInvalidThresholds() {
        SystemOverloadProperties properties = new SystemOverloadProperties();
        
        // Set invalid threshold values (outside 0.0-1.0 range)
        properties.getThreadPool().setThreshold(1.5);
        properties.getDatabase().setThreshold(-0.1);
        properties.getMemory().setThreshold(2.0);
        properties.getRequestRatio().setThreshold(-1.0);

        Set<ConstraintViolation<SystemOverloadProperties>> violations = validator.validate(properties);
        assertThat(violations).hasSize(4);
        
        violations.forEach(violation -> {
            assertThat(violation.getMessage()).containsAnyOf(
                "must be less than or equal to 1",
                "must be greater than or equal to 0"
            );
        });
    }

    @Test
    void shouldFailValidationForInvalidIntervals() {
        SystemOverloadProperties properties = new SystemOverloadProperties();
        
        // Set invalid interval values
        properties.getDetection().setCheckIntervalSeconds(0); // Below minimum
        properties.getDetection().setConsecutiveThreshold(0); // Below minimum
        properties.getRetry().setBaseDelaySeconds(0); // Below minimum
        properties.getRetry().setMaxDelaySeconds(0); // Below minimum

        Set<ConstraintViolation<SystemOverloadProperties>> violations = validator.validate(properties);
        assertThat(violations).hasSize(4);
        
        violations.forEach(violation -> {
            assertThat(violation.getMessage()).contains("must be greater than or equal to 1");
        });
    }

    @Test
    void shouldFailValidationForExcessiveValues() {
        SystemOverloadProperties properties = new SystemOverloadProperties();
        
        // Set values that exceed maximum limits
        properties.getDetection().setCheckIntervalSeconds(500); // Above maximum
        properties.getDetection().setConsecutiveThreshold(15); // Above maximum
        properties.getRetry().setBaseDelaySeconds(4000); // Above maximum
        properties.getRetry().setMaxDelaySeconds(4000); // Above maximum
        properties.getRetry().setDelayMultiplier(15.0); // Above maximum

        Set<ConstraintViolation<SystemOverloadProperties>> violations = validator.validate(properties);
        assertThat(violations).hasSize(5);
        
        violations.forEach(violation -> {
            assertThat(violation.getMessage()).contains("must be less than or equal to");
        });
    }

    @Test
    void shouldValidateErrorHandlingPropertiesSuccessfully() {
        ErrorHandlingProperties properties = new ErrorHandlingProperties();
        
        // Default values should be valid
        Set<ConstraintViolation<ErrorHandlingProperties>> violations = validator.validate(properties);
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFailValidationForNullRequiredFields() {
        ErrorHandlingProperties errorProperties = new ErrorHandlingProperties();
        errorProperties.setEnabled(null);
        errorProperties.setStructuredResponses(null);
        errorProperties.setIncludeRetryAfter(null);
        errorProperties.setIncludeErrorDetails(null);
        errorProperties.setBackwardCompatibility(null);
        errorProperties.setFeatureFlagEnabled(null);

        Set<ConstraintViolation<ErrorHandlingProperties>> violations = validator.validate(errorProperties);
        assertThat(violations).hasSize(6); // All @NotNull fields should fail
        
        violations.forEach(violation -> {
            assertThat(violation.getMessage()).contains("must not be null");
        });
    }

    @Test
    void shouldBindPropertiesFromMapSuccessfully() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("system.overload.detection.enabled", "true");
        properties.put("system.overload.detection.check-interval-seconds", "45");
        properties.put("system.overload.thread-pool.threshold", "0.8");
        properties.put("system.overload.database.threshold", "0.9");
        properties.put("system.overload.retry.base-delay-seconds", "30");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);

        SystemOverloadProperties boundProperties = binder.bind("system.overload", SystemOverloadProperties.class).get();

        assertThat(boundProperties.getDetection().getEnabled()).isTrue();
        assertThat(boundProperties.getDetection().getCheckIntervalSeconds()).isEqualTo(45);
        assertThat(boundProperties.getThreadPool().getThreshold()).isEqualTo(0.8);
        assertThat(boundProperties.getDatabase().getThreshold()).isEqualTo(0.9);
        assertThat(boundProperties.getRetry().getBaseDelaySeconds()).isEqualTo(30);
    }

    @Test
    void shouldFailBindingForInvalidPropertyTypes() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("system.overload.detection.enabled", "invalid-boolean");
        properties.put("system.overload.detection.check-interval-seconds", "not-a-number");
        properties.put("system.overload.thread-pool.threshold", "not-a-double");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);

        assertThatThrownBy(() -> 
            binder.bind("system.overload", SystemOverloadProperties.class).get()
        ).isInstanceOf(BindException.class);
    }

    @Test
    void shouldBindErrorHandlingPropertiesFromMap() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("error.handling.enabled", "false");
        properties.put("error.handling.structured-responses", "false");
        properties.put("error.handling.include-retry-after", "false");
        properties.put("error.handling.include-error-details", "true");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);

        ErrorHandlingProperties boundProperties = binder.bind("error.handling", ErrorHandlingProperties.class).get();

        assertThat(boundProperties.getEnabled()).isFalse();
        assertThat(boundProperties.getStructuredResponses()).isFalse();
        assertThat(boundProperties.getIncludeRetryAfter()).isFalse();
        assertThat(boundProperties.getIncludeErrorDetails()).isTrue();
    }

    @Test
    void shouldHandlePartialPropertyBinding() {
        Map<String, Object> properties = new HashMap<>();
        // Only set some properties, others should use defaults
        properties.put("system.overload.thread-pool.threshold", "0.7");
        properties.put("system.overload.retry.base-delay-seconds", "120");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);

        SystemOverloadProperties boundProperties = binder.bind("system.overload", SystemOverloadProperties.class).get();

        // Custom values
        assertThat(boundProperties.getThreadPool().getThreshold()).isEqualTo(0.7);
        assertThat(boundProperties.getRetry().getBaseDelaySeconds()).isEqualTo(120);

        // Default values
        assertThat(boundProperties.getDetection().getEnabled()).isTrue();
        assertThat(boundProperties.getDatabase().getThreshold()).isEqualTo(0.95);
        assertThat(boundProperties.getMemory().getThreshold()).isEqualTo(0.85);
    }
}