package org.kasbench.globeco_order_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Standardized error response structure for all API errors.
 * Provides consistent error formatting with optional retry guidance.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDTO {
    
    private String code;
    private String message;
    private Integer retryAfter;
    private String timestamp;
    private Map<String, Object> details;
    
    public ErrorResponseDTO() {
        this.timestamp = Instant.now().toString();
    }
    
    public ErrorResponseDTO(String code, String message) {
        this();
        this.code = code;
        this.message = message;
    }
    
    public ErrorResponseDTO(String code, String message, Integer retryAfter) {
        this(code, message);
        this.retryAfter = retryAfter;
    }
    
    public ErrorResponseDTO(String code, String message, Integer retryAfter, Map<String, Object> details) {
        this(code, message, retryAfter);
        this.details = details;
    }
    
    // Getters and setters
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Integer getRetryAfter() {
        return retryAfter;
    }
    
    public void setRetryAfter(Integer retryAfter) {
        this.retryAfter = retryAfter;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public Map<String, Object> getDetails() {
        return details;
    }
    
    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}