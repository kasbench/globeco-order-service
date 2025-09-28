package org.kasbench.globeco_order_service.controller;

import org.kasbench.globeco_order_service.dto.ErrorResponseDTO;
import org.kasbench.globeco_order_service.exception.SystemOverloadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for centralized error processing.
 * Handles system overload conditions and validation errors with proper HTTP status codes.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handles SystemOverloadException by returning 503 Service Unavailable with Retry-After header.
     * 
     * @param ex the SystemOverloadException
     * @param request the web request
     * @return ResponseEntity with 503 status and structured error response
     */
    @ExceptionHandler(SystemOverloadException.class)
    public ResponseEntity<ErrorResponseDTO> handleSystemOverload(
            SystemOverloadException ex, WebRequest request) {
        
        logger.warn("System overload detected: {} - Retry after {} seconds", 
                   ex.getMessage(), ex.getRetryAfterSeconds());
        
        // Create structured error response
        Map<String, Object> details = new HashMap<>();
        details.put("overloadReason", ex.getOverloadReason());
        details.put("recommendedAction", "retry_with_exponential_backoff");
        
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
            "SERVICE_OVERLOADED",
            "System temporarily overloaded - please retry in a few minutes",
            ex.getRetryAfterSeconds(),
            details
        );
        
        // Set Retry-After header
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        
        return new ResponseEntity<>(errorResponse, headers, HttpStatus.SERVICE_UNAVAILABLE);
    }
    
    /**
     * Handles IllegalArgumentException by returning 400 Bad Request for client validation errors.
     * 
     * @param ex the IllegalArgumentException
     * @param request the web request
     * @return ResponseEntity with 400 status and structured error response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationError(
            IllegalArgumentException ex, WebRequest request) {
        
        logger.debug("Validation error: {}", ex.getMessage());
        
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
            "VALIDATION_ERROR",
            ex.getMessage()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Handles generic exceptions by returning 500 Internal Server Error.
     * 
     * @param ex the generic Exception
     * @param request the web request
     * @return ResponseEntity with 500 status and structured error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericError(
            Exception ex, WebRequest request) {
        
        logger.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
            "INTERNAL_ERROR",
            "An unexpected error occurred. Please try again later."
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}