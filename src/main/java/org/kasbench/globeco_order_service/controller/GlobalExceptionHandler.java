package org.kasbench.globeco_order_service.controller;

import org.kasbench.globeco_order_service.dto.ErrorResponseDTO;
import org.kasbench.globeco_order_service.exception.SystemOverloadException;
import org.kasbench.globeco_order_service.service.ErrorClassification;
import org.kasbench.globeco_order_service.service.ErrorMetricsService;
import org.kasbench.globeco_order_service.service.SystemOverloadDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    
    private final SystemOverloadDetector systemOverloadDetector;
    private final ErrorMetricsService errorMetricsService;
    
    @Autowired
    public GlobalExceptionHandler(SystemOverloadDetector systemOverloadDetector,
                                 @Autowired(required = false) ErrorMetricsService errorMetricsService) {
        this.systemOverloadDetector = systemOverloadDetector;
        this.errorMetricsService = errorMetricsService;
    }
    
    /**
     * Handles SystemOverloadException by returning 503 Service Unavailable with Retry-After header.
     * Uses SystemOverloadDetector to calculate retry delay based on current system resource utilization.
     * 
     * @param ex the SystemOverloadException
     * @param request the web request
     * @return ResponseEntity with 503 status and structured error response
     */
    @ExceptionHandler(SystemOverloadException.class)
    public ResponseEntity<ErrorResponseDTO> handleSystemOverload(
            SystemOverloadException ex, WebRequest request) {
        
        // Calculate retry delay based on current system resource utilization
        int retryDelay = calculateRetryDelayBasedOnSystemUtilization(ex.getRetryAfterSeconds());
        
        logger.warn("System overload detected: {} - Retry after {} seconds (calculated from system utilization)", 
                   ex.getMessage(), retryDelay);
        
        // Record error metrics
        recordErrorMetrics(ErrorClassification.SERVICE_OVERLOADED, HttpStatus.SERVICE_UNAVAILABLE.value(), 
                          ErrorClassification.getSeverityLevel(ErrorClassification.SERVICE_OVERLOADED));
        
        // Record system overload event
        if (errorMetricsService != null) {
            errorMetricsService.recordSystemOverload(ex.getOverloadReason(), retryDelay);
        }
        
        // Create structured error response with detailed system information
        Map<String, Object> details = createSystemOverloadDetails(ex.getOverloadReason());
        
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
            ErrorClassification.SERVICE_OVERLOADED,
            "System temporarily overloaded - please retry in a few minutes",
            retryDelay,
            details
        );
        
        // Set Retry-After header to match JSON response
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", String.valueOf(retryDelay));
        
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
        
        // Record error metrics
        recordErrorMetrics(ErrorClassification.VALIDATION_ERROR, HttpStatus.BAD_REQUEST.value(),
                          ErrorClassification.getSeverityLevel(ErrorClassification.VALIDATION_ERROR));
        
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
            ErrorClassification.VALIDATION_ERROR,
            ex.getMessage()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Handles RuntimeException by returning 500 Internal Server Error.
     * 
     * @param ex the RuntimeException
     * @param request the web request
     * @return ResponseEntity with 500 status and structured error response
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDTO> handleRuntimeError(
            RuntimeException ex, WebRequest request) {
        
        logger.error("Runtime error occurred: {}", ex.getMessage(), ex);
        
        // Record error metrics
        recordErrorMetrics(ErrorClassification.RUNTIME_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                          ErrorClassification.getSeverityLevel(ErrorClassification.RUNTIME_ERROR));
        
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
            ErrorClassification.RUNTIME_ERROR,
            "A runtime error occurred. Please try again later."
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * Handles generic exceptions by returning 500 Internal Server Error.
     * This is the catch-all handler for any unhandled exceptions.
     * 
     * @param ex the generic Exception
     * @param request the web request
     * @return ResponseEntity with 500 status and structured error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericError(
            Exception ex, WebRequest request) {
        
        logger.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        
        // Record error metrics
        recordErrorMetrics(ErrorClassification.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                          ErrorClassification.getSeverityLevel(ErrorClassification.INTERNAL_ERROR));
        
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
            ErrorClassification.INTERNAL_ERROR,
            "An unexpected error occurred. Please try again later."
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * Calculates retry delay based on current system resource utilization.
     * Uses SystemOverloadDetector to get real-time system metrics for accurate retry timing.
     * 
     * @param fallbackDelay fallback delay from exception if system detector is unavailable
     * @return calculated retry delay in seconds
     */
    private int calculateRetryDelayBasedOnSystemUtilization(int fallbackDelay) {
        try {
            if (systemOverloadDetector != null && systemOverloadDetector.isInitialized()) {
                int calculatedDelay = systemOverloadDetector.calculateRetryDelay();
                logger.debug("Calculated retry delay from system utilization: {} seconds", calculatedDelay);
                return calculatedDelay;
            } else {
                logger.debug("SystemOverloadDetector not available, using fallback delay: {} seconds", fallbackDelay);
                return fallbackDelay;
            }
        } catch (Exception e) {
            logger.warn("Failed to calculate retry delay from system utilization, using fallback: {} seconds", 
                       fallbackDelay, e);
            return fallbackDelay;
        }
    }
    
    /**
     * Creates detailed system overload information for error response.
     * Includes current resource utilization and recommended actions.
     * 
     * @param overloadReason the primary reason for overload
     * @return map containing detailed overload information
     */
    private Map<String, Object> createSystemOverloadDetails(String overloadReason) {
        Map<String, Object> details = new HashMap<>();
        
        try {
            details.put("overloadReason", overloadReason);
            details.put("recommendedAction", "retry_with_exponential_backoff");
            
            if (systemOverloadDetector != null && systemOverloadDetector.isInitialized()) {
                // Add current resource utilization for debugging/monitoring
                details.put("threadPoolUtilization", String.format("%.1f%%", 
                           systemOverloadDetector.getThreadPoolUtilization() * 100));
                details.put("databasePoolUtilization", String.format("%.1f%%", 
                           systemOverloadDetector.getDatabaseConnectionUtilization() * 100));
                details.put("memoryUtilization", String.format("%.1f%%", 
                           systemOverloadDetector.getMemoryUtilization() * 100));
            }
            
        } catch (Exception e) {
            logger.debug("Failed to gather system overload details: {}", e.getMessage());
            // Ensure basic details are always present
            details.put("overloadReason", overloadReason != null ? overloadReason : "unknown");
            details.put("recommendedAction", "retry_with_exponential_backoff");
        }
        
        return details;
    }
    
    /**
     * Records error metrics for monitoring and alerting purposes.
     * 
     * @param errorCode the error classification code
     * @param httpStatus the HTTP status code
     * @param severity the error severity level
     */
    private void recordErrorMetrics(String errorCode, int httpStatus, String severity) {
        try {
            if (errorMetricsService != null) {
                errorMetricsService.recordError(errorCode, httpStatus, severity);
                logger.debug("Recorded error metrics: code={}, status={}, severity={}", 
                           errorCode, httpStatus, severity);
            } else {
                logger.debug("ErrorMetricsService not available, skipping error metrics recording");
            }
        } catch (Exception e) {
            logger.warn("Failed to record error metrics: {}", e.getMessage());
        }
    }
}