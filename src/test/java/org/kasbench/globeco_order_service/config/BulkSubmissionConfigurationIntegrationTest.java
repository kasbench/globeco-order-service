package org.kasbench.globeco_order_service.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for BulkSubmissionConfiguration with Spring Boot context.
 * Tests property binding, conditional configuration, and Spring integration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("bulk-submission-test")
@DisplayName("BulkSubmissionConfiguration Integration Tests")
class BulkSubmissionConfigurationIntegrationTest {

    @Nested
    @DisplayName("Default Configuration Tests")
    @TestPropertySource(properties = {
        "bulk.submission.enabled=true"
    })
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @ActiveProfiles("bulk-submission-test")
    static class DefaultConfigurationTests {

        @Autowired(required = false)
        private BulkSubmissionConfiguration configuration;

        @Autowired(required = false)
        private BulkSubmissionProperties properties;

        @Test
        @DisplayName("Should load configuration with default properties")
        void shouldLoadConfigurationWithDefaultProperties() {
            assertNotNull(configuration, "BulkSubmissionConfiguration should be loaded");
            assertNotNull(properties, "BulkSubmissionProperties should be loaded");
            
            assertTrue(properties.isEnabled());
            assertTrue(properties.isBulkSubmissionEnabled());
            assertTrue(configuration.isBulkSubmissionReady());
        }

        @Test
        @DisplayName("Should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            // Trade service defaults
            assertEquals(60000, properties.getTradeService().getTimeoutMs());
            assertEquals(100, properties.getTradeService().getMaxBatchSize());
            assertEquals(2, properties.getTradeService().getMaxRetries());
            
            // Fallback defaults
            assertFalse(properties.getFallback().isEnabled());
            assertEquals(10, properties.getFallback().getMaxIndividualOrders());
            
            // Monitoring defaults
            assertTrue(properties.getMonitoring().isPerformanceMonitoringEnabled());
            assertEquals(0.95, properties.getMonitoring().getSuccessRateThreshold());
            
            // Performance defaults
            assertTrue(properties.getPerformance().isDbPoolOptimizationEnabled());
            assertEquals(5, properties.getPerformance().getMaxDbConnections());
        }
    }

    @Nested
    @DisplayName("Custom Configuration Tests")
    @TestPropertySource(properties = {
        "bulk.submission.enabled=true",
        "bulk.submission.trade-service.timeout-ms=45000",
        "bulk.submission.trade-service.max-batch-size=50",
        "bulk.submission.trade-service.max-retries=3",
        "bulk.submission.fallback.enabled=true",
        "bulk.submission.fallback.max-individual-orders=5",
        "bulk.submission.monitoring.success-rate-threshold=0.90",
        "bulk.submission.performance.max-db-connections=10"
    })
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @ActiveProfiles("bulk-submission-test")
    static class CustomConfigurationTests {

        @Autowired(required = false)
        private BulkSubmissionConfiguration configuration;

        @Autowired(required = false)
        private BulkSubmissionProperties properties;

        @Test
        @DisplayName("Should load configuration with custom properties")
        void shouldLoadConfigurationWithCustomProperties() {
            assertNotNull(configuration, "BulkSubmissionConfiguration should be loaded");
            assertNotNull(properties, "BulkSubmissionProperties should be loaded");
            
            assertTrue(properties.isEnabled());
            assertTrue(properties.isBulkSubmissionEnabled());
            assertTrue(configuration.isBulkSubmissionReady());
        }

        @Test
        @DisplayName("Should bind custom property values correctly")
        void shouldBindCustomPropertyValuesCorrectly() {
            // Custom trade service values
            assertEquals(45000, properties.getTradeService().getTimeoutMs());
            assertEquals(50, properties.getTradeService().getMaxBatchSize());
            assertEquals(3, properties.getTradeService().getMaxRetries());
            
            // Custom fallback values
            assertTrue(properties.getFallback().isEnabled());
            assertEquals(5, properties.getFallback().getMaxIndividualOrders());
            
            // Custom monitoring values
            assertEquals(0.90, properties.getMonitoring().getSuccessRateThreshold());
            
            // Custom performance values
            assertEquals(10, properties.getPerformance().getMaxDbConnections());
        }

        @Test
        @DisplayName("Should enable fallback when configured")
        void shouldEnableFallbackWhenConfigured() {
            assertTrue(properties.isFallbackEnabled());
            assertTrue(configuration.shouldTriggerFallback(503, null));
        }
    }

    @Nested
    @DisplayName("Disabled Configuration Tests")
    @TestPropertySource(properties = {
        "bulk.submission.enabled=false"
    })
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @ActiveProfiles("bulk-submission-test")
    static class DisabledConfigurationTests {

        @Autowired(required = false)
        private BulkSubmissionConfiguration configuration;

        @Autowired(required = false)
        private BulkSubmissionProperties properties;

        @Test
        @DisplayName("Should not load configuration when disabled")
        void shouldNotLoadConfigurationWhenDisabled() {
            assertNull(configuration, "BulkSubmissionConfiguration should not be loaded when disabled");
            assertNull(properties, "BulkSubmissionProperties should not be loaded when disabled");
        }
    }

    @Nested
    @DisplayName("Validation Integration Tests")
    @TestPropertySource(properties = {
        "bulk.submission.enabled=true",
        "bulk.submission.trade-service.timeout-ms=500", // Invalid - too low
        "bulk.submission.trade-service.max-batch-size=2000", // Invalid - too high
        "bulk.submission.trade-service.max-retries=-1", // Invalid - negative
        "bulk.submission.monitoring.success-rate-threshold=1.5", // Invalid - above 1.0
        "bulk.submission.performance.max-db-connections=0" // Invalid - zero
    })
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @ActiveProfiles("bulk-submission-test")
    static class ValidationIntegrationTests {

        @Autowired(required = false)
        private BulkSubmissionConfiguration configuration;

        @Autowired(required = false)
        private BulkSubmissionProperties properties;

        @Test
        @DisplayName("Should validate and correct invalid property values")
        void shouldValidateAndCorrectInvalidPropertyValues() {
            assertNotNull(configuration, "Configuration should still load with invalid values");
            assertNotNull(properties, "Properties should still load with invalid values");
            
            // Values should be corrected by validation
            assertEquals(1000, properties.getTradeService().getTimeoutMs()); // Corrected from 500
            assertEquals(1000, properties.getTradeService().getMaxBatchSize()); // Corrected from 2000
            assertEquals(0, properties.getTradeService().getMaxRetries()); // Corrected from -1
            assertEquals(0.95, properties.getMonitoring().getSuccessRateThreshold()); // Corrected from 1.5
            assertEquals(1, properties.getPerformance().getMaxDbConnections()); // Corrected from 0
        }

        @Test
        @DisplayName("Should still be ready after validation corrections")
        void shouldStillBeReadyAfterValidationCorrections() {
            assertTrue(configuration.isBulkSubmissionReady());
            assertTrue(properties.isBulkSubmissionEnabled());
        }
    }
}