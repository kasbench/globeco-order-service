package org.kasbench.globeco_order_service.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.service.DatabaseMetricsService;
import org.kasbench.globeco_order_service.service.HttpMetricsService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MetricsConfigurationTest {

    @Mock
    private DatabaseMetricsService databaseMetricsService;

    @Mock
    private HttpMetricsService httpMetricsService;

    @Mock
    private ContextRefreshedEvent contextRefreshedEvent;

    private MeterRegistry meterRegistry;
    private MetricsConfiguration metricsConfiguration;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsConfiguration = new MetricsConfiguration(meterRegistry, databaseMetricsService, httpMetricsService);
        
        // Set default property values
        ReflectionTestUtils.setField(metricsConfiguration, "databaseMetricsEnabled", true);
        ReflectionTestUtils.setField(metricsConfiguration, "httpMetricsEnabled", true);
        ReflectionTestUtils.setField(metricsConfiguration, "securityServiceUrl", "http://security-service:8000");
        ReflectionTestUtils.setField(metricsConfiguration, "portfolioServiceUrl", "http://portfolio-service:8000");
        ReflectionTestUtils.setField(metricsConfiguration, "initializationTimeoutSeconds", 30);
    }

    @Test
    void testInitializeMetricsConfiguration() {
        // When
        metricsConfiguration.initializeMetricsConfiguration();

        // Then
        // Should not throw any exceptions and should create a validation counter
        Counter validationCounter = meterRegistry.find("metrics.configuration.validation.test").counter();
        assertNotNull(validationCounter);
        assertEquals(1.0, validationCounter.count());
    }

    @Test
    void testInitializeMetricsConfigurationWithNullMeterRegistry() {
        // Given
        MetricsConfiguration configWithNullRegistry = new MetricsConfiguration(null, databaseMetricsService, httpMetricsService);

        // When/Then - Should not throw exception, just log error
        assertDoesNotThrow(() -> configWithNullRegistry.initializeMetricsConfiguration());
    }

    @Test
    void testOnApplicationContextRefreshed() {
        // Given
        when(databaseMetricsService.isInitialized()).thenReturn(true);

        // When
        metricsConfiguration.onApplicationContextRefreshed(contextRefreshedEvent);

        // Then - Give some time for async execution
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify that HTTP metrics registration was attempted
        verify(httpMetricsService, timeout(1000)).registerHttpConnectionPoolMetrics("security-service", "http://security-service:8000");
        verify(httpMetricsService, timeout(1000)).registerHttpConnectionPoolMetrics("portfolio-service", "http://portfolio-service:8000");
    }

    @Test
    void testGetMetricsStatusWithAllEnabled() {
        // Given
        when(databaseMetricsService.isInitialized()).thenReturn(true);
        when(httpMetricsService.getRegisteredServices()).thenReturn(java.util.Set.of("security-service", "portfolio-service"));

        // When
        String status = metricsConfiguration.getMetricsStatus();

        // Then
        assertNotNull(status);
        assertTrue(status.contains("Custom Metrics Status:"));
        assertTrue(status.contains("Database metrics: INITIALIZED"));
        assertTrue(status.contains("HTTP metrics: 2 services registered"));
        assertTrue(status.contains("Total registered meters:"));
    }

    @Test
    void testGetMetricsStatusWithDatabaseDisabled() {
        // Given
        ReflectionTestUtils.setField(metricsConfiguration, "databaseMetricsEnabled", false);
        when(httpMetricsService.getRegisteredServices()).thenReturn(java.util.Set.of("security-service"));

        // When
        String status = metricsConfiguration.getMetricsStatus();

        // Then
        assertNotNull(status);
        assertTrue(status.contains("Database metrics: DISABLED"));
        assertTrue(status.contains("HTTP metrics: 1 services registered"));
    }

    @Test
    void testGetMetricsStatusWithHttpDisabled() {
        // Given
        ReflectionTestUtils.setField(metricsConfiguration, "httpMetricsEnabled", false);
        when(databaseMetricsService.isInitialized()).thenReturn(true);

        // When
        String status = metricsConfiguration.getMetricsStatus();

        // Then
        assertNotNull(status);
        assertTrue(status.contains("Database metrics: INITIALIZED"));
        assertTrue(status.contains("HTTP metrics: DISABLED"));
    }

    @Test
    void testAreAllMetricsInitializedWhenAllEnabled() {
        // Given
        when(databaseMetricsService.isInitialized()).thenReturn(true);
        when(httpMetricsService.getRegisteredServices()).thenReturn(java.util.Set.of("security-service"));

        // When
        boolean allInitialized = metricsConfiguration.areAllMetricsInitialized();

        // Then
        assertTrue(allInitialized);
    }

    @Test
    void testAreAllMetricsInitializedWhenDatabaseNotInitialized() {
        // Given
        when(databaseMetricsService.isInitialized()).thenReturn(false);
        when(httpMetricsService.getRegisteredServices()).thenReturn(java.util.Set.of("security-service"));

        // When
        boolean allInitialized = metricsConfiguration.areAllMetricsInitialized();

        // Then
        assertFalse(allInitialized);
    }

    @Test
    void testAreAllMetricsInitializedWhenHttpNotInitialized() {
        // Given
        when(databaseMetricsService.isInitialized()).thenReturn(true);
        when(httpMetricsService.getRegisteredServices()).thenReturn(java.util.Set.of());

        // When
        boolean allInitialized = metricsConfiguration.areAllMetricsInitialized();

        // Then
        assertFalse(allInitialized);
    }

    @Test
    void testAreAllMetricsInitializedWithDatabaseDisabled() {
        // Given
        ReflectionTestUtils.setField(metricsConfiguration, "databaseMetricsEnabled", false);
        when(httpMetricsService.getRegisteredServices()).thenReturn(java.util.Set.of("security-service"));

        // When
        boolean allInitialized = metricsConfiguration.areAllMetricsInitialized();

        // Then
        assertTrue(allInitialized);
    }

    @Test
    void testAreAllMetricsInitializedWithHttpDisabled() {
        // Given
        ReflectionTestUtils.setField(metricsConfiguration, "httpMetricsEnabled", false);
        when(databaseMetricsService.isInitialized()).thenReturn(true);

        // When
        boolean allInitialized = metricsConfiguration.areAllMetricsInitialized();

        // Then
        assertTrue(allInitialized);
    }

    @Test
    void testInitializationWithEmptyServiceUrls() {
        // Given
        ReflectionTestUtils.setField(metricsConfiguration, "securityServiceUrl", "");
        ReflectionTestUtils.setField(metricsConfiguration, "portfolioServiceUrl", "");

        // When
        metricsConfiguration.onApplicationContextRefreshed(contextRefreshedEvent);

        // Then - Give some time for async execution
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify that HTTP metrics registration was not attempted for empty URLs
        verify(httpMetricsService, never()).registerHttpConnectionPoolMetrics(eq("security-service"), anyString());
        verify(httpMetricsService, never()).registerHttpConnectionPoolMetrics(eq("portfolio-service"), anyString());
    }

    @Test
    void testInitializationWithNullServiceUrls() {
        // Given
        ReflectionTestUtils.setField(metricsConfiguration, "securityServiceUrl", null);
        ReflectionTestUtils.setField(metricsConfiguration, "portfolioServiceUrl", null);

        // When
        metricsConfiguration.onApplicationContextRefreshed(contextRefreshedEvent);

        // Then - Give some time for async execution
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify that HTTP metrics registration was not attempted for null URLs
        verify(httpMetricsService, never()).registerHttpConnectionPoolMetrics(eq("security-service"), anyString());
        verify(httpMetricsService, never()).registerHttpConnectionPoolMetrics(eq("portfolio-service"), anyString());
    }

    @Test
    void testHttpMetricsRegistrationWithException() {
        // Given
        lenient().doThrow(new RuntimeException("Registration failed")).when(httpMetricsService)
            .registerHttpConnectionPoolMetrics(anyString(), anyString());

        // When/Then - Should not throw exception, just log error
        assertDoesNotThrow(() -> metricsConfiguration.onApplicationContextRefreshed(contextRefreshedEvent));
    }
}