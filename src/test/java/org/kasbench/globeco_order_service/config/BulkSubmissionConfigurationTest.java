package org.kasbench.globeco_order_service.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.ContextRefreshedEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BulkSubmissionConfiguration class.
 * Tests configuration initialization, validation, and business logic methods.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BulkSubmissionConfiguration Tests")
class BulkSubmissionConfigurationTest {

    @Mock
    private BulkSubmissionProperties mockProperties;

    @Mock
    private ContextRefreshedEvent mockContextRefreshedEvent;

    private BulkSubmissionConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = new BulkSubmissionConfiguration(mockProperties);
    }

    @Nested
    @DisplayName("Initialization Tests")
    class InitializationTests {

        @Test
        @DisplayName("Should initialize configuration successfully with valid properties")
        void shouldInitializeConfigurationSuccessfullyWithValidProperties() {
            // Setup valid properties
            when(mockProperties.isEnabled()).thenReturn(true);
            when(mockProperties.isBulkSubmissionEnabled()).thenReturn(true);
            when(mockProperties.getTradeService()).thenReturn(createValidTradeServiceConfig());
            when(mockProperties.getFallback()).thenReturn(createValidFallbackConfig());
            when(mockProperties.getMonitoring()).thenReturn(createValidMonitoringConfig());
            when(mockProperties.getPerformance()).thenReturn(createValidPerformanceConfig());

            // Should not throw exception
            assertDoesNotThrow(() -> configuration.initializeBulkSubmissionConfiguration());

            // Verify validation was called
            verify(mockProperties).validate();
        }

        @Test
        @DisplayName("Should handle initialization failure gracefully")
        void shouldHandleInitializationFailureGracefully() {
            // Setup properties to throw exception during validation
            doThrow(new RuntimeException("Validation failed")).when(mockProperties).validate();

            // Should throw RuntimeException
            RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> configuration.initializeBulkSubmissionConfiguration());
            
            assertEquals("Bulk submission configuration initialization failed", exception.getMessage());
            assertTrue(exception.getCause() instanceof RuntimeException);
            assertEquals("Validation failed", exception.getCause().getMessage());
        }

        @Test
        @DisplayName("Should handle context refresh event correctly")
        void shouldHandleContextRefreshEventCorrectly() {
            // Setup enabled configuration
            when(mockProperties.isBulkSubmissionEnabled()).thenReturn(true);
            when(mockProperties.isFallbackEnabled()).thenReturn(true);
            when(mockProperties.isPerformanceMonitoringEnabled()).thenReturn(true);

            // Should not throw exception
            assertDoesNotThrow(() -> configuration.onApplicationContextRefreshed(mockContextRefreshedEvent));

            // Verify properties were checked
            verify(mockProperties).isBulkSubmissionEnabled();
            verify(mockProperties).isFallbackEnabled();
            verify(mockProperties).isPerformanceMonitoringEnabled();
        }

        @Test
        @DisplayName("Should handle context refresh with disabled configuration")
        void shouldHandleContextRefreshWithDisabledConfiguration() {
            // Setup disabled configuration
            when(mockProperties.isBulkSubmissionEnabled()).thenReturn(false);

            // Should not throw exception
            assertDoesNotThrow(() -> configuration.onApplicationContextRefreshed(mockContextRefreshedEvent));

            // Verify properties were checked
            verify(mockProperties).isBulkSubmissionEnabled();
        }
    }

    @Nested
    @DisplayName("Status and Readiness Tests")
    class StatusAndReadinessTests {

        @Test
        @DisplayName("Should report bulk submission as ready when properly configured")
        void shouldReportBulkSubmissionAsReadyWhenProperlyConfigured() {
            // Setup ready configuration
            when(mockProperties.isBulkSubmissionEnabled()).thenReturn(true);
            
            BulkSubmissionProperties.TradeService tradeService = createValidTradeServiceConfig();
            when(mockProperties.getTradeService()).thenReturn(tradeService);

            assertTrue(configuration.isBulkSubmissionReady());
        }

        @Test
        @DisplayName("Should report bulk submission as not ready when disabled")
        void shouldReportBulkSubmissionAsNotReadyWhenDisabled() {
            when(mockProperties.isBulkSubmissionEnabled()).thenReturn(false);

            assertFalse(configuration.isBulkSubmissionReady());
        }

        @Test
        @DisplayName("Should report bulk submission as not ready with invalid configuration")
        void shouldReportBulkSubmissionAsNotReadyWithInvalidConfiguration() {
            when(mockProperties.isBulkSubmissionEnabled()).thenReturn(true);
            
            // Setup invalid trade service configuration
            BulkSubmissionProperties.TradeService tradeService = new BulkSubmissionProperties.TradeService();
            tradeService.setMaxBatchSize(0); // Invalid
            tradeService.setTimeoutMs(0); // Invalid
            when(mockProperties.getTradeService()).thenReturn(tradeService);

            assertFalse(configuration.isBulkSubmissionReady());
        }

        @Test
        @DisplayName("Should generate comprehensive status report")
        void shouldGenerateComprehensiveStatusReport() {
            // Setup configuration
            when(mockProperties.isEnabled()).thenReturn(true);
            when(mockProperties.isBulkSubmissionEnabled()).thenReturn(true);
            when(mockProperties.isFallbackEnabled()).thenReturn(false);
            when(mockProperties.isPerformanceMonitoringEnabled()).thenReturn(true);
            when(mockProperties.getEffectiveMaxBatchSize()).thenReturn(75);
            when(mockProperties.getEffectiveTotalTimeoutMs()).thenReturn(65000L);
            
            BulkSubmissionProperties.TradeService tradeService = createValidTradeServiceConfig();
            when(mockProperties.getTradeService()).thenReturn(tradeService);

            String status = configuration.getBulkSubmissionStatus();

            assertNotNull(status);
            assertTrue(status.contains("ENABLED"));
            assertTrue(status.contains("ACTIVE"));
            assertTrue(status.contains("DISABLED")); // Fallback
            assertTrue(status.contains("100")); // Max batch size
            assertTrue(status.contains("75")); // Effective max batch size
            assertTrue(status.contains("60000")); // Timeout
            assertTrue(status.contains("65000")); // Total timeout
        }
    }

    @Nested
    @DisplayName("Batch Size Validation Tests")
    class BatchSizeValidationTests {

        @Test
        @DisplayName("Should validate batch size correctly")
        void shouldValidateBatchSizeCorrectly() {
            when(mockProperties.getEffectiveMaxBatchSize()).thenReturn(100);

            assertTrue(configuration.isValidBatchSize(50));
            assertTrue(configuration.isValidBatchSize(100));
            assertTrue(configuration.isValidBatchSize(1));
            
            assertFalse(configuration.isValidBatchSize(0));
            assertFalse(configuration.isValidBatchSize(-1));
            assertFalse(configuration.isValidBatchSize(101));
        }

        @Test
        @DisplayName("Should recommend appropriate batch size")
        void shouldRecommendAppropriateBatchSize() {
            when(mockProperties.getEffectiveMaxBatchSize()).thenReturn(100);

            // Normal cases
            assertEquals(50, configuration.getRecommendedBatchSize(50));
            assertEquals(100, configuration.getRecommendedBatchSize(100));
            assertEquals(100, configuration.getRecommendedBatchSize(150)); // Capped at max
            
            // Edge cases
            assertEquals(50, configuration.getRecommendedBatchSize(0)); // Default to 50
            assertEquals(50, configuration.getRecommendedBatchSize(-1)); // Default to 50
        }

        @Test
        @DisplayName("Should handle small maximum batch size")
        void shouldHandleSmallMaximumBatchSize() {
            when(mockProperties.getEffectiveMaxBatchSize()).thenReturn(10);

            assertEquals(10, configuration.getRecommendedBatchSize(0)); // Default capped at max
            assertEquals(10, configuration.getRecommendedBatchSize(50)); // Requested capped at max
            assertEquals(5, configuration.getRecommendedBatchSize(5)); // Requested within limit
        }
    }

    @Nested
    @DisplayName("Fallback Decision Tests")
    class FallbackDecisionTests {

        @Test
        @DisplayName("Should trigger fallback for configured status codes")
        void shouldTriggerFallbackForConfiguredStatusCodes() {
            when(mockProperties.isFallbackEnabled()).thenReturn(true);
            when(mockProperties.shouldTriggerFallback(503)).thenReturn(true);
            when(mockProperties.shouldTriggerFallback(429)).thenReturn(true);
            when(mockProperties.shouldTriggerFallback(200)).thenReturn(false);

            assertTrue(configuration.shouldTriggerFallback(503, null));
            assertTrue(configuration.shouldTriggerFallback(429, null));
            assertFalse(configuration.shouldTriggerFallback(200, null));
        }

        @Test
        @DisplayName("Should not trigger fallback when fallback is disabled")
        void shouldNotTriggerFallbackWhenFallbackIsDisabled() {
            when(mockProperties.isFallbackEnabled()).thenReturn(false);

            assertFalse(configuration.shouldTriggerFallback(503, null));
            assertFalse(configuration.shouldTriggerFallback(429, null));
        }

        @Test
        @DisplayName("Should trigger fallback for connection-related exceptions")
        void shouldTriggerFallbackForConnectionRelatedExceptions() {
            when(mockProperties.isFallbackEnabled()).thenReturn(true);
            when(mockProperties.shouldTriggerFallback(200)).thenReturn(false);

            // Connection-related exceptions
            Exception connectionException = new RuntimeException("Connection timeout");
            Exception unavailableException = new RuntimeException("Service unavailable");
            Exception timeoutException = new RuntimeException("Request timeout");
            Exception otherException = new RuntimeException("Other error");

            assertTrue(configuration.shouldTriggerFallback(200, connectionException));
            assertTrue(configuration.shouldTriggerFallback(200, unavailableException));
            assertTrue(configuration.shouldTriggerFallback(200, timeoutException));
            assertFalse(configuration.shouldTriggerFallback(200, otherException));
        }

        @Test
        @DisplayName("Should handle null exception gracefully")
        void shouldHandleNullExceptionGracefully() {
            when(mockProperties.isFallbackEnabled()).thenReturn(true);
            when(mockProperties.shouldTriggerFallback(200)).thenReturn(false);

            assertFalse(configuration.shouldTriggerFallback(200, null));
        }

        @Test
        @DisplayName("Should handle exception with null message")
        void shouldHandleExceptionWithNullMessage() {
            when(mockProperties.isFallbackEnabled()).thenReturn(true);
            when(mockProperties.shouldTriggerFallback(200)).thenReturn(false);

            Exception exceptionWithNullMessage = new RuntimeException((String) null);
            assertFalse(configuration.shouldTriggerFallback(200, exceptionWithNullMessage));
        }
    }

    @Nested
    @DisplayName("Property Access Tests")
    class PropertyAccessTests {

        @Test
        @DisplayName("Should provide access to bulk submission properties")
        void shouldProvideAccessToBulkSubmissionProperties() {
            BulkSubmissionProperties properties = configuration.getBulkSubmissionProperties();
            assertSame(mockProperties, properties);
        }
    }

    // Helper methods to create valid configuration objects
    private BulkSubmissionProperties.TradeService createValidTradeServiceConfig() {
        BulkSubmissionProperties.TradeService tradeService = new BulkSubmissionProperties.TradeService();
        tradeService.setTimeoutMs(60000);
        tradeService.setMaxBatchSize(100);
        tradeService.setConnectionTimeoutMs(10000);
        tradeService.setReadTimeoutMs(60000);
        tradeService.setMaxRetries(2);
        tradeService.setRetryDelayMs(1000);
        tradeService.setExponentialBackoff(true);
        return tradeService;
    }

    private BulkSubmissionProperties.Fallback createValidFallbackConfig() {
        BulkSubmissionProperties.Fallback fallback = new BulkSubmissionProperties.Fallback();
        fallback.setEnabled(false);
        fallback.setMaxIndividualOrders(10);
        fallback.setIndividualTimeoutMs(30000);
        fallback.setAutoFallbackOnErrors(true);
        fallback.setFallbackTriggerStatusCodes(new int[]{503, 429, 502, 504});
        return fallback;
    }

    private BulkSubmissionProperties.Monitoring createValidMonitoringConfig() {
        BulkSubmissionProperties.Monitoring monitoring = new BulkSubmissionProperties.Monitoring();
        monitoring.setPerformanceMonitoringEnabled(true);
        monitoring.setSuccessRateMonitoringEnabled(true);
        monitoring.setSuccessRateThreshold(0.95);
        monitoring.setSuccessRateWindowMinutes(15);
        monitoring.setLatencyMonitoringEnabled(true);
        monitoring.setMaxAcceptableLatencyMs(30000);
        monitoring.setErrorRateMonitoringEnabled(true);
        monitoring.setMaxErrorRate(0.05);
        monitoring.setDetailedLoggingEnabled(true);
        monitoring.setLogPerformanceMetrics(true);
        return monitoring;
    }

    private BulkSubmissionProperties.Performance createValidPerformanceConfig() {
        BulkSubmissionProperties.Performance performance = new BulkSubmissionProperties.Performance();
        performance.setDbPoolOptimizationEnabled(true);
        performance.setMaxDbConnections(5);
        performance.setBatchDbUpdatesEnabled(true);
        performance.setDbUpdateBatchSize(50);
        performance.setAsyncProcessingEnabled(true);
        performance.setAsyncThreadPoolSize(3);
        performance.setMemoryOptimizationEnabled(true);
        performance.setMaxMemoryUsageMb(256);
        return performance;
    }
}