package org.kasbench.globeco_order_service.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.dto.ErrorResponseDTO;
import org.kasbench.globeco_order_service.exception.SystemOverloadException;
import org.kasbench.globeco_order_service.service.SystemOverloadDetector;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Test for structured error response generation functionality.
 * Verifies that the GlobalExceptionHandler properly integrates with SystemOverloadDetector
 * to generate structured error responses with retry delay calculation.
 */
@ExtendWith(MockitoExtension.class)
class StructuredErrorResponseTest {
    
    private GlobalExceptionHandler exceptionHandler;
    
    @Mock
    private WebRequest webRequest;
    
    @Mock
    private SystemOverloadDetector systemOverloadDetector;
    
    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler(systemOverloadDetector);
        when(webRequest.getDescription(false)).thenReturn("uri=/api/v1/orders");
    }
    
    @Test
    void structuredErrorResponse_ShouldUseSystemDetectorForRetryDelay() {
        // Given
        SystemOverloadException exception = new SystemOverloadException(
            "System overloaded", 120, "thread_pool_overloaded");
        when(systemOverloadDetector.isInitialized()).thenReturn(true);
        when(systemOverloadDetector.calculateRetryDelay()).thenReturn(180);
        when(systemOverloadDetector.getThreadPoolUtilization()).thenReturn(0.85);
        when(systemOverloadDetector.getDatabaseConnectionUtilization()).thenReturn(0.92);
        when(systemOverloadDetector.getMemoryUtilization()).thenReturn(0.88);
        
        // When
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleSystemOverload(exception, webRequest);
        
        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("180", response.getHeaders().getFirst("Retry-After"));
        
        ErrorResponseDTO errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("SERVICE_OVERLOADED", errorResponse.getCode());
        assertEquals("System temporarily overloaded - please retry in a few minutes", errorResponse.getMessage());
        assertEquals(Integer.valueOf(180), errorResponse.getRetryAfter());
        assertNotNull(errorResponse.getTimestamp());
        
        // Verify structured details with system utilization
        assertNotNull(errorResponse.getDetails());
        assertEquals("thread_pool_overloaded", errorResponse.getDetails().get("overloadReason"));
        assertEquals("retry_with_exponential_backoff", errorResponse.getDetails().get("recommendedAction"));
        assertEquals("85.0%", errorResponse.getDetails().get("threadPoolUtilization"));
        assertEquals("92.0%", errorResponse.getDetails().get("databasePoolUtilization"));
        assertEquals("88.0%", errorResponse.getDetails().get("memoryUtilization"));
    }
    
    @Test
    void structuredErrorResponse_ShouldFallbackWhenDetectorUnavailable() {
        // Given
        SystemOverloadException exception = new SystemOverloadException(
            "System overloaded", 200, "memory_overloaded");
        when(systemOverloadDetector.isInitialized()).thenReturn(false);
        
        // When
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleSystemOverload(exception, webRequest);
        
        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("200", response.getHeaders().getFirst("Retry-After"));
        
        ErrorResponseDTO errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(Integer.valueOf(200), errorResponse.getRetryAfter());
        
        // Verify basic details without system utilization
        assertNotNull(errorResponse.getDetails());
        assertEquals("memory_overloaded", errorResponse.getDetails().get("overloadReason"));
        assertEquals("retry_with_exponential_backoff", errorResponse.getDetails().get("recommendedAction"));
        assertNull(errorResponse.getDetails().get("threadPoolUtilization"));
    }
    
    @Test
    void structuredErrorResponse_ShouldHandleDetectorException() {
        // Given
        SystemOverloadException exception = new SystemOverloadException(
            "System overloaded", 150, "database_overloaded");
        when(systemOverloadDetector.isInitialized()).thenReturn(true);
        when(systemOverloadDetector.calculateRetryDelay()).thenThrow(new RuntimeException("Detector failed"));
        
        // When
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleSystemOverload(exception, webRequest);
        
        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("150", response.getHeaders().getFirst("Retry-After"));
        
        ErrorResponseDTO errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(Integer.valueOf(150), errorResponse.getRetryAfter());
    }
    
    @Test
    void structuredErrorResponse_ShouldEnsureRetryAfterHeaderMatchesJsonField() {
        // Given
        SystemOverloadException exception = new SystemOverloadException("Overloaded", 240);
        when(systemOverloadDetector.isInitialized()).thenReturn(true);
        when(systemOverloadDetector.calculateRetryDelay()).thenReturn(300);
        
        // When
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleSystemOverload(exception, webRequest);
        
        // Then
        String headerValue = response.getHeaders().getFirst("Retry-After");
        Integer jsonValue = response.getBody().getRetryAfter();
        
        assertEquals(headerValue, jsonValue.toString());
        assertEquals("300", headerValue);
        assertEquals(Integer.valueOf(300), jsonValue);
    }
    
    @Test
    void structuredErrorResponse_ShouldGenerateConsistentStructureForAllErrorTypes() {
        // Given
        SystemOverloadException overloadException = new SystemOverloadException("Overloaded", 60);
        IllegalArgumentException validationException = new IllegalArgumentException("Invalid input");
        RuntimeException runtimeException = new RuntimeException("Runtime error");
        Exception genericException = new Exception("Generic error");
        when(systemOverloadDetector.isInitialized()).thenReturn(false);
        
        // When
        ResponseEntity<ErrorResponseDTO> overloadResponse = exceptionHandler.handleSystemOverload(overloadException, webRequest);
        ResponseEntity<ErrorResponseDTO> validationResponse = exceptionHandler.handleValidationError(validationException, webRequest);
        ResponseEntity<ErrorResponseDTO> runtimeResponse = exceptionHandler.handleRuntimeError(runtimeException, webRequest);
        ResponseEntity<ErrorResponseDTO> genericResponse = exceptionHandler.handleGenericError(genericException, webRequest);
        
        // Then - All responses should have consistent structure
        verifyConsistentErrorStructure(overloadResponse.getBody());
        verifyConsistentErrorStructure(validationResponse.getBody());
        verifyConsistentErrorStructure(runtimeResponse.getBody());
        verifyConsistentErrorStructure(genericResponse.getBody());
    }
    
    private void verifyConsistentErrorStructure(ErrorResponseDTO errorResponse) {
        assertNotNull(errorResponse);
        assertNotNull(errorResponse.getCode());
        assertNotNull(errorResponse.getMessage());
        assertNotNull(errorResponse.getTimestamp());
        // retryAfter and details are optional and may be null
    }
}