package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.config.MetricsProperties;
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

    private MetricsProperties metricsProperties;
    private HttpRequestMetricsInterceptor interceptor;

    @BeforeEach
    void setUp() {
        metricsProperties = createDefaultMetricsProperties();
        interceptor = new HttpRequestMetricsInterceptor(metricsService, metricsProperties);
        // Clear any existing context
        HttpRequestMetricsInterceptor.clearCurrentContext();
    }

    private MetricsProperties createDefaultMetricsProperties() {
        MetricsProperties properties = new MetricsProperties();
        properties.setEnabled(true);
        properties.getHttp().setEnabled(true);
        properties.getHttp().getRequest().setEnabled(true);
        properties.getHttp().getRequest().setRouteSanitizationEnabled(true);
        properties.getHttp().getRequest().setMaxPathSegments(10);
        properties.getHttp().getRequest().setMetricCachingEnabled(true);
        properties.getHttp().getRequest().setMaxCacheSize(1000);
        properties.getHttp().getRequest().setDetailedLoggingEnabled(false);
        return properties;
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
        RequestTimingContext context = 
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
        
        RequestTimingContext context = 
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
        
        RequestTimingContext context = 
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

        // Then - should not crash and should not call any metrics methods since no context exists
        verify(metricsService, never()).decrementInFlightRequests();
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
        RequestTimingContext context = 
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
        RequestTimingContext context = 
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
        RequestTimingContext context1 = 
            HttpRequestMetricsInterceptor.getCurrentContext();
        
        // Complete first request
        interceptor.afterCompletion(request1, response1, handlerMethod, null);
        
        // Start second request
        interceptor.preHandle(request2, response2, handlerMethod);
        RequestTimingContext context2 = 
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

    @Test
    void preHandle_WithNullMetricsService_ShouldContinueProcessing() {
        // Given
        HttpRequestMetricsInterceptor nullServiceInterceptor = new HttpRequestMetricsInterceptor(null, metricsProperties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        boolean result = nullServiceInterceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();
        // Context should not be set when metrics service is null
        assertThat(HttpRequestMetricsInterceptor.getCurrentContext()).isNull();
    }

    @Test
    void afterCompletion_WithNullMetricsService_ShouldHandleGracefully() {
        // Given
        HttpRequestMetricsInterceptor nullServiceInterceptor = new HttpRequestMetricsInterceptor(null, metricsProperties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        // When - call afterCompletion without preHandle (no context)
        nullServiceInterceptor.afterCompletion(request, response, handlerMethod, null);

        // Then - should not crash
        assertThat(HttpRequestMetricsInterceptor.getCurrentContext()).isNull();
    }

    @Test
    void requestTiming_ShouldBeAccurate() throws InterruptedException {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        
        long testStartTime = System.nanoTime();

        // When
        interceptor.preHandle(request, response, handlerMethod);
        
        // Verify context timing starts immediately
        RequestTimingContext context = HttpRequestMetricsInterceptor.getCurrentContext();
        assertThat(context.getStartTime()).isGreaterThanOrEqualTo(testStartTime);
        assertThat(context.getStartTime()).isLessThanOrEqualTo(System.nanoTime());
        
        // Simulate processing time
        Thread.sleep(10);
        
        long beforeCompletion = System.nanoTime();
        interceptor.afterCompletion(request, response, handlerMethod, null);
        long afterCompletion = System.nanoTime();

        // Then - verify timing accuracy
        verify(metricsService).recordRequest(eq("GET"), eq("/api/v1/orders"), eq(200), longThat(duration -> {
            // Duration should be at least 10ms (our sleep time) but less than total test time
            long minExpectedDuration = 10_000_000L; // 10ms in nanoseconds
            long maxExpectedDuration = afterCompletion - testStartTime;
            return duration >= minExpectedDuration && duration <= maxExpectedDuration;
        }));
    }

    @Test
    void routePatternExtraction_WithDifferentHandlerTypes() {
        // Test with String handler (non-HandlerMethod)
        MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/static/css/style.css");
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        String stringHandler = "StaticResourceHandler";

        interceptor.preHandle(request1, response1, stringHandler);
        RequestTimingContext context1 = HttpRequestMetricsInterceptor.getCurrentContext();
        assertThat(context1.getPath()).isEqualTo("/static/css/style.css");
        HttpRequestMetricsInterceptor.clearCurrentContext();

        // Test with null handler
        MockHttpServletRequest request2 = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        interceptor.preHandle(request2, response2, null);
        RequestTimingContext context2 = HttpRequestMetricsInterceptor.getCurrentContext();
        assertThat(context2.getPath()).isEqualTo("/api/v1/health");
        HttpRequestMetricsInterceptor.clearCurrentContext();

        // Test with HandlerMethod (already covered in other tests but verify here)
        MockHttpServletRequest request3 = new MockHttpServletRequest("POST", "/api/v1/orders");
        request3.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/orders");
        MockHttpServletResponse response3 = new MockHttpServletResponse();

        interceptor.preHandle(request3, response3, handlerMethod);
        RequestTimingContext context3 = HttpRequestMetricsInterceptor.getCurrentContext();
        assertThat(context3.getPath()).isEqualTo("/api/v1/orders");
        HttpRequestMetricsInterceptor.clearCurrentContext();
    }

    @Test
    void errorHandling_ShouldNotInterruptRequestProcessing() {
        // Test with invalid HTTP method
        MockHttpServletRequest request1 = new MockHttpServletRequest(null, "/api/v1/orders");
        MockHttpServletResponse response1 = new MockHttpServletResponse();

        boolean result1 = interceptor.preHandle(request1, response1, handlerMethod);
        assertThat(result1).isTrue(); // Should continue processing

        RequestTimingContext context1 = HttpRequestMetricsInterceptor.getCurrentContext();
        assertThat(context1.getMethod()).isEqualTo("UNKNOWN"); // Should use fallback
        HttpRequestMetricsInterceptor.clearCurrentContext();

        // Test with invalid URI
        MockHttpServletRequest request2 = new MockHttpServletRequest("GET", null);
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        boolean result2 = interceptor.preHandle(request2, response2, handlerMethod);
        assertThat(result2).isTrue(); // Should continue processing

        RequestTimingContext context2 = HttpRequestMetricsInterceptor.getCurrentContext();
        assertThat(context2.getPath()).isEqualTo("/unknown"); // Should use fallback
        HttpRequestMetricsInterceptor.clearCurrentContext();
    }

    @Test
    void statusCodeInference_WithExceptions() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders/999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(0); // Invalid status that should be inferred from exception
        
        // Test different exception types
        RuntimeException notFoundException = new RuntimeException("Entity not found");
        RuntimeException unauthorizedException = new RuntimeException("Authentication failed");
        RuntimeException badRequestException = new RuntimeException("Invalid request parameters");

        // When/Then - Test not found exception
        interceptor.preHandle(request, response, handlerMethod);
        interceptor.afterCompletion(request, response, handlerMethod, notFoundException);
        verify(metricsService).recordRequest(eq("GET"), anyString(), eq(500), anyLong()); // Should default to 500 for generic exceptions
        HttpRequestMetricsInterceptor.clearCurrentContext();

        // Reset mock
        reset(metricsService);

        // Test with response that has valid status (should use response status, not infer from exception)
        response.setStatus(404);
        interceptor.preHandle(request, response, handlerMethod);
        interceptor.afterCompletion(request, response, handlerMethod, notFoundException);
        verify(metricsService).recordRequest(eq("GET"), anyString(), eq(404), anyLong()); // Should use response status
        HttpRequestMetricsInterceptor.clearCurrentContext();
    }

    @Test
    void threadLocalCleanup_ShouldAlwaysOccur() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        // Test normal flow cleanup
        interceptor.preHandle(request, response, handlerMethod);
        assertThat(HttpRequestMetricsInterceptor.getCurrentContext()).isNotNull();
        
        interceptor.afterCompletion(request, response, handlerMethod, null);
        assertThat(HttpRequestMetricsInterceptor.getCurrentContext()).isNull();

        // Test cleanup when metrics service throws exception
        doThrow(new RuntimeException("Metrics error")).when(metricsService)
            .recordRequest(anyString(), anyString(), anyInt(), anyLong());

        interceptor.preHandle(request, response, handlerMethod);
        assertThat(HttpRequestMetricsInterceptor.getCurrentContext()).isNotNull();
        
        interceptor.afterCompletion(request, response, handlerMethod, null);
        assertThat(HttpRequestMetricsInterceptor.getCurrentContext()).isNull(); // Should still be cleaned up
    }

    @Test
    void fallbackMetricRecording_WithMissingContext() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500); // Error status suggests processing occurred
        RuntimeException handlerException = new RuntimeException("Processing failed");

        // When - call afterCompletion without preHandle (no context)
        interceptor.afterCompletion(request, response, handlerMethod, handlerException);

        // Then - should record fallback metric due to exception and error status
        verify(metricsService).recordRequest(eq("POST"), eq("/api/v1/orders"), eq(500), eq(0L));
    }

    @Test
    void fallbackMetricRecording_ShouldNotRecordForNormalResponses() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200); // Normal status without exception

        // When - call afterCompletion without preHandle (no context)
        interceptor.afterCompletion(request, response, handlerMethod, null);

        // Then - should NOT record fallback metric for normal responses without context
        verify(metricsService, never()).recordRequest(anyString(), anyString(), anyInt(), anyLong());
    }
}