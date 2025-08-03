package org.kasbench.globeco_order_service.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for error handling and resilience in HTTP request metrics components.
 * These tests verify that metrics collection failures don't impact request processing.
 */
@ExtendWith(MockitoExtension.class)
class HttpRequestMetricsErrorHandlingTest {

    @Mock
    private HttpRequestMetricsService mockMetricsService;
    
    @Mock
    private HandlerMethod handlerMethod;
    
    private HttpRequestMetricsInterceptor interceptor;
    private HttpRequestMetricsService realMetricsService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        realMetricsService = new HttpRequestMetricsService(meterRegistry);
        interceptor = new HttpRequestMetricsInterceptor(mockMetricsService);
    }

    @Test
    void interceptor_WithNullMetricsService_ShouldNotCrash() {
        // Given
        HttpRequestMetricsInterceptor nullServiceInterceptor = new HttpRequestMetricsInterceptor(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // When & Then - should not throw any exceptions
        assertThatNoException().isThrownBy(() -> {
            boolean result = nullServiceInterceptor.preHandle(request, response, handlerMethod);
            assertThat(result).isTrue(); // Should continue processing
            
            response.setStatus(200);
            nullServiceInterceptor.afterCompletion(request, response, handlerMethod, null);
        });
    }

    @Test
    void interceptor_WithMetricsServiceException_ShouldContinueProcessing() {
        // Given
        doThrow(new RuntimeException("Metrics service failed")).when(mockMetricsService)
            .incrementInFlightRequests();
        
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // When & Then - should not throw any exceptions
        assertThatNoException().isThrownBy(() -> {
            boolean result = interceptor.preHandle(request, response, handlerMethod);
            assertThat(result).isTrue(); // Should continue processing even if metrics fail
            
            response.setStatus(200);
            interceptor.afterCompletion(request, response, handlerMethod, null);
        });
    }

    @Test
    void metricsService_WithInvalidInputs_ShouldHandleGracefully() {
        // When & Then - should not throw any exceptions
        assertThatNoException().isThrownBy(() -> {
            realMetricsService.recordRequest(null, null, -1, -1000);
            realMetricsService.recordRequest("", "", 0, Long.MAX_VALUE);
            realMetricsService.recordRequest("INVALID_METHOD_WITH_VERY_LONG_NAME_THAT_EXCEEDS_LIMITS", 
                                           "/very/long/path/with/many/segments/that/should/be/truncated/for/safety", 
                                           9999, -500);
        });
    }

    @Test
    void routePatternSanitizer_WithInvalidInputs_ShouldReturnFallbacks() {
        // Test null input
        assertThat(RoutePatternSanitizer.sanitize(null)).isEqualTo("/unknown");
        
        // Test empty input
        assertThat(RoutePatternSanitizer.sanitize("")).isEqualTo("/unknown");
        assertThat(RoutePatternSanitizer.sanitize("   ")).isEqualTo("/unknown");
        
        // Test malformed paths
        assertThat(RoutePatternSanitizer.sanitize("not-a-path")).startsWith("/");
        assertThat(RoutePatternSanitizer.sanitize("///multiple///slashes///")).isEqualTo("/multiple/slashes");
    }

    @Test
    void httpMethodNormalizer_WithInvalidInputs_ShouldReturnFallbacks() {
        // Test null input
        assertThat(HttpMethodNormalizer.normalize(null)).isEqualTo("UNKNOWN");
        
        // Test empty input
        assertThat(HttpMethodNormalizer.normalize("")).isEqualTo("UNKNOWN");
        assertThat(HttpMethodNormalizer.normalize("   ")).isEqualTo("UNKNOWN");
        
        // Test methods that should be normalized to UNKNOWN (truly invalid patterns)
        assertThat(HttpMethodNormalizer.normalize("invalid method with spaces")).isEqualTo("UNKNOWN");
        assertThat(HttpMethodNormalizer.normalize("123")).isEqualTo("UNKNOWN");
        assertThat(HttpMethodNormalizer.normalize("get")).isEqualTo("GET"); // Should normalize case
        
        // Test that reasonable custom methods are allowed (this is the intended behavior)
        String customMethod = HttpMethodNormalizer.normalize("CUSTOM_METHOD");
        assertThat(customMethod).isNotEqualTo("UNKNOWN"); // Should allow reasonable custom methods
    }

    @Test
    void statusCodeHandler_WithInvalidInputs_ShouldReturnFallbacks() {
        // Test invalid status codes
        assertThat(StatusCodeHandler.normalize(-1)).isEqualTo("unknown");
        assertThat(StatusCodeHandler.normalize(0)).isEqualTo("unknown");
        assertThat(StatusCodeHandler.normalize(1000)).isEqualTo("unknown"); // Actually invalid, > 999
        
        // Test valid status codes
        assertThat(StatusCodeHandler.normalize(200)).isEqualTo("200");
        assertThat(StatusCodeHandler.normalize(404)).isEqualTo("404");
        assertThat(StatusCodeHandler.normalize(500)).isEqualTo("500");
        
        // Test edge case valid codes
        assertThat(StatusCodeHandler.normalize(999)).isEqualTo("999"); // Edge of valid range
    }

    @Test
    void requestTimingContext_WithNullMetricsService_ShouldHandleGracefully() {
        // When & Then - should not throw any exceptions
        assertThatNoException().isThrownBy(() -> {
            RequestTimingContext context = new RequestTimingContext("GET", "/api/v1/orders", null);
            
            assertThat(context.getMethod()).isEqualTo("GET");
            assertThat(context.getPath()).isEqualTo("/api/v1/orders");
            assertThat(context.isCompleted()).isFalse();
            assertThat(context.isInFlightIncremented()).isFalse();
            
            context.complete(200);
            assertThat(context.isCompleted()).isTrue();
            
            // Cleanup should also not throw
            context.cleanup();
        });
    }

    @Test
    void requestTimingContext_WithInvalidInputs_ShouldNormalizeGracefully() {
        // When
        RequestTimingContext context = new RequestTimingContext(null, null, realMetricsService);
        
        // Then
        assertThat(context.getMethod()).isEqualTo("UNKNOWN");
        assertThat(context.getPath()).isEqualTo("/unknown");
        assertThat(context.isCompleted()).isFalse();
        
        // Complete with invalid status should not throw
        assertThatNoException().isThrownBy(() -> {
            context.complete(-1);
            assertThat(context.isCompleted()).isTrue();
        });
    }

    @Test
    void metricsService_InFlightCounter_ShouldHandleNegativeValues() {
        // Given - force in-flight counter to go negative
        realMetricsService.decrementInFlightRequests(); // Should handle gracefully
        realMetricsService.decrementInFlightRequests(); // Should reset to 0 if negative
        
        // When
        int inFlightCount = realMetricsService.getInFlightRequests();
        
        // Then
        assertThat(inFlightCount).isGreaterThanOrEqualTo(0); // Should not be negative
    }

    @Test
    void interceptor_WithMalformedRequest_ShouldExtractFallbackValues() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(null); // Malformed request
        request.setRequestURI(null);
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // When & Then - should not throw any exceptions
        assertThatNoException().isThrownBy(() -> {
            boolean result = interceptor.preHandle(request, response, handlerMethod);
            assertThat(result).isTrue();
            
            response.setStatus(500);
            interceptor.afterCompletion(request, response, handlerMethod, new RuntimeException("Test exception"));
        });
        
        // Verify that metrics service was called with fallback values
        verify(mockMetricsService, atLeastOnce()).incrementInFlightRequests();
        verify(mockMetricsService, atLeastOnce()).decrementInFlightRequests();
    }
}