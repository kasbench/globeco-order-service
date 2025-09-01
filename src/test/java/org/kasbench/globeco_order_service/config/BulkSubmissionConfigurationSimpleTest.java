package org.kasbench.globeco_order_service.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple configuration binding tests that don't require full Spring Boot context.
 * Tests property binding and validation without database dependencies.
 */
@DisplayName("Bulk Submission Configuration Simple Tests")
class BulkSubmissionConfigurationSimpleTest {

    @Test
    @DisplayName("Should bind properties from map source")
    void shouldBindPropertiesFromMapSource() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("bulk.submission.enabled", true);
        properties.put("bulk.submission.trade-service.timeout-ms", 45000);
        properties.put("bulk.submission.trade-service.max-batch-size", 75);
        properties.put("bulk.submission.fallback.enabled", true);
        properties.put("bulk.submission.fallback.max-individual-orders", 8);
        properties.put("bulk.submission.monitoring.success-rate-threshold", 0.90);

        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);

        BulkSubmissionProperties boundProperties = binder.bind("bulk.submission", BulkSubmissionProperties.class)
                .orElseThrow(() -> new RuntimeException("Failed to bind properties"));

        // Verify binding
        assertTrue(boundProperties.isEnabled());
        assertEquals(45000, boundProperties.getTradeService().getTimeoutMs());
        assertEquals(75, boundProperties.getTradeService().getMaxBatchSize());
        assertTrue(boundProperties.getFallback().isEnabled());
        assertEquals(8, boundProperties.getFallback().getMaxIndividualOrders());
        assertEquals(0.90, boundProperties.getMonitoring().getSuccessRateThreshold());
    }

    @Test
    @DisplayName("Should apply validation after binding")
    void shouldApplyValidationAfterBinding() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("bulk.submission.enabled", true);
        properties.put("bulk.submission.trade-service.timeout-ms", 500); // Invalid - too low
        properties.put("bulk.submission.trade-service.max-batch-size", 2000); // Invalid - too high
        properties.put("bulk.submission.trade-service.max-retries", -1); // Invalid - negative

        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);

        BulkSubmissionProperties boundProperties = binder.bind("bulk.submission", BulkSubmissionProperties.class)
                .orElseThrow(() -> new RuntimeException("Failed to bind properties"));

        // Apply validation
        boundProperties.validate();

        // Verify validation corrections
        assertEquals(1000, boundProperties.getTradeService().getTimeoutMs()); // Corrected from 500
        assertEquals(1000, boundProperties.getTradeService().getMaxBatchSize()); // Corrected from 2000
        assertEquals(0, boundProperties.getTradeService().getMaxRetries()); // Corrected from -1
    }

    @Test
    @DisplayName("Should handle missing properties with defaults")
    void shouldHandleMissingPropertiesWithDefaults() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("bulk.submission.enabled", true);
        // Only set enabled, let others use defaults

        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);

        BulkSubmissionProperties boundProperties = binder.bind("bulk.submission", BulkSubmissionProperties.class)
                .orElseThrow(() -> new RuntimeException("Failed to bind properties"));

        // Verify defaults are used
        assertTrue(boundProperties.isEnabled());
        assertEquals(60000, boundProperties.getTradeService().getTimeoutMs()); // Default
        assertEquals(100, boundProperties.getTradeService().getMaxBatchSize()); // Default
        assertFalse(boundProperties.getFallback().isEnabled()); // Default
        assertTrue(boundProperties.getMonitoring().isPerformanceMonitoringEnabled()); // Default
    }

    @Test
    @DisplayName("Should create configuration with bound properties")
    void shouldCreateConfigurationWithBoundProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("bulk.submission.enabled", true);
        properties.put("bulk.submission.trade-service.max-batch-size", 50);

        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);

        BulkSubmissionProperties boundProperties = binder.bind("bulk.submission", BulkSubmissionProperties.class)
                .orElseThrow(() -> new RuntimeException("Failed to bind properties"));

        BulkSubmissionConfiguration configuration = new BulkSubmissionConfiguration(boundProperties);

        // Verify configuration works with bound properties
        assertTrue(configuration.isBulkSubmissionReady());
        assertTrue(configuration.isValidBatchSize(25));
        assertTrue(configuration.isValidBatchSize(50));
        assertFalse(configuration.isValidBatchSize(51));
    }

    @Test
    @DisplayName("Should handle array properties correctly")
    void shouldHandleArrayPropertiesCorrectly() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("bulk.submission.enabled", true);
        properties.put("bulk.submission.fallback.enabled", true);
        properties.put("bulk.submission.fallback.auto-fallback-on-errors", true);
        properties.put("bulk.submission.fallback.fallback-trigger-status-codes", new int[]{500, 502, 503});

        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);

        BulkSubmissionProperties boundProperties = binder.bind("bulk.submission", BulkSubmissionProperties.class)
                .orElseThrow(() -> new RuntimeException("Failed to bind properties"));

        // Verify array binding
        assertTrue(boundProperties.getFallback().isEnabled());
        assertTrue(boundProperties.getFallback().isAutoFallbackOnErrors());
        assertArrayEquals(new int[]{500, 502, 503}, boundProperties.getFallback().getFallbackTriggerStatusCodes());

        // Verify array usage
        assertTrue(boundProperties.shouldTriggerFallback(500));
        assertTrue(boundProperties.shouldTriggerFallback(502));
        assertTrue(boundProperties.shouldTriggerFallback(503));
        assertFalse(boundProperties.shouldTriggerFallback(429)); // Not in array
    }

    @Test
    @DisplayName("Should handle nested object properties correctly")
    void shouldHandleNestedObjectPropertiesCorrectly() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("bulk.submission.enabled", true);
        properties.put("bulk.submission.trade-service.timeout-ms", 30000);
        properties.put("bulk.submission.trade-service.max-batch-size", 80);
        properties.put("bulk.submission.trade-service.exponential-backoff", false);
        properties.put("bulk.submission.monitoring.detailed-logging-enabled", false);
        properties.put("bulk.submission.performance.async-processing-enabled", false);

        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);

        BulkSubmissionProperties boundProperties = binder.bind("bulk.submission", BulkSubmissionProperties.class)
                .orElseThrow(() -> new RuntimeException("Failed to bind properties"));

        // Verify nested object binding
        assertEquals(30000, boundProperties.getTradeService().getTimeoutMs());
        assertEquals(80, boundProperties.getTradeService().getMaxBatchSize());
        assertFalse(boundProperties.getTradeService().isExponentialBackoff());
        assertFalse(boundProperties.getMonitoring().isDetailedLoggingEnabled());
        assertFalse(boundProperties.getPerformance().isAsyncProcessingEnabled());
    }
}