package org.kasbench.globeco_order_service.service;

/**
 * Error classification constants for different error types.
 * These constants are used for consistent error categorization across the application
 * and for metrics tracking and monitoring purposes.
 */
public final class ErrorClassification {
    
    // Private constructor to prevent instantiation
    private ErrorClassification() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // System overload and capacity related errors
    public static final String SERVICE_OVERLOADED = "SERVICE_OVERLOADED";
    public static final String DATABASE_UNAVAILABLE = "DATABASE_UNAVAILABLE";
    public static final String EXTERNAL_SERVICE_TIMEOUT = "EXTERNAL_SERVICE_TIMEOUT";
    public static final String THREAD_POOL_EXHAUSTED = "THREAD_POOL_EXHAUSTED";
    public static final String MEMORY_EXHAUSTED = "MEMORY_EXHAUSTED";
    
    // Client validation and request errors
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String INVALID_REQUEST_FORMAT = "INVALID_REQUEST_FORMAT";
    public static final String MISSING_REQUIRED_FIELD = "MISSING_REQUIRED_FIELD";
    public static final String INVALID_FIELD_VALUE = "INVALID_FIELD_VALUE";
    
    // Rate limiting errors
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";
    
    // Internal system errors
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String RUNTIME_ERROR = "RUNTIME_ERROR";
    public static final String DATABASE_ERROR = "DATABASE_ERROR";
    public static final String CONFIGURATION_ERROR = "CONFIGURATION_ERROR";
    
    // Authentication and authorization errors
    public static final String AUTHENTICATION_ERROR = "AUTHENTICATION_ERROR";
    public static final String AUTHORIZATION_ERROR = "AUTHORIZATION_ERROR";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    
    // External service integration errors
    public static final String EXTERNAL_SERVICE_ERROR = "EXTERNAL_SERVICE_ERROR";
    public static final String PORTFOLIO_SERVICE_ERROR = "PORTFOLIO_SERVICE_ERROR";
    public static final String SECURITY_SERVICE_ERROR = "SECURITY_SERVICE_ERROR";
    public static final String TRADE_SERVICE_ERROR = "TRADE_SERVICE_ERROR";
    
    // Business logic errors
    public static final String BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION";
    public static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    public static final String INVALID_ORDER_STATE = "INVALID_ORDER_STATE";
    public static final String DUPLICATE_ORDER = "DUPLICATE_ORDER";
    
    /**
     * Determines if an error code represents a client error (4xx status codes).
     * 
     * @param errorCode the error code to check
     * @return true if the error is a client error, false otherwise
     */
    public static boolean isClientError(String errorCode) {
        if (errorCode == null) {
            return false;
        }
        
        return errorCode.equals(VALIDATION_ERROR) ||
               errorCode.equals(INVALID_REQUEST_FORMAT) ||
               errorCode.equals(MISSING_REQUIRED_FIELD) ||
               errorCode.equals(INVALID_FIELD_VALUE) ||
               errorCode.equals(RATE_LIMITED) ||
               errorCode.equals(TOO_MANY_REQUESTS) ||
               errorCode.equals(AUTHENTICATION_ERROR) ||
               errorCode.equals(AUTHORIZATION_ERROR) ||
               errorCode.equals(ACCESS_DENIED) ||
               errorCode.equals(BUSINESS_RULE_VIOLATION) ||
               errorCode.equals(INSUFFICIENT_FUNDS) ||
               errorCode.equals(INVALID_ORDER_STATE) ||
               errorCode.equals(DUPLICATE_ORDER);
    }
    
    /**
     * Determines if an error code represents a server error (5xx status codes).
     * 
     * @param errorCode the error code to check
     * @return true if the error is a server error, false otherwise
     */
    public static boolean isServerError(String errorCode) {
        if (errorCode == null) {
            return false;
        }
        
        return errorCode.equals(SERVICE_OVERLOADED) ||
               errorCode.equals(DATABASE_UNAVAILABLE) ||
               errorCode.equals(EXTERNAL_SERVICE_TIMEOUT) ||
               errorCode.equals(THREAD_POOL_EXHAUSTED) ||
               errorCode.equals(MEMORY_EXHAUSTED) ||
               errorCode.equals(INTERNAL_ERROR) ||
               errorCode.equals(RUNTIME_ERROR) ||
               errorCode.equals(DATABASE_ERROR) ||
               errorCode.equals(CONFIGURATION_ERROR) ||
               errorCode.equals(EXTERNAL_SERVICE_ERROR) ||
               errorCode.equals(PORTFOLIO_SERVICE_ERROR) ||
               errorCode.equals(SECURITY_SERVICE_ERROR) ||
               errorCode.equals(TRADE_SERVICE_ERROR);
    }
    
    /**
     * Determines if an error code represents a retryable error.
     * 
     * @param errorCode the error code to check
     * @return true if the error is retryable, false otherwise
     */
    public static boolean isRetryable(String errorCode) {
        if (errorCode == null) {
            return false;
        }
        
        return errorCode.equals(SERVICE_OVERLOADED) ||
               errorCode.equals(DATABASE_UNAVAILABLE) ||
               errorCode.equals(EXTERNAL_SERVICE_TIMEOUT) ||
               errorCode.equals(THREAD_POOL_EXHAUSTED) ||
               errorCode.equals(MEMORY_EXHAUSTED) ||
               errorCode.equals(RATE_LIMITED) ||
               errorCode.equals(TOO_MANY_REQUESTS) ||
               errorCode.equals(EXTERNAL_SERVICE_ERROR) ||
               errorCode.equals(PORTFOLIO_SERVICE_ERROR) ||
               errorCode.equals(SECURITY_SERVICE_ERROR) ||
               errorCode.equals(TRADE_SERVICE_ERROR);
    }
    
    /**
     * Gets the severity level for an error code.
     * 
     * @param errorCode the error code to check
     * @return severity level (CRITICAL, HIGH, MEDIUM, LOW)
     */
    public static String getSeverityLevel(String errorCode) {
        if (errorCode == null) {
            return "UNKNOWN";
        }
        
        // Critical errors that require immediate attention
        if (errorCode.equals(SERVICE_OVERLOADED) ||
            errorCode.equals(DATABASE_UNAVAILABLE) ||
            errorCode.equals(THREAD_POOL_EXHAUSTED) ||
            errorCode.equals(MEMORY_EXHAUSTED)) {
            return "CRITICAL";
        }
        
        // High severity errors that impact functionality
        if (errorCode.equals(EXTERNAL_SERVICE_TIMEOUT) ||
            errorCode.equals(INTERNAL_ERROR) ||
            errorCode.equals(DATABASE_ERROR) ||
            errorCode.equals(CONFIGURATION_ERROR)) {
            return "HIGH";
        }
        
        // Medium severity errors that may impact some users
        if (errorCode.equals(RATE_LIMITED) ||
            errorCode.equals(TOO_MANY_REQUESTS) ||
            errorCode.equals(EXTERNAL_SERVICE_ERROR) ||
            errorCode.equals(PORTFOLIO_SERVICE_ERROR) ||
            errorCode.equals(SECURITY_SERVICE_ERROR) ||
            errorCode.equals(TRADE_SERVICE_ERROR)) {
            return "MEDIUM";
        }
        
        // Low severity errors (client errors, business rule violations)
        return "LOW";
    }
}