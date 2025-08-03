package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HttpRequestMetricsInterceptor.
 * Tests request timing, route pattern extraction, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class HttpRequestMetricsInterceptorTest {

    @Mock
    private HttpRequestMetricsService metricsService;

    @Mock
    private HandlerMethod handlerMethod;

    private HttpRequestMetricsInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new HttpRequestMetricsInterceptor(metricsService);
        // Clear any existing context
        HttpRequestMetricsInterceptor.clearCurrentContext();
    }

    @Test
    void preHandle_ShouldStartTimingAndIncrementInFlightGauge() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();
        verify(metricsService).incrementInFlightRequests();
        
        // Verify context is set
        HttpRequestMetricsInterceptor.RequestTimingContext context = 
            HttpRequestMetricsInterceptor.getCurrentContext();
        assertThat(context).isNotNull();
        assertThat(context.getMethod()).isEqualTo("GET");
        assertThat(context.getPath()).isEqualTo("/api/v1/orders");
        assertThat(context.getStartTime()).isGreaterThan(0);
    }

    @Test
    void preHandle_WithBestMatchingPattern_ShouldUsePattern() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders/123");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/orders/{id}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();
        
        HttpRequestMetricsInterceptor.RequestTimingContext context = 
            HttpRequestMetricsInterceptor.getCurrentContext();
        assertThat(context.getPath()).isEqualTo("/api/v1/orders/{id}");
    }

    @Test
    void preHandle_WithPathWithinHandlerMapping_ShouldUsePath() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/blotters");
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "/api/v1/blotters");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();
        
        HttpRequestMetricsInterceptor.RequestTimingContext context = 
            HttpRequestMetricsInterceptor.getCurrentContext();
        assertThat(context.getPath()).isEqualTo("/api/v1/blotters");
    }

    @Test
    void preHandle_WithException_ShouldContinueProcessing() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        doThrow(new RuntimeException("Metrics service error")).when(metricsService).incrementInFlightRequests();

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue(); // Should continue processing despite error
    }

    @Test
    void afterCompletion_ShouldRecordMetricsAndDecrementGauge() throws InterruptedException {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        // Start the request
        interceptor.preHandle(request, response, handlerMethod);
        
        // Simulate some processing time
        Thread.sleep(1);

        // When
        interceptor.afterCompletion(request, response, handlerMethod, null);

        // Then
        verify(metricsService).recordRequest(eq("GET"), eq("/api/v1/orders"), eq(200), anyLong());
        verify(metricsService).decrementInFlightRequests();
        
        // Verify context is cleared
        assertThat(HttpRequestMetricsInterceptor.getCurrentContext()).isNull();
    }

    @Test
    void afterCompletion_WithError_ShouldStillRecordMetrics() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        Exception handlerException = new RuntimeException("Handler error");

        // Start the request
        interceptor.preHandle(request, response, handlerMethod);

        // When
        interceptor.afterCompletion(request, response, handlerMethod, handlerException);

        // Then
        verify(metricsService).recordRequest(eq("POST"), eq("/api/v1/orders"), eq(500), anyLong());
        verify(metricsService).decrementInFlightRequests();
    }

    @Test
    void afterCompletion_WithoutPreHandle_ShouldHandleGracefully() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        // When - call afterCompletion without preHandle
        interceptor.afterCompletion(request, response, handlerMethod, null);

        // Then - should not crash and should still decrement gauge
        verify(metricsService).decrementInFlightRequests();
        verify(metricsService, never()).recordRequest(anyString(), anyString(), anyInt(), anyLong());
    }

    @Test
    void afterCompletion_WithMetricsServiceException_ShouldContinue() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.preHandle(request, response, handlerMethod);
        
        doThrow(new RuntimeException("Metrics error")).when(metricsService)
            .recordRequest(anyString(), anyString(), anyInt(), anyLong());

        // When
        interceptor.afterCompletion(request, response, handlerMethod, null);

        // Then - should not throw exception and should clean up
        assertThat(HttpRequestMetricsInterceptor.getCurrentContext()).isNull();
        verify(metricsService).decrementInFlightRequests();
    }

    @Test
    void extractRoutePath_WithQueryParameters_ShouldSanitize() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders?page=1&size=10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        interceptor.preHandle(request, response, handlerMethod);

        // Then
        HttpRequestMetricsInterceptor.RequestTimingContext context = 
            HttpRequestMetricsInterceptor.getCurrentContext();
        assertThat(context.getPath()).isEqualTo("/api/v1/orders");
    }

    @Test
    void extractRoutePath_WithNullRequestURI_ShouldUseUnknown() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        interceptor.preHandle(request, response, handlerMethod);

        // Then
        HttpRequestMetricsInterceptor.RequestTimingContext context = 
            HttpRequestMetricsInterceptor.getCurrentContext();
        assertThat(context.getPath()).isEqualTo("/unknown");
    }

    @Test
    void fullRequestLifecycle_ShouldMaintainCorrectTiming() throws InterruptedException {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/orders/123");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/orders/{id}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(204);

        // When
        long startTime = System.nanoTime();
        interceptor.preHandle(request, response, handlerMethod);
        
        Thread.sleep(5); // Simulate processing time
        
        interceptor.afterCompletion(request, response, handlerMethod, null);
        long endTime = System.nanoTime();

        // Then
        verify(metricsService).incrementInFlightRequests();
        verify(metricsService).recordRequest(eq("PUT"), eq("/api/v1/orders/{id}"), eq(204), longThat(duration -> {
            // Verify duration is reasonable (between 1ms and total test time)
            return duration > 1_000_000 && duration < (endTime - startTime);
        }));
        verify(metricsService).decrementInFlightRequests();
        
        // Verify cleanup
        assertThat(HttpRequestMetricsInterceptor.getCurrentContext()).isNull();
    }

    @Test
    void multipleRequests_ShouldHandleThreadLocalCorrectly() {
        // Given
        MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletRequest request2 = new MockHttpServletRequest("POST", "/api/v1/blotters");
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        response1.setStatus(200);
        response2.setStatus(201);

        // When - simulate overlapping requests (though in same thread for test)
        interceptor.preHandle(request1, response1, handlerMethod);
        HttpRequestMetricsInterceptor.RequestTimingContext context1 = 
            HttpRequestMetricsInterceptor.getCurrentContext();
        
        // Complete first request
        interceptor.afterCompletion(request1, response1, handlerMethod, null);
        
        // Start second request
        interceptor.preHandle(request2, response2, handlerMethod);
        HttpRequestMetricsInterceptor.RequestTimingContext context2 = 
            HttpRequestMetricsInterceptor.getCurrentContext();
        
        // Complete second request
        interceptor.afterCompletion(request2, response2, handlerMethod, null);

        // Then
        assertThat(context1.getMethod()).isEqualTo("GET");
        assertThat(context1.getPath()).isEqualTo("/api/v1/orders");
        assertThat(context2.getMethod()).isEqualTo("POST");
        assertThat(context2.getPath()).isEqualTo("/api/v1/blotters");
        
        verify(metricsService, times(2)).incrementInFlightRequests();
        verify(metricsService, times(2)).decrementInFlightRequests();
        verify(metricsService).recordRequest(eq("GET"), eq("/api/v1/orders"), eq(200), anyLong());
        verify(metricsService).recordRequest(eq("POST"), eq("/api/v1/blotters"), eq(201), anyLong());
        
        // Final cleanup verification
        assertThat(HttpRequestMetricsInterceptor.getCurrentContext()).isNull();
    }
}