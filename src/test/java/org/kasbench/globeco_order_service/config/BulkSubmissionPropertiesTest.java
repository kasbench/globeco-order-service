package org.kasbench.globeco_order_service.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BulkSubmissionProperties configuration class.
 * Tests configuration validation, property binding, and business logic methods.
 */
@DisplayName("BulkSubmissionProperties Tests")
class BulkSubmissionPropertiesTest {

    private BulkSubmissionProperties properties;

    @BeforeEach
    void setUp() {
        properties = new BulkSubmissionProperties();
    }

    @Nested
    @DisplayName("Default Configuration Tests")
    class DefaultConfigurationTests {

        @Test
        @DisplayName("Should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            // Main configuration
            assertTrue(properties.isEnabled());
            
            // Trade service defaults
            assertEquals(60000, properties.getTradeService().getTimeoutMs());
            assertEquals(100, properties.getTradeService().getMaxBatchSize());
            assertEquals(10000, properties.getTradeService().getConnectionTimeoutMs());
            assertEquals(60000, properties.getTradeService().getReadTimeoutMs());
            assertEquals(2, properties.getTradeService().getMaxRetries());
            assertEquals(1000, properties.getTradeService().getRetryDelayMs());
            assertTrue(properties.getTradeService().isExponentialBackoff());
            
            // Fallback defaults
            assertFalse(properties.getFallback().isEnabled());
            assertEquals(10, properties.getFallback().getMaxIndividualOrders());
            assertEquals(30000, properties.getFallback().getIndividualTimeoutMs());
            assertTrue(properties.getFallback().isAutoFallbackOnErrors());
            assertArrayEquals(new int[]{503, 429, 502, 504}, properties.getFallback().getFallbackTriggerStatusCodes());
            
            // Monitoring defaults
            assertTrue(properties.getMonitoring().isPerformanceMonitoringEnabled());
            assertTrue(properties.getMonitoring().isSuccessRateMonitoringEnabled());
            assertEquals(0.95, properties.getMonitoring().getSuccessRateThreshold());
            assertEquals(15, properties.getMonitoring().getSuccessRateWindowMinutes());
            assertTrue(properties.getMonitoring().isLatencyMonitoringEnabled());
            assertEquals(30000, properties.getMonitoring().getMaxAcceptableLatencyMs());
            assertTrue(properties.getMonitoring().isErrorRateMonitoringEnabled());
            assertEquals(0.05, properties.getMonitoring().getMaxErrorRate());
            assertTrue(properties.getMonitoring().isDetailedLoggingEnabled());
            assertTrue(properties.getMonitoring().isLogPerformanceMetrics());
            
            // Performance defaults
            assertTrue(properties.getPerformance().isDbPoolOptimizationEnabled());
            assertEquals(5, properties.getPerformance().getMaxDbConnections());
            assertTrue(properties.getPerformance().isBatchDbUpdatesEnabled());
            assertEquals(50, properties.getPerformance().getDbUpdateBatchSize());
            assertTrue(properties.getPerformance().isAsyncProcessingEnabled());
            assertEquals(3, properties.getPerformance().getAsyncThreadPoolSize());
            assertTrue(properties.getPerformance().isMemoryOptimizationEnabled());
            assertEquals(256, properties.getPerformance().getMaxMemoryUsageMb());
        }

        @Test
        @DisplayName("Should indicate bulk submission is enabled by default")
        void shouldIndicateBulkSubmissionIsEnabledByDefault() {
            assertTrue(properties.isBulkSubmissionEnabled());
        }

        @Test
        @DisplayName("Should indicate fallback is disabled by default")
        void shouldIndicateFallbackIsDisabledByDefault() {
            assertFalse(properties.isFallbackEnabled());
        }

        @Test
        @DisplayName("Should indicate performance monitoring is enabled by default")
        void shouldIndicatePerformanceMonitoringIsEnabledByDefault() {
            assertTrue(properties.isPerformanceMonitoringEnabled());
        }

        @Test
        @DisplayName("Should indicate detailed logging is enabled by default")
        void shouldIndicateDetailedLoggingIsEnabledByDefault() {
            assertTrue(properties.isDetailedLoggingEnabled());
        }
    }

    @Nested
    @DisplayName("Configuration Validation Tests")
    class ConfigurationValidationTests {

        @Test
        @DisplayName("Should validate and correct invalid timeout values")
        void shouldValidateAndCorrectInvalidTimeoutValues() {
            // Set invalid timeout values
            properties.getTradeService().setTimeoutMs(500); // Too low
            properties.getTradeService().setConnectionTimeoutMs(500); // Too low
            properties.getTradeService().setReadTimeoutMs(100); // Lower than timeout
            
            properties.validate();
            
            // Should be corrected to minimum values
            assertEquals(1000, properties.getTradeService().getTimeoutMs());
            assertEquals(1000, properties.getTradeService().getConnectionTimeoutMs());
            assertEquals(1000, properties.getTradeService().getReadTimeoutMs()); // Should match timeout
        }

        @Test
        @DisplayName("Should validate and correct invalid batch size values")
        void shouldValidateAndCorrectInvalidBatchSizeValues() {
            // Set invalid batch size values
            properties.getTradeService().setMaxBatchSize(0); // Too low
            
            properties.validate();
            
            assertEquals(1, properties.getTradeService().getMaxBatchSize());
            
            // Test upper limit
            properties.getTradeService().setMaxBatchSize(2000); // Too high
            properties.validate();
            
            assertEquals(1000, properties.getTradeService().getMaxBatchSize());
        }

        @Test
        @DisplayName("Should validate and correct invalid retry configuration")
        void shouldValidateAndCorrectInvalidRetryConfiguration() {
            // Set invalid retry values
            properties.getTradeService().setMaxRetries(-1); // Negative
            
            properties.validate();
            
            assertEquals(0, properties.getTradeService().getMaxRetries());
            
            // Test upper limit
            properties.getTradeService().setMaxRetries(10); // Too high
            properties.validate();
            
            assertEquals(5, properties.getTradeService().getMaxRetries());
        }

        @Test
        @DisplayName("Should validate and correct invalid fallback configuration")
        void shouldValidateAndCorrectInvalidFallbackConfiguration() {
            // Set invalid fallback values
            properties.getFallback().setMaxIndividualOrders(-1); // Negative
            
            properties.validate();
            
            assertEquals(0, properties.getFallback().getMaxIndividualOrders());
            
            // Test upper limit relative to batch size
            properties.getTradeService().setMaxBatchSize(50);
            properties.getFallback().setMaxIndividualOrders(100); // Higher than batch size
            
            properties.validate();
            
            assertEquals(50, properties.getFallback().getMaxIndividualOrders());
        }

        @Test
        @DisplayName("Should validate and correct invalid monitoring thresholds")
        void shouldValidateAndCorrectInvalidMonitoringThresholds() {
            // Set invalid threshold values
            properties.getMonitoring().setSuccessRateThreshold(-0.1); // Below 0
            properties.getMonitoring().setMaxErrorRate(1.5); // Above 1
            
            properties.validate();
            
            assertEquals(0.95, properties.getMonitoring().getSuccessRateThreshold());
            assertEquals(0.05, properties.getMonitoring().getMaxErrorRate());
            
            // Test upper bounds
            properties.getMonitoring().setSuccessRateThreshold(1.5); // Above 1
            properties.getMonitoring().setMaxErrorRate(-0.1); // Below 0
            
            properties.validate();
            
            assertEquals(0.95, properties.getMonitoring().getSuccessRateThreshold());
            assertEquals(0.05, properties.getMonitoring().getMaxErrorRate());
        }

        @Test
        @DisplayName("Should validate and correct invalid performance configuration")
        void shouldValidateAndCorrectInvalidPerformanceConfiguration() {
            // Set invalid performance values
            properties.getPerformance().setMaxDbConnections(0); // Too low
            properties.getPerformance().setDbUpdateBatchSize(0); // Too low
            properties.getPerformance().setAsyncThreadPoolSize(0); // Too low
            
            properties.validate();
            
            assertEquals(1, properties.getPerformance().getMaxDbConnections());
            assertEquals(1, properties.getPerformance().getDbUpdateBatchSize());
            assertEquals(1, properties.getPerformance().getAsyncThreadPoolSize());
        }
    }

    @Nested
    @DisplayName("Business Logic Tests")
    class BusinessLogicTests {

        @Test
        @DisplayName("Should correctly determine if bulk submission is enabled")
        void shouldCorrectlyDetermineIfBulkSubmissionIsEnabled() {
            // Enabled with valid batch size
            properties.setEnabled(true);
            properties.getTradeService().setMaxBatchSize(10);
            assertTrue(properties.isBulkSubmissionEnabled());
            
            // Disabled
            properties.setEnabled(false);
            assertFalse(properties.isBulkSubmissionEnabled());
            
            // Enabled but zero batch size
            properties.setEnabled(true);
            properties.getTradeService().setMaxBatchSize(0);
            assertFalse(properties.isBulkSubmissionEnabled());
        }

        @Test
        @DisplayName("Should correctly determine if fallback is enabled")
        void shouldCorrectlyDetermineIfFallbackIsEnabled() {
            // Enabled with valid max orders
            properties.getFallback().setEnabled(true);
            properties.getFallback().setMaxIndividualOrders(5);
            assertTrue(properties.isFallbackEnabled());
            
            // Disabled
            properties.getFallback().setEnabled(false);
            assertFalse(properties.isFallbackEnabled());
            
            // Enabled but zero max orders
            properties.getFallback().setEnabled(true);
            properties.getFallback().setMaxIndividualOrders(0);
            assertFalse(properties.isFallbackEnabled());
        }

        @Test
        @DisplayName("Should correctly determine if status code should trigger fallback")
        void shouldCorrectlyDetermineIfStatusCodeShouldTriggerFallback() {
            properties.getFallback().setAutoFallbackOnErrors(true);
            
            // Test configured trigger codes
            assertTrue(properties.shouldTriggerFallback(503));
            assertTrue(properties.shouldTriggerFallback(429));
            assertTrue(properties.shouldTriggerFallback(502));
            assertTrue(properties.shouldTriggerFallback(504));
            
            // Test non-trigger codes
            assertFalse(properties.shouldTriggerFallback(200));
            assertFalse(properties.shouldTriggerFallback(400));
            assertFalse(properties.shouldTriggerFallback(500));
            
            // Test when auto fallback is disabled
            properties.getFallback().setAutoFallbackOnErrors(false);
            assertFalse(properties.shouldTriggerFallback(503));
        }

        @Test
        @DisplayName("Should calculate effective total timeout correctly")
        void shouldCalculateEffectiveTotalTimeoutCorrectly() {
            properties.getTradeService().setTimeoutMs(10000);
            properties.getTradeService().setMaxRetries(2);
            properties.getTradeService().setRetryDelayMs(1000);
            
            // Without exponential backoff
            properties.getTradeService().setExponentialBackoff(false);
            long expectedTimeout = 10000 + (2 * 1000); // base + (retries * delay)
            assertEquals(expectedTimeout, properties.getEffectiveTotalTimeoutMs());
            
            // With exponential backoff
            properties.getTradeService().setExponentialBackoff(true);
            long expectedExponentialTimeout = 10000 + (1000 * 1) + (1000 * 2); // base + delay*2^0 + delay*2^1
            assertEquals(expectedExponentialTimeout, properties.getEffectiveTotalTimeoutMs());
        }

        @Test
        @DisplayName("Should calculate retry delay correctly")
        void shouldCalculateRetryDelayCorrectly() {
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

        @Test
        @DisplayName("Should determine performance monitoring status correctly")
        void shouldDeterminePerformanceMonitoringStatusCorrectly() {
            // All monitoring disabled
            properties.getMonitoring().setPerformanceMonitoringEnabled(false);
            properties.getMonitoring().setSuccessRateMonitoringEnabled(false);
            properties.getMonitoring().setLatencyMonitoringEnabled(false);
            properties.getMonitoring().setErrorRateMonitoringEnabled(false);
            assertFalse(properties.isPerformanceMonitoringEnabled());
            
            // At least one monitoring enabled
            properties.getMonitoring().setPerformanceMonitoringEnabled(true);
            assertTrue(properties.isPerformanceMonitoringEnabled());
            
            properties.getMonitoring().setPerformanceMonitoringEnabled(false);
            properties.getMonitoring().setSuccessRateMonitoringEnabled(true);
            assertTrue(properties.isPerformanceMonitoringEnabled());
        }

        @Test
        @DisplayName("Should calculate effective max batch size correctly")
        void shouldCalculateEffectiveMaxBatchSizeCorrectly() {
            properties.getTradeService().setMaxBatchSize(100);
            
            // Without memory optimization
            properties.getPerformance().setMemoryOptimizationEnabled(false);
            assertEquals(100, properties.getEffectiveMaxBatchSize());
            
            // With memory optimization - sufficient memory
            properties.getPerformance().setMemoryOptimizationEnabled(true);
            properties.getPerformance().setMaxMemoryUsageMb(256); // 256MB should allow 100 orders
            assertEquals(100, properties.getEffectiveMaxBatchSize());
            
            // With memory optimization - limited memory
            properties.getPerformance().setMaxMemoryUsageMb(1); // 1MB should limit orders
            int effectiveSize = properties.getEffectiveMaxBatchSize();
            assertTrue(effectiveSize >= 1); // Should be at least 1
            assertTrue(effectiveSize <= 100); // Should not exceed configured max
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle zero retry configuration")
        void shouldHandleZeroRetryConfiguration() {
            properties.getTradeService().setMaxRetries(0);
            properties.getTradeService().setTimeoutMs(5000);
            
            assertEquals(5000, properties.getEffectiveTotalTimeoutMs());
            assertEquals(1000, properties.getRetryDelayMs(0)); // Should still return base delay
        }

        @Test
        @DisplayName("Should handle extreme memory limits")
        void shouldHandleExtremeMemoryLimits() {
            properties.getPerformance().setMemoryOptimizationEnabled(true);
            
            // Very low memory limit
            properties.getPerformance().setMaxMemoryUsageMb(0);
            assertEquals(1, properties.getEffectiveMaxBatchSize()); // Should be at least 1
            
            // Very high memory limit
            properties.getPerformance().setMaxMemoryUsageMb(10000); // 10GB
            properties.getTradeService().setMaxBatchSize(50);
            assertEquals(50, properties.getEffectiveMaxBatchSize()); // Should respect configured max
        }

        @Test
        @DisplayName("Should handle empty fallback trigger codes")
        void shouldHandleEmptyFallbackTriggerCodes() {
            properties.getFallback().setAutoFallbackOnErrors(true);
            properties.getFallback().setFallbackTriggerStatusCodes(new int[]{});
            
            assertFalse(properties.shouldTriggerFallback(503));
            assertFalse(properties.shouldTriggerFallback(429));
        }

        @Test
        @DisplayName("Should handle null fallback trigger codes")
        void shouldHandleNullFallbackTriggerCodes() {
            properties.getFallback().setAutoFallbackOnErrors(true);
            properties.getFallback().setFallbackTriggerStatusCodes(null);
            
            // Should not throw exception and should return false
            assertDoesNotThrow(() -> {
                boolean result = properties.shouldTriggerFallback(503);
                assertFalse(result);
            });
        }
    }

    @Nested
    @DisplayName("Configuration Consistency Tests")
    class ConfigurationConsistencyTests {

        @Test
        @DisplayName("Should maintain consistency between related timeout values")
        void shouldMaintainConsistencyBetweenRelatedTimeoutValues() {
            properties.getTradeService().setTimeoutMs(30000);
            properties.getTradeService().setReadTimeoutMs(20000); // Lower than timeout
            
            properties.validate();
            
            // Read timeout should be adjusted to match timeout
            assertEquals(30000, properties.getTradeService().getReadTimeoutMs());
        }

        @Test
        @DisplayName("Should maintain consistency between batch sizes and fallback limits")
        void shouldMaintainConsistencyBetweenBatchSizesAndFallbackLimits() {
            properties.getTradeService().setMaxBatchSize(20);
            properties.getFallback().setMaxIndividualOrders(50); // Higher than batch size
            
            properties.validate();
            
            // Fallback limit should be adjusted to match batch size
            assertEquals(20, properties.getFallback().getMaxIndividualOrders());
        }

        @Test
        @DisplayName("Should maintain reasonable monitoring thresholds")
        void shouldMaintainReasonableMonitoringThresholds() {
            // Set valid but different values
            properties.getMonitoring().setSuccessRateThreshold(0.9); // 90% success
            properties.getMonitoring().setMaxErrorRate(0.08); // 8% error
            
            properties.validate();
            
            // Values should remain as set since they're valid
            assertEquals(0.9, properties.getMonitoring().getSuccessRateThreshold());
            assertEquals(0.08, properties.getMonitoring().getMaxErrorRate());
        }
    }
}