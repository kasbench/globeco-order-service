package org.kasbench.globeco_order_service.integration;

import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.controller.GlobalExceptionHandler;
import org.kasbench.globeco_order_service.dto.ErrorResponseDTO;
import org.kasbench.globeco_order_service.exception.SystemOverloadException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for GlobalExceptionHandler to verify it works correctly in Spring context.
 */
@SpringBootTest
class GlobalExceptionHandlerIntegrationTest {
    
    @Test
    void globalExceptionHandler_ShouldHandleSystemOverloadException() {
        // Given
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        SystemOverloadException exception = new SystemOverloadException(
            "System overloaded", 300, "database_connection_pool_exhausted");
        WebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest());
        
        // When
        ResponseEntity<ErrorResponseDTO> response = handler.handleSystemOverload(exception, webRequest);
        
        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("300", response.getHeaders().getFirst("Retry-After"));
        
        ErrorResponseDTO errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("SERVICE_OVERLOADED", errorResponse.getCode());
        assertEquals("System temporarily overloaded - please retry in a few minutes", errorResponse.getMessage());
        assertEquals(Integer.valueOf(300), errorResponse.getRetryAfter());
        assertNotNull(errorResponse.getTimestamp());
        assertNotNull(errorResponse.getDetails());
    }
    
    @Test
    void globalExceptionHandler_ShouldHandleValidationException() {
        // Given
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        IllegalArgumentException exception = new IllegalArgumentException("Invalid order data");
        WebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest());
        
        // When
        ResponseEntity<ErrorResponseDTO> response = handler.handleValidationError(exception, webRequest);
        
        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getHeaders().get("Retry-After"));
        
        ErrorResponseDTO errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("VALIDATION_ERROR", errorResponse.getCode());
        assertEquals("Invalid order data", errorResponse.getMessage());
        assertNull(errorResponse.getRetryAfter());
        assertNotNull(errorResponse.getTimestamp());
    }
    
    @Test
    void globalExceptionHandler_ShouldHandleGenericException() {
        // Given
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        RuntimeException exception = new RuntimeException("Unexpected error");
        WebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest());
        
        // When
        ResponseEntity<ErrorResponseDTO> response = handler.handleGenericError(exception, webRequest);
        
        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        ErrorResponseDTO errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("INTERNAL_ERROR", errorResponse.getCode());
        assertEquals("An unexpected error occurred. Please try again later.", errorResponse.getMessage());
        assertNull(errorResponse.getRetryAfter());
        assertNotNull(errorResponse.getTimestamp());
    }
}