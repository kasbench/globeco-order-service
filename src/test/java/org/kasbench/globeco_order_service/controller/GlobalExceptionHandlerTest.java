package org.kasbench.globeco_order_service.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.dto.ErrorResponseDTO;
import org.kasbench.globeco_order_service.exception.SystemOverloadException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GlobalExceptionHandler.
 * Tests proper status code mapping and structured error response generation.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {
    
    private GlobalExceptionHandler exceptionHandler;
    
    @Mock
    private WebRequest webRequest;
    
    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        when(webRequest.getDescription(false)).thenReturn("uri=/api/v1/orders");
    }
    
    @Test
    void handleSystemOverload_ShouldReturn503WithRetryAfterHeader() {
        // Given
        SystemOverloadException exception = new SystemOverloadException(
            "System overloaded", 300, "database_connection_pool_exhausted");
        
        // When
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleSystemOverload(exception, webRequest);
        
        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getHeaders().get("Retry-After"));
        assertEquals("300", response.getHeaders().getFirst("Retry-After"));
        
        ErrorResponseDTO errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("SERVICE_OVERLOADED", errorResponse.getCode());
        assertEquals("System temporarily overloaded - please retry in a few minutes", errorResponse.getMessage());
        assertEquals(Integer.valueOf(300), errorResponse.getRetryAfter());
        assertNotNull(errorResponse.getTimestamp());
        
        // Verify details
        assertNotNull(errorResponse.getDetails());
        assertEquals("database_connection_pool_exhausted", errorResponse.getDetails().get("overloadReason"));
        assertEquals("retry_with_exponential_backoff", errorResponse.getDetails().get("recommendedAction"));
    }
    
    @Test
    void handleSystemOverload_ShouldIncludeCorrectRetryAfterValue() {
        // Given
        SystemOverloadException exception = new SystemOverloadException(
            "Thread pool exhausted", 120, "thread_pool_overloaded");
        
        // When
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleSystemOverload(exception, webRequest);
        
        // Then
        assertEquals("120", response.getHeaders().getFirst("Retry-After"));
        assertEquals(Integer.valueOf(120), response.getBody().getRetryAfter());
    }
    
    @Test
    void handleValidationError_ShouldReturn400BadRequest() {
        // Given
        IllegalArgumentException exception = new IllegalArgumentException("Invalid order data: quantity must be positive");
        
        // When
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleValidationError(exception, webRequest);
        
        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getHeaders().get("Retry-After"));
        
        ErrorResponseDTO errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("VALIDATION_ERROR", errorResponse.getCode());
        assertEquals("Invalid order data: quantity must be positive", errorResponse.getMessage());
        assertNull(errorResponse.getRetryAfter());
        assertNotNull(errorResponse.getTimestamp());
        assertNull(errorResponse.getDetails());
    }
    
    @Test
    void handleValidationError_ShouldPreserveOriginalErrorMessage() {
        // Given
        String originalMessage = "Portfolio ID cannot be null";
        IllegalArgumentException exception = new IllegalArgumentException(originalMessage);
        
        // When
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleValidationError(exception, webRequest);
        
        // Then
        assertEquals(originalMessage, response.getBody().getMessage());
    }
    
    @Test
    void handleGenericError_ShouldReturn500InternalServerError() {
        // Given
        RuntimeException exception = new RuntimeException("Unexpected database error");
        
        // When
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleGenericError(exception, webRequest);
        
        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNull(response.getHeaders().get("Retry-After"));
        
        ErrorResponseDTO errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("INTERNAL_ERROR", errorResponse.getCode());
        assertEquals("An unexpected error occurred. Please try again later.", errorResponse.getMessage());
        assertNull(errorResponse.getRetryAfter());
        assertNotNull(errorResponse.getTimestamp());
        assertNull(errorResponse.getDetails());
    }
    
    @Test
    void handleGenericError_ShouldNotExposeInternalErrorDetails() {
        // Given
        RuntimeException exception = new RuntimeException("Database connection failed: password incorrect");
        
        // When
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleGenericError(exception, webRequest);
        
        // Then
        // Should return generic message, not expose internal details
        assertEquals("An unexpected error occurred. Please try again later.", response.getBody().getMessage());
        assertNotEquals("Database connection failed: password incorrect", response.getBody().getMessage());
    }
    
    @Test
    void errorResponses_ShouldHaveConsistentStructure() {
        // Given
        SystemOverloadException overloadException = new SystemOverloadException("Overloaded", 60);
        IllegalArgumentException validationException = new IllegalArgumentException("Invalid input");
        RuntimeException genericException = new RuntimeException("Generic error");
        
        // When
        ResponseEntity<ErrorResponseDTO> overloadResponse = exceptionHandler.handleSystemOverload(overloadException, webRequest);
        ResponseEntity<ErrorResponseDTO> validationResponse = exceptionHandler.handleValidationError(validationException, webRequest);
        ResponseEntity<ErrorResponseDTO> genericResponse = exceptionHandler.handleGenericError(genericException, webRequest);
        
        // Then - All responses should have consistent structure
        assertNotNull(overloadResponse.getBody().getCode());
        assertNotNull(overloadResponse.getBody().getMessage());
        assertNotNull(overloadResponse.getBody().getTimestamp());
        
        assertNotNull(validationResponse.getBody().getCode());
        assertNotNull(validationResponse.getBody().getMessage());
        assertNotNull(validationResponse.getBody().getTimestamp());
        
        assertNotNull(genericResponse.getBody().getCode());
        assertNotNull(genericResponse.getBody().getMessage());
        assertNotNull(genericResponse.getBody().getTimestamp());
    }
    
    @Test
    void handleSystemOverload_RetryAfterHeaderShouldMatchJsonField() {
        // Given
        SystemOverloadException exception = new SystemOverloadException("Overloaded", 180);
        
        // When
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleSystemOverload(exception, webRequest);
        
        // Then
        String headerValue = response.getHeaders().getFirst("Retry-After");
        Integer jsonValue = response.getBody().getRetryAfter();
        
        assertEquals(headerValue, jsonValue.toString());
    }
    
    @Test
    void handleSystemOverload_ShouldHandleZeroRetryAfter() {
        // Given
        SystemOverloadException exception = new SystemOverloadException("Overloaded", 0);
        
        // When
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleSystemOverload(exception, webRequest);
        
        // Then
        assertEquals("0", response.getHeaders().getFirst("Retry-After"));
        assertEquals(Integer.valueOf(0), response.getBody().getRetryAfter());
    }
    
    @Test
    void handleSystemOverload_ShouldHandleLargeRetryAfterValues() {
        // Given
        SystemOverloadException exception = new SystemOverloadException("Overloaded", 3600); // 1 hour
        
        // When
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleSystemOverload(exception, webRequest);
        
        // Then
        assertEquals("3600", response.getHeaders().getFirst("Retry-After"));
        assertEquals(Integer.valueOf(3600), response.getBody().getRetryAfter());
    }
}