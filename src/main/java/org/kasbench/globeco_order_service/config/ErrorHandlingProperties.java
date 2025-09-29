package org.kasbench.globeco_order_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for error handling system.
 * Binds properties with prefix 'error.handling'.
 */
@Component
@ConfigurationProperties(prefix = "error.handling")
@Validated
public class ErrorHandlingProperties {

    /**
     * Enable or disable the enhanced error handling system.
     */
    @NotNull
    private Boolean enabled = true;

    /**
     * Enable structured error responses with consistent JSON format.
     */
    @NotNull
    private Boolean structuredResponses = true;

    /**
     * Include Retry-After header in 503 responses.
     */
    @NotNull
    private Boolean includeRetryAfter = true;

    /**
     * Include detailed error information in responses.
     * Should be false in production to avoid information disclosure.
     */
    @NotNull
    private Boolean includeErrorDetails = false;

    /**
     * Enable backward compatibility mode for existing clients.
     */
    @NotNull
    private Boolean backwardCompatibility = true;

    /**
     * Enable feature flag for gradual rollout.
     */
    @NotNull
    private Boolean featureFlagEnabled = true;

    // Getters and setters
    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getStructuredResponses() {
        return structuredResponses;
    }

    public void setStructuredResponses(Boolean structuredResponses) {
        this.structuredResponses = structuredResponses;
    }

    public Boolean getIncludeRetryAfter() {
        return includeRetryAfter;
    }

    public void setIncludeRetryAfter(Boolean includeRetryAfter) {
        this.includeRetryAfter = includeRetryAfter;
    }

    public Boolean getIncludeErrorDetails() {
        return includeErrorDetails;
    }

    public void setIncludeErrorDetails(Boolean includeErrorDetails) {
        this.includeErrorDetails = includeErrorDetails;
    }

    public Boolean getBackwardCompatibility() {
        return backwardCompatibility;
    }

    public void setBackwardCompatibility(Boolean backwardCompatibility) {
        this.backwardCompatibility = backwardCompatibility;
    }

    public Boolean getFeatureFlagEnabled() {
        return featureFlagEnabled;
    }

    public void setFeatureFlagEnabled(Boolean featureFlagEnabled) {
        this.featureFlagEnabled = featureFlagEnabled;
    }
}