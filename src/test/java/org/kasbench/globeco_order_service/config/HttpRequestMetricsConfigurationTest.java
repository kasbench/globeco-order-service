package org.kasbench.globeco_order_service.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.kasbench.globeco_order_service.service.HttpRequestMetricsInterceptor;
import org.kasbench.globeco_order_service.service.HttpRequestMetricsService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HttpRequestMetricsConfiguration.
 * Tests the configuration class functionality including interceptor registration
 * and initialization validation.
 */
class HttpRequestMetricsConfigurationTest {

    @Mock
    private HttpRequestMetricsInterceptor mockInterceptor;

    @Mock
    private HttpRequestMetricsService mockService;

    @Mock
    private MetricsProperties mockProperties;

    @Mock
    private MetricsProperties.HttpMetrics mockHttpMetrics;

    @Mock
    private MetricsProperties.Initialization mockInitialization;

    @Mock
    private InterceptorRegistry mockRegistry;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup default behavior for mocks
        when(mockProperties.getHttp()).thenReturn(mockHttpMetrics);
        when(mockProperties.getInitialization()).thenReturn(mockInitialization);
        when(mockInitialization.isFailOnError()).thenReturn(false);
        when(mockInitialization.isVerboseLogging()).thenReturn(false);
    }

    @Test
    void testConfigurationCreation() {
        // When
        HttpRequestMetricsConfiguration config = new HttpRequestMetricsConfiguration(
                mockInterceptor, mockService, mockProperties);

        // Then
        assertNotNull(config);
        assertSame(mockProperties, config.getMetricsProperties());
    }

    @Test
    void testInterceptorRegistration() {
        // Given
        org.springframework.web.servlet.config.annotation.InterceptorRegistration mockRegistration = 
            mock(org.springframework.web.servlet.config.annotation.InterceptorRegistration.class);
        when(mockRegistry.addInterceptor(mockInterceptor)).thenReturn(mockRegistration);
        when(mockRegistration.addPathPatterns("/**")).thenReturn(mockRegistration);
        when(mockRegistration.order(0)).thenReturn(mockRegistration);

        HttpRequestMetricsConfiguration config = new HttpRequestMetricsConfiguration(
                mockInterceptor, mockService, mockProperties);

        // When
        config.addInterceptors(mockRegistry);

        // Then
        verify(mockRegistry).addInterceptor(mockInterceptor);
        verify(mockRegistration).addPathPatterns("/**");
        verify(mockRegistration).order(0);
    }

    @Test
    void testInterceptorRegistrationWithNullInterceptor() {
        // Given
        HttpRequestMetricsConfiguration config = new HttpRequestMetricsConfiguration(
                null, mockService, mockProperties);

        // When
        config.addInterceptors(mockRegistry);

        // Then
        verify(mockRegistry, never()).addInterceptor(any());
    }

    @Test
    void testOperationalStatusWithAllComponents() {
        // Given
        when(mockService.isInitialized()).thenReturn(true);
        when(mockHttpMetrics.isEnabled()).thenReturn(true);

        HttpRequestMetricsConfiguration config = new HttpRequestMetricsConfiguration(
                mockInterceptor, mockService, mockProperties);

        // When & Then
        assertTrue(config.isOperational());
    }

    @Test
    void testOperationalStatusWithMissingService() {
        // Given
        HttpRequestMetricsConfiguration config = new HttpRequestMetricsConfiguration(
                mockInterceptor, null, mockProperties);

        // When & Then
        assertFalse(config.isOperational());
    }
}