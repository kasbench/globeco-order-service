package org.kasbench.globeco_order_service.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for bulk submission feature flag behavior and configuration-driven decisions.
 * Demonstrates how the configuration can be used to control feature behavior at runtime.
 */
@DisplayName("Bulk Submission Feature Flag Tests")
class BulkSubmissionFeatureFlagTest {

    private BulkSubmissionProperties properties;
    private BulkSubmissionConfiguration configuration;

    @BeforeEach
    void setUp() {
        properties = new BulkSubmissionProperties();
        configuration = new BulkSubmissionConfiguration(properties);
    }

    @Nested
    @DisplayName("Feature Enable/Disable Tests")
    class FeatureEnableDisableTests {

        @Test
        @DisplayName("Should enable bulk submission when feature flag is true")
        void shouldEnableBulkSubmissionWhenFeatureFlagIsTrue() {
            properties.setEnabled(true);
            properties.getTradeService().setMaxBatchSize(50);

            assertTrue(properties.isBulkSubmissionEnabled());
            assertTrue(configuration.isBulkSubmissionReady());
        }

        @Test
        @DisplayName("Should disable bulk submission when feature flag is false")
        void shouldDisableBulkSubmissionWhenFeatureFlagIsFalse() {
            properties.setEnabled(false);
            properties.getTradeService().setMaxBatchSize(50);

            assertFalse(properties.isBulkSubmissionEnabled());
            assertFalse(configuration.isBulkSubmissionReady());
        }

        @Test
        @DisplayName("Should disable bulk submission when batch size is zero")
        void shouldDisableBulkSubmissionWhenBatchSizeIsZero() {
            properties.setEnabled(true);
            properties.getTradeService().setMaxBatchSize(0);

            assertFalse(properties.isBulkSubmissionEnabled());
            assertFalse(configuration.isBulkSubmissionReady());
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 10, 50, 100, 500})
        @DisplayName("Should enable bulk submission for valid batch sizes")
        void shouldEnableBulkSubmissionForValidBatchSizes(int batchSize) {
            properties.setEnabled(true);
            properties.getTradeService().setMaxBatchSize(batchSize);

            assertTrue(properties.isBulkSubmissionEnabled());
            assertTrue(configuration.isBulkSubmissionReady());
        }
    }

    @Nested
    @DisplayName("Fallback Feature Flag Tests")
    class FallbackFeatureFlagTests {

        @Test
        @DisplayName("Should enable fallback when feature flag is true and max orders > 0")
        void shouldEnableFallbackWhenFeatureFlagIsTrueAndMaxOrdersGreaterThanZero() {
            properties.getFallback().setEnabled(true);
            properties.getFallback().setMaxIndividualOrders(5);

            assertTrue(properties.isFallbackEnabled());
        }

        @Test
        @DisplayName("Should disable fallback when feature flag is false")
        void shouldDisableFallbackWhenFeatureFlagIsFalse() {
            properties.getFallback().setEnabled(false);
            properties.getFallback().setMaxIndividualOrders(5);

            assertFalse(properties.isFallbackEnabled());
        }

        @Test
        @DisplayName("Should disable fallback when max individual orders is zero")
        void shouldDisableFallbackWhenMaxIndividualOrdersIsZero() {
            properties.getFallback().setEnabled(true);
            properties.getFallback().setMaxIndividualOrders(0);

            assertFalse(properties.isFallbackEnabled());
        }

        @ParameterizedTest
        @CsvSource({
            "true, 5, true",
            "true, 0, false",
            "false, 5, false",
            "false, 0, false"
        })
        @DisplayName("Should determine fallback status based on flag and max orders")
        void shouldDetermineFallbackStatusBasedOnFlagAndMaxOrders(boolean enabled, int maxOrders, boolean expectedResult) {
            properties.getFallback().setEnabled(enabled);
            properties.getFallback().setMaxIndividualOrders(maxOrders);

            assertEquals(expectedResult, properties.isFallbackEnabled());
        }
    }

    @Nested
    @DisplayName("Monitoring Feature Flag Tests")
    class MonitoringFeatureFlagTests {

        @Test
        @DisplayName("Should enable performance monitoring when any monitoring flag is true")
        void shouldEnablePerformanceMonitoringWhenAnyMonitoringFlagIsTrue() {
            // All monitoring disabled
            properties.getMonitoring().setPerformanceMonitoringEnabled(false);
            properties.getMonitoring().setSuccessRateMonitoringEnabled(false);
            properties.getMonitoring().setLatencyMonitoringEnabled(false);
            properties.getMonitoring().setErrorRateMonitoringEnabled(false);
            assertFalse(properties.isPerformanceMonitoringEnabled());

            // Enable performance monitoring
            properties.getMonitoring().setPerformanceMonitoringEnabled(true);
            assertTrue(properties.isPerformanceMonitoringEnabled());

            // Disable performance, enable success rate
            properties.getMonitoring().setPerformanceMonitoringEnabled(false);
            properties.getMonitoring().setSuccessRateMonitoringEnabled(true);
            assertTrue(properties.isPerformanceMonitoringEnabled());

            // Disable success rate, enable latency
            properties.getMonitoring().setSuccessRateMonitoringEnabled(false);
            properties.getMonitoring().setLatencyMonitoringEnabled(true);
            assertTrue(properties.isPerformanceMonitoringEnabled());

            // Disable latency, enable error rate
            properties.getMonitoring().setLatencyMonitoringEnabled(false);
            properties.getMonitoring().setErrorRateMonitoringEnabled(true);
            assertTrue(properties.isPerformanceMonitoringEnabled());
        }

        @Test
        @DisplayName("Should control detailed logging through feature flag")
        void shouldControlDetailedLoggingThroughFeatureFlag() {
            properties.getMonitoring().setDetailedLoggingEnabled(true);
            assertTrue(properties.isDetailedLoggingEnabled());

            properties.getMonitoring().setDetailedLoggingEnabled(false);
            assertFalse(properties.isDetailedLoggingEnabled());
        }
    }

    @Nested
    @DisplayName("Batch Size Control Tests")
    class BatchSizeControlTests {

        @Test
        @DisplayName("Should respect configured maximum batch size")
        void shouldRespectConfiguredMaximumBatchSize() {
            properties.getTradeService().setMaxBatchSize(50);

            assertTrue(configuration.isValidBatchSize(25));
            assertTrue(configuration.isValidBatchSize(50));
            assertFalse(configuration.isValidBatchSize(51));
            assertFalse(configuration.isValidBatchSize(100));
        }

        @Test
        @DisplayName("Should recommend batch size within configured limits")
        void shouldRecommendBatchSizeWithinConfiguredLimits() {
            properties.getTradeService().setMaxBatchSize(30);

            assertEquals(25, configuration.getRecommendedBatchSize(25));
            assertEquals(30, configuration.getRecommendedBatchSize(30));
            assertEquals(30, configuration.getRecommendedBatchSize(50)); // Capped at max
        }

        @Test
        @DisplayName("Should apply memory-based batch size limits when enabled")
        void shouldApplyMemoryBasedBatchSizeLimitsWhenEnabled() {
            properties.getTradeService().setMaxBatchSize(1000);
            properties.getPerformance().setMemoryOptimizationEnabled(true);
            properties.getPerformance().setMaxMemoryUsageMb(1); // Very low memory limit

            int effectiveSize = properties.getEffectiveMaxBatchSize();
            assertTrue(effectiveSize >= 1, "Effective size should be at least 1, but was: " + effectiveSize);
            assertTrue(effectiveSize <= 1000, "Effective size should not exceed configured max, but was: " + effectiveSize);
            
            // With 1MB limit and 1KB per order estimate, should allow around 1024 orders
            // But since configured max is 1000, it should be 1000
            // Let's test with a smaller memory limit to force reduction
            properties.getPerformance().setMaxMemoryUsageMb(0); // Essentially no memory
            int veryLimitedSize = properties.getEffectiveMaxBatchSize();
            assertEquals(1, veryLimitedSize); // Should be minimum of 1
        }

        @Test
        @DisplayName("Should ignore memory limits when memory optimization is disabled")
        void shouldIgnoreMemoryLimitsWhenMemoryOptimizationIsDisabled() {
            properties.getTradeService().setMaxBatchSize(100);
            properties.getPerformance().setMemoryOptimizationEnabled(false);
            properties.getPerformance().setMaxMemoryUsageMb(1); // Very low memory limit

            assertEquals(100, properties.getEffectiveMaxBatchSize()); // Should ignore memory limit
        }
    }

    @Nested
    @DisplayName("Timeout Control Tests")
    class TimeoutControlTests {

        @Test
        @DisplayName("Should calculate total timeout based on retry configuration")
        void shouldCalculateTotalTimeoutBasedOnRetryConfiguration() {
            properties.getTradeService().setTimeoutMs(10000);
            properties.getTradeService().setMaxRetries(0);
            properties.getTradeService().setRetryDelayMs(1000);

            assertEquals(10000, properties.getEffectiveTotalTimeoutMs()); // No retries

            properties.getTradeService().setMaxRetries(2);
            properties.getTradeService().setExponentialBackoff(false);
            assertEquals(12000, properties.getEffectiveTotalTimeoutMs()); // 10000 + 2*1000

            properties.getTradeService().setExponentialBackoff(true);
            assertEquals(13000, properties.getEffectiveTotalTimeoutMs()); // 10000 + 1000*1 + 1000*2
        }

        @Test
        @DisplayName("Should control retry behavior through configuration")
        void shouldControlRetryBehaviorThroughConfiguration() {
            properties.getTradeService().setRetryDelayMs(1000);

            // Without exponential backoff
            properties.getTradeService().setExponentialBackoff(false);
            assertEquals(1000, properties.getRetryDelayMs(0));
            assertEquals(1000, properties.getRetryDelayMs(1));
            assertEquals(1000, properties.getRetryDelayMs(2));

            // With exponential backoff
            properties.getTradeService().setExponentialBackoff(true);
            assertEquals(1000, properties.getRetryDelayMs(0)); // 1000 * 2^0
            assertEquals(2000, properties.getRetryDelayMs(1)); // 1000 * 2^1
            assertEquals(4000, properties.getRetryDelayMs(2)); // 1000 * 2^2
        }
    }

    @Nested
    @DisplayName("Error Handling Control Tests")
    class ErrorHandlingControlTests {

        @Test
        @DisplayName("Should control fallback triggers through configuration")
        void shouldControlFallbackTriggersThroughConfiguration() {
            properties.getFallback().setAutoFallbackOnErrors(true);
            properties.getFallback().setFallbackTriggerStatusCodes(new int[]{503, 429});

            assertTrue(properties.shouldTriggerFallback(503));
            assertTrue(properties.shouldTriggerFallback(429));
            assertFalse(properties.shouldTriggerFallback(500));
            assertFalse(properties.shouldTriggerFallback(502));

            // Disable auto fallback
            properties.getFallback().setAutoFallbackOnErrors(false);
            assertFalse(properties.shouldTriggerFallback(503));
            assertFalse(properties.shouldTriggerFallback(429));
        }

        @Test
        @DisplayName("Should allow custom fallback trigger codes")
        void shouldAllowCustomFallbackTriggerCodes() {
            properties.getFallback().setAutoFallbackOnErrors(true);
            properties.getFallback().setFallbackTriggerStatusCodes(new int[]{500, 502, 504});

            assertTrue(properties.shouldTriggerFallback(500));
            assertTrue(properties.shouldTriggerFallback(502));
            assertTrue(properties.shouldTriggerFallback(504));
            assertFalse(properties.shouldTriggerFallback(503)); // Not in custom list
            assertFalse(properties.shouldTriggerFallback(429)); // Not in custom list
        }

        @Test
        @DisplayName("Should handle empty fallback trigger codes")
        void shouldHandleEmptyFallbackTriggerCodes() {
            properties.getFallback().setAutoFallbackOnErrors(true);
            properties.getFallback().setFallbackTriggerStatusCodes(new int[]{});

            assertFalse(properties.shouldTriggerFallback(503));
            assertFalse(properties.shouldTriggerFallback(429));
            assertFalse(properties.shouldTriggerFallback(500));
        }
    }

    @Nested
    @DisplayName("Runtime Configuration Changes Tests")
    class RuntimeConfigurationChangesTests {

        @Test
        @DisplayName("Should reflect configuration changes immediately")
        void shouldReflectConfigurationChangesImmediately() {
            // Initial state
            properties.setEnabled(true);
            properties.getTradeService().setMaxBatchSize(100);
            assertTrue(properties.isBulkSubmissionEnabled());

            // Change configuration
            properties.setEnabled(false);
            assertFalse(properties.isBulkSubmissionEnabled());

            // Change back
            properties.setEnabled(true);
            assertTrue(properties.isBulkSubmissionEnabled());

            // Change batch size
            properties.getTradeService().setMaxBatchSize(0);
            assertFalse(properties.isBulkSubmissionEnabled());
        }

        @Test
        @DisplayName("Should validate configuration after changes")
        void shouldValidateConfigurationAfterChanges() {
            // Set invalid values
            properties.getTradeService().setTimeoutMs(100); // Too low
            properties.getTradeService().setMaxBatchSize(2000); // Too high

            // Validate should correct them
            properties.validate();

            assertEquals(1000, properties.getTradeService().getTimeoutMs());
            assertEquals(1000, properties.getTradeService().getMaxBatchSize());
        }
    }
}