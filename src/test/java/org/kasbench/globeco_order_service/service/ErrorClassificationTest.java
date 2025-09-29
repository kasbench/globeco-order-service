package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ErrorClassification utility class.
 * Tests error classification logic, severity levels, and retry behavior.
 */
class ErrorClassificationTest {

    @Test
    @DisplayName("Should not allow instantiation of utility class")
    void shouldNotAllowInstantiation() {
        // Test that the constructor is private by using reflection
        assertThrows(UnsupportedOperationException.class, () -> {
            try {
                java.lang.reflect.Constructor<ErrorClassification> constructor = 
                    ErrorClassification.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                constructor.newInstance();
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof UnsupportedOperationException) {
                    throw (UnsupportedOperationException) e.getCause();
                }
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "VALIDATION_ERROR", "INVALID_REQUEST_FORMAT", "MISSING_REQUIRED_FIELD", 
        "INVALID_FIELD_VALUE", "RATE_LIMITED", "TOO_MANY_REQUESTS",
        "AUTHENTICATION_ERROR", "AUTHORIZATION_ERROR", "ACCESS_DENIED",
        "BUSINESS_RULE_VIOLATION", "INSUFFICIENT_FUNDS", "INVALID_ORDER_STATE", "DUPLICATE_ORDER"
    })
    @DisplayName("Should correctly identify client errors (4xx)")
    void shouldIdentifyClientErrors(String errorCode) {
        assertTrue(ErrorClassification.isClientError(errorCode));
        assertFalse(ErrorClassification.isServerError(errorCode));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SERVICE_OVERLOADED", "DATABASE_UNAVAILABLE", "EXTERNAL_SERVICE_TIMEOUT",
        "THREAD_POOL_EXHAUSTED", "MEMORY_EXHAUSTED", "INTERNAL_ERROR",
        "RUNTIME_ERROR", "DATABASE_ERROR", "CONFIGURATION_ERROR",
        "EXTERNAL_SERVICE_ERROR", "PORTFOLIO_SERVICE_ERROR", 
        "SECURITY_SERVICE_ERROR", "TRADE_SERVICE_ERROR"
    })
    @DisplayName("Should correctly identify server errors (5xx)")
    void shouldIdentifyServerErrors(String errorCode) {
        assertTrue(ErrorClassification.isServerError(errorCode));
        assertFalse(ErrorClassification.isClientError(errorCode));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SERVICE_OVERLOADED", "DATABASE_UNAVAILABLE", "EXTERNAL_SERVICE_TIMEOUT",
        "THREAD_POOL_EXHAUSTED", "MEMORY_EXHAUSTED", "RATE_LIMITED", "TOO_MANY_REQUESTS",
        "EXTERNAL_SERVICE_ERROR", "PORTFOLIO_SERVICE_ERROR", 
        "SECURITY_SERVICE_ERROR", "TRADE_SERVICE_ERROR"
    })
    @DisplayName("Should correctly identify retryable errors")
    void shouldIdentifyRetryableErrors(String errorCode) {
        assertTrue(ErrorClassification.isRetryable(errorCode));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "VALIDATION_ERROR", "INVALID_REQUEST_FORMAT", "MISSING_REQUIRED_FIELD",
        "INVALID_FIELD_VALUE", "AUTHENTICATION_ERROR", "AUTHORIZATION_ERROR",
        "ACCESS_DENIED", "BUSINESS_RULE_VIOLATION", "INSUFFICIENT_FUNDS",
        "INVALID_ORDER_STATE", "DUPLICATE_ORDER", "INTERNAL_ERROR",
        "RUNTIME_ERROR", "DATABASE_ERROR", "CONFIGURATION_ERROR"
    })
    @DisplayName("Should correctly identify non-retryable errors")
    void shouldIdentifyNonRetryableErrors(String errorCode) {
        assertFalse(ErrorClassification.isRetryable(errorCode));
    }

    @Test
    @DisplayName("Should handle null error codes gracefully")
    void shouldHandleNullErrorCodes() {
        assertFalse(ErrorClassification.isClientError(null));
        assertFalse(ErrorClassification.isServerError(null));
        assertFalse(ErrorClassification.isRetryable(null));
        assertEquals("UNKNOWN", ErrorClassification.getSeverityLevel(null));
    }

    @Test
    @DisplayName("Should handle empty error codes gracefully")
    void shouldHandleEmptyErrorCodes() {
        assertFalse(ErrorClassification.isClientError(""));
        assertFalse(ErrorClassification.isServerError(""));
        assertFalse(ErrorClassification.isRetryable(""));
        assertEquals("LOW", ErrorClassification.getSeverityLevel(""));
    }

    @Test
    @DisplayName("Should assign CRITICAL severity to system overload errors")
    void shouldAssignCriticalSeverityToSystemOverloadErrors() {
        assertEquals("CRITICAL", ErrorClassification.getSeverityLevel(ErrorClassification.SERVICE_OVERLOADED));
        assertEquals("CRITICAL", ErrorClassification.getSeverityLevel(ErrorClassification.DATABASE_UNAVAILABLE));
        assertEquals("CRITICAL", ErrorClassification.getSeverityLevel(ErrorClassification.THREAD_POOL_EXHAUSTED));
        assertEquals("CRITICAL", ErrorClassification.getSeverityLevel(ErrorClassification.MEMORY_EXHAUSTED));
    }

    @Test
    @DisplayName("Should assign HIGH severity to internal system errors")
    void shouldAssignHighSeverityToInternalErrors() {
        assertEquals("HIGH", ErrorClassification.getSeverityLevel(ErrorClassification.EXTERNAL_SERVICE_TIMEOUT));
        assertEquals("HIGH", ErrorClassification.getSeverityLevel(ErrorClassification.INTERNAL_ERROR));
        assertEquals("HIGH", ErrorClassification.getSeverityLevel(ErrorClassification.DATABASE_ERROR));
        assertEquals("HIGH", ErrorClassification.getSeverityLevel(ErrorClassification.CONFIGURATION_ERROR));
    }

    @Test
    @DisplayName("Should assign MEDIUM severity to rate limiting and external service errors")
    void shouldAssignMediumSeverityToRateLimitingErrors() {
        assertEquals("MEDIUM", ErrorClassification.getSeverityLevel(ErrorClassification.RATE_LIMITED));
        assertEquals("MEDIUM", ErrorClassification.getSeverityLevel(ErrorClassification.TOO_MANY_REQUESTS));
        assertEquals("MEDIUM", ErrorClassification.getSeverityLevel(ErrorClassification.EXTERNAL_SERVICE_ERROR));
        assertEquals("MEDIUM", ErrorClassification.getSeverityLevel(ErrorClassification.PORTFOLIO_SERVICE_ERROR));
        assertEquals("MEDIUM", ErrorClassification.getSeverityLevel(ErrorClassification.SECURITY_SERVICE_ERROR));
        assertEquals("MEDIUM", ErrorClassification.getSeverityLevel(ErrorClassification.TRADE_SERVICE_ERROR));
    }

    @Test
    @DisplayName("Should assign LOW severity to client errors and business rule violations")
    void shouldAssignLowSeverityToClientErrors() {
        assertEquals("LOW", ErrorClassification.getSeverityLevel(ErrorClassification.VALIDATION_ERROR));
        assertEquals("LOW", ErrorClassification.getSeverityLevel(ErrorClassification.INVALID_REQUEST_FORMAT));
        assertEquals("LOW", ErrorClassification.getSeverityLevel(ErrorClassification.AUTHENTICATION_ERROR));
        assertEquals("LOW", ErrorClassification.getSeverityLevel(ErrorClassification.BUSINESS_RULE_VIOLATION));
        assertEquals("LOW", ErrorClassification.getSeverityLevel(ErrorClassification.INSUFFICIENT_FUNDS));
    }

    @Test
    @DisplayName("Should assign LOW severity to unknown error codes")
    void shouldAssignLowSeverityToUnknownErrors() {
        assertEquals("LOW", ErrorClassification.getSeverityLevel("UNKNOWN_ERROR_CODE"));
        assertEquals("LOW", ErrorClassification.getSeverityLevel("CUSTOM_ERROR"));
    }

    @Test
    @DisplayName("Should have consistent error code constants")
    void shouldHaveConsistentErrorCodeConstants() {
        // Verify that all error codes are non-null and non-empty
        assertNotNull(ErrorClassification.SERVICE_OVERLOADED);
        assertNotNull(ErrorClassification.VALIDATION_ERROR);
        assertNotNull(ErrorClassification.RATE_LIMITED);
        assertNotNull(ErrorClassification.INTERNAL_ERROR);
        
        assertFalse(ErrorClassification.SERVICE_OVERLOADED.isEmpty());
        assertFalse(ErrorClassification.VALIDATION_ERROR.isEmpty());
        assertFalse(ErrorClassification.RATE_LIMITED.isEmpty());
        assertFalse(ErrorClassification.INTERNAL_ERROR.isEmpty());
    }

    @Test
    @DisplayName("Should correctly classify system overload related errors")
    void shouldCorrectlyClassifySystemOverloadErrors() {
        // System overload errors should be server errors, retryable, and critical
        String[] overloadErrors = {
            ErrorClassification.SERVICE_OVERLOADED,
            ErrorClassification.DATABASE_UNAVAILABLE,
            ErrorClassification.THREAD_POOL_EXHAUSTED,
            ErrorClassification.MEMORY_EXHAUSTED
        };
        
        for (String errorCode : overloadErrors) {
            assertTrue(ErrorClassification.isServerError(errorCode), 
                      "Error " + errorCode + " should be a server error");
            assertTrue(ErrorClassification.isRetryable(errorCode), 
                      "Error " + errorCode + " should be retryable");
            assertEquals("CRITICAL", ErrorClassification.getSeverityLevel(errorCode),
                        "Error " + errorCode + " should have CRITICAL severity");
        }
    }

    @Test
    @DisplayName("Should correctly classify validation errors")
    void shouldCorrectlyClassifyValidationErrors() {
        // Validation errors should be client errors, non-retryable, and low severity
        String[] validationErrors = {
            ErrorClassification.VALIDATION_ERROR,
            ErrorClassification.INVALID_REQUEST_FORMAT,
            ErrorClassification.MISSING_REQUIRED_FIELD,
            ErrorClassification.INVALID_FIELD_VALUE
        };
        
        for (String errorCode : validationErrors) {
            assertTrue(ErrorClassification.isClientError(errorCode), 
                      "Error " + errorCode + " should be a client error");
            assertFalse(ErrorClassification.isRetryable(errorCode), 
                       "Error " + errorCode + " should not be retryable");
            assertEquals("LOW", ErrorClassification.getSeverityLevel(errorCode),
                        "Error " + errorCode + " should have LOW severity");
        }
    }

    @Test
    @DisplayName("Should correctly classify rate limiting errors")
    void shouldCorrectlyClassifyRateLimitingErrors() {
        // Rate limiting errors should be client errors, retryable, and medium severity
        String[] rateLimitErrors = {
            ErrorClassification.RATE_LIMITED,
            ErrorClassification.TOO_MANY_REQUESTS
        };
        
        for (String errorCode : rateLimitErrors) {
            assertTrue(ErrorClassification.isClientError(errorCode), 
                      "Error " + errorCode + " should be a client error");
            assertTrue(ErrorClassification.isRetryable(errorCode), 
                      "Error " + errorCode + " should be retryable");
            assertEquals("MEDIUM", ErrorClassification.getSeverityLevel(errorCode),
                        "Error " + errorCode + " should have MEDIUM severity");
        }
    }
}