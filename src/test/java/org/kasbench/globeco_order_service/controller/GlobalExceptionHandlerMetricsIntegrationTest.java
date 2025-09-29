package org.kasbench.globeco_order_service.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.dto.ErrorResponseDTO;
import org.kasbench.globeco_order_service.exception.SystemOverloadException;
import org.kasbench.globeco_order_service.service.ErrorClassification;
import org.kasbench.globeco_order_service.service.ErrorMetricsService;
import org.kasbench.globeco_order_service.service.SystemOverloadDetector;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.WebRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for GlobalExceptionHandler with ErrorMetricsService.
 * Tests that error metrics are properly recorded when exceptions are handled.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerMetricsIntegrationTest {

    private MeterRegistry meterRegistry;
    private ErrorMetricsService errorMetricsService;
    private GlobalExceptionHandler globalExceptionHandler;
    
    @Mock
    private SystemOverloadDetector systemOverloadDetector;
    
    @Mock
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        errorMetricsService = new ErrorMetricsService(meterRegistry, systemOverloadDetector);
        errorMetricsService.initializeErrorMetrics();
        
        globalExceptionHandler = new GlobalExceptionHandler(systemOverloadDetector, errorMetricsService);
        
        // Set up request context for Spring Web
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("Should record metrics when handling SystemOverloadException")
    void shouldRecordMetricsWhenHandlingSystemOverloadException() {
        // Given
        when(systemOverloadDetector.isInitialized()).thenReturn(true);
        when(systemOverloadDetector.calculateRetryDelay()).thenReturn(300);
        when(systemOverloadDetector.getThreadPoolUtilization()).thenReturn(0.95);
        when(systemOverloadDetector.getDatabaseConnectionUtilization()).thenReturn(0.90);
        when(systemOverloadDetector.getMemoryUtilization()).thenReturn(0.85);
        
        SystemOverloadException exception = new SystemOverloadException(
            "System overloaded", 300, "database_connection_pool_exhausted");
        
        // When
        ResponseEntity<ErrorResponseDTO> response = globalExceptionHandler.handleSystemOverload(exception, webRequest);
        
        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(ErrorClassification.SERVICE_OVERLOADED, response.getBody().getCode());
        
        // Verify error metrics were recorded
        Counter errorCounter = meterRegistry.find("http_errors_total")
            .tag("error_code", ErrorClassification.SERVICE_OVERLOADED)
            .tag("status_code", "503")
            .tag("severity", "CRITICAL")
            .tag("retryable", "true")
            .counter();
        
        assertNotNull(errorCounter);
        assertEquals(1.0, errorCounter.count());
        
        // Verify overload event was recorded
        Counter overloadCounter = meterRegistry.find("system_overload_events_total")
            .tag("reason", "database_connection_pool_exhausted")
            .counter();
        
        assertNotNull(overloadCounter);
        assertEquals(1.0, overloadCounter.count());
    }

    @Test
    @DisplayName("Should record metrics when handling IllegalArgumentException")
    void shouldRecordMetricsWhenHandlingIllegalArgumentException() {
        // Given
        IllegalArgumentException exception = new IllegalArgumentException("Invalid order data");
        
        // When
        ResponseEntity<ErrorResponseDTO> response = globalExceptionHandler.handleValidationError(exception, webRequest);
        
        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorClassification.VALIDATION_ERROR, response.getBody().getCode());
        
        // Verify error metrics were recorded
        Counter errorCounter = meterRegistry.find("http_errors_total")
            .tag("error_code", ErrorClassification.VALIDATION_ERROR)
            .tag("status_code", "400")
            .tag("severity", "LOW")
            .tag("retryable", "false")
            .counter();
        
        assertNotNull(errorCounter);
        assertEquals(1.0, errorCounter.count());
    }

    @Test
    @DisplayName("Should record metrics when handling RuntimeException")
    void shouldRecordMetricsWhenHandlingRuntimeException() {
        // Given
        RuntimeException exception = new RuntimeException("Database connection failed");
        
        // When
        ResponseEntity<ErrorResponseDTO> response = globalExceptionHandler.handleRuntimeError(exception, webRequest);
        
        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ErrorClassification.RUNTIME_ERROR, response.getBody().getCode());
        
        // Verify error metrics were recorded
        Counter errorCounter = meterRegistry.find("http_errors_total")
            .tag("error_code", ErrorClassification.RUNTIME_ERROR)
            .tag("status_code", "500")
            .tag("severity", "HIGH")
            .tag("retryable", "false")
            .counter();
        
        assertNotNull(errorCounter);
        assertEquals(1.0, errorCounter.count());
    }

    @Test
    @DisplayName("Should record metrics when handling generic Exception")
    void shouldRecordMetricsWhenHandlingGenericException() {
        // Given
        Exception exception = new Exception("Unexpected error occurred");
        
        // When
        ResponseEntity<ErrorResponseDTO> response = globalExceptionHandler.handleGenericError(exception, webRequest);
        
        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ErrorClassification.INTERNAL_ERROR, response.getBody().getCode());
        
        // Verify error metrics were recorded
        Counter errorCounter = meterRegistry.find("http_errors_total")
            .tag("error_code", ErrorClassification.INTERNAL_ERROR)
            .tag("status_code", "500")
            .tag("severity", "HIGH")
            .tag("retryable", "false")
            .counter();
        
        assertNotNull(errorCounter);
        assertEquals(1.0, errorCounter.count());
    }

    @Test
    @DisplayName("Should handle multiple exceptions and track metrics separately")
    void shouldHandleMultipleExceptionsAndTrackMetricsSeparately() {
        // Given
        IllegalArgumentException validationException = new IllegalArgumentException("Invalid data");
        RuntimeException runtimeException = new RuntimeException("Runtime error");
        
        // When
        globalExceptionHandler.handleValidationError(validationException, webRequest);
        globalExceptionHandler.handleValidationError(validationException, webRequest);
        globalExceptionHandler.handleRuntimeError(runtimeException, webRequest);
        
        // Then
        Counter validationCounter = meterRegistry.find("http_errors_total")
            .tag("error_code", ErrorClassification.VALIDATION_ERROR)
            .counter();
        Counter runtimeCounter = meterRegistry.find("http_errors_total")
            .tag("error_code", ErrorClassification.RUNTIME_ERROR)
            .counter();
        
        assertNotNull(validationCounter);
        assertNotNull(runtimeCounter);
        assertEquals(2.0, validationCounter.count());
        assertEquals(1.0, runtimeCounter.count());
    }

    @Test
    @DisplayName("Should work gracefully when ErrorMetricsService is not available")
    void shouldWorkGracefullyWhenErrorMetricsServiceNotAvailable() {
        // Given
        GlobalExceptionHandler handlerWithoutMetrics = new GlobalExceptionHandler(systemOverloadDetector, null);
        IllegalArgumentException exception = new IllegalArgumentException("Invalid data");
        
        // When & Then - should not throw exception
        assertDoesNotThrow(() -> {
            ResponseEntity<ErrorResponseDTO> response = handlerWithoutMetrics.handleValidationError(exception, webRequest);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        });
    }

    @Test
    @DisplayName("Should include retry delay in overload response and metrics")
    void shouldIncludeRetryDelayInOverloadResponseAndMetrics() {
        // Given
        when(systemOverloadDetector.isInitialized()).thenReturn(true);
        when(systemOverloadDetector.calculateRetryDelay()).thenReturn(180);
        when(systemOverloadDetector.getThreadPoolUtilization()).thenReturn(0.92);
        when(systemOverloadDetector.getDatabaseConnectionUtilization()).thenReturn(0.88);
        when(systemOverloadDetector.getMemoryUtilization()).thenReturn(0.75);
        
        SystemOverloadException exception = new SystemOverloadException(
            "Thread pool exhausted", 120, "thread_pool_exhausted");
        
        // When
        ResponseEntity<ErrorResponseDTO> response = globalExceptionHandler.handleSystemOverload(exception, webRequest);
        
        // Then
        assertEquals("180", response.getHeaders().getFirst("Retry-After"));
        assertEquals(Integer.valueOf(180), response.getBody().getRetryAfter());
        
        // Verify retry delay is recorded in metrics
        assertEquals(180.0, meterRegistry.find("system_overload_retry_after_seconds").gauge().value());
    }

    @Test
    @DisplayName("Should record error severity levels correctly")
    void shouldRecordErrorSeverityLevelsCorrectly() {
        // Given
        SystemOverloadException criticalException = new SystemOverloadException(
            "System overloaded", 300, "memory_exhausted");
        IllegalArgumentException lowException = new IllegalArgumentException("Invalid input");
        RuntimeException highException = new RuntimeException("Database error");
        
        when(systemOverloadDetector.isInitialized()).thenReturn(true);
        when(systemOverloadDetector.calculateRetryDelay()).thenReturn(300);
        
        // When
        globalExceptionHandler.handleSystemOverload(criticalException, webRequest);
        globalExceptionHandler.handleValidationError(lowException, webRequest);
        globalExceptionHandler.handleRuntimeError(highException, webRequest);
        
        // Then
        Counter criticalCounter = meterRegistry.find("http_errors_total")
            .tag("severity", "CRITICAL").counter();
        Counter lowCounter = meterRegistry.find("http_errors_total")
            .tag("severity", "LOW").counter();
        Counter highCounter = meterRegistry.find("http_errors_total")
            .tag("severity", "HIGH").counter();
        
        assertNotNull(criticalCounter);
        assertNotNull(lowCounter);
        assertNotNull(highCounter);
        assertEquals(1.0, criticalCounter.count());
        assertEquals(1.0, lowCounter.count());
        assertEquals(1.0, highCounter.count());
    }

    @Test
    @DisplayName("Should record retryable vs non-retryable errors correctly")
    void shouldRecordRetryableVsNonRetryableErrorsCorrectly() {
        // Given
        SystemOverloadException retryableException = new SystemOverloadException(
            "Service overloaded", 300, "database_unavailable");
        IllegalArgumentException nonRetryableException = new IllegalArgumentException("Invalid format");
        
        when(systemOverloadDetector.isInitialized()).thenReturn(true);
        when(systemOverloadDetector.calculateRetryDelay()).thenReturn(300);
        
        // When
        globalExceptionHandler.handleSystemOverload(retryableException, webRequest);
        globalExceptionHandler.handleValidationError(nonRetryableException, webRequest);
        
        // Then
        Counter retryableCounter = meterRegistry.find("http_errors_total")
            .tag("retryable", "true").counter();
        Counter nonRetryableCounter = meterRegistry.find("http_errors_total")
            .tag("retryable", "false").counter();
        
        assertNotNull(retryableCounter);
        assertNotNull(nonRetryableCounter);
        assertEquals(1.0, retryableCounter.count());
        assertEquals(1.0, nonRetryableCounter.count());
    }

    @Test
    @DisplayName("Should handle metrics recording errors gracefully")
    void shouldHandleMetricsRecordingErrorsGracefully() {
        // Given - Create a metrics service that will throw an exception
        ErrorMetricsService faultyMetricsService = spy(errorMetricsService);
        doThrow(new RuntimeException("Metrics recording failed")).when(faultyMetricsService)
            .recordError(anyString(), anyInt(), anyString());
        
        GlobalExceptionHandler handlerWithFaultyMetrics = new GlobalExceptionHandler(
            systemOverloadDetector, faultyMetricsService);
        IllegalArgumentException exception = new IllegalArgumentException("Test error");
        
        // When & Then - should not throw exception despite metrics failure
        assertDoesNotThrow(() -> {
            ResponseEntity<ErrorResponseDTO> response = handlerWithFaultyMetrics.handleValidationError(exception, webRequest);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        });
    }
}