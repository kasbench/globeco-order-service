package org.kasbench.globeco_order_service.service;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for handling and normalizing HTTP status codes for metrics.
 * 
 * This handler:
 * - Converts numeric status codes to string format
 * - Validates status codes against HTTP standards
 * - Handles edge cases and invalid codes gracefully
 * - Provides caching for performance optimization
 * - Ensures consistent labeling for metrics
 */
@Slf4j
public final class StatusCodeHandler {

    // Constants
    private static final String UNKNOWN_STATUS = "unknown";
    
    // Cache for normalized status codes to improve performance
    private static final ConcurrentHashMap<Integer, String> NORMALIZATION_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 100; // Status codes are limited, so small cache is sufficient

    // Private constructor to prevent instantiation
    private StatusCodeHandler() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Normalizes an HTTP status code to string format for metrics.
     * This method is thread-safe and uses caching for performance.
     * 
     * @param statusCode the HTTP status code to normalize
     * @return normalized status code as string
     */
    public static String normalize(int statusCode) {
        // Check cache first
        String cachedResult = NORMALIZATION_CACHE.get(statusCode);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        try {
            String normalized = performNormalization(statusCode);
            
            // Cache the result if cache is not too large
            if (NORMALIZATION_CACHE.size() < MAX_CACHE_SIZE) {
                NORMALIZATION_CACHE.put(statusCode, normalized);
            }
            
            return normalized;
            
        } catch (Exception e) {
            log.warn("Failed to normalize status code '{}', using fallback: {}", statusCode, e.getMessage());
            return UNKNOWN_STATUS;
        }
    }

    /**
     * Performs the actual normalization logic.
     * 
     * @param statusCode the status code to normalize
     * @return normalized status code
     */
    private static String performNormalization(int statusCode) {
        // Handle special cases
        if (statusCode <= 0) {
            return UNKNOWN_STATUS;
        }
        
        // Validate against HTTP status code ranges
        if (isValidHttpStatusCode(statusCode)) {
            return String.valueOf(statusCode);
        }
        
        // Handle edge cases for non-standard but reasonable codes
        if (statusCode >= 100 && statusCode <= 999) {
            log.debug("Using non-standard HTTP status code: {}", statusCode);
            return String.valueOf(statusCode);
        }
        
        // Fallback for invalid codes
        log.debug("Invalid HTTP status code '{}', using fallback", statusCode);
        return UNKNOWN_STATUS;
    }

    /**
     * Checks if a status code is a valid HTTP status code according to RFC standards.
     * 
     * @param statusCode the status code to validate
     * @return true if it's a valid HTTP status code
     */
    public static boolean isValidHttpStatusCode(int statusCode) {
        // HTTP status codes are defined in ranges:
        // 1xx: Informational responses
        // 2xx: Successful responses
        // 3xx: Redirection messages
        // 4xx: Client error responses
        // 5xx: Server error responses
        return statusCode >= 100 && statusCode <= 599;
    }

    /**
     * Gets the status code category (1xx, 2xx, 3xx, 4xx, 5xx).
     * 
     * @param statusCode the status code
     * @return status code category as string
     */
    public static String getStatusCategory(int statusCode) {
        if (statusCode < 100 || statusCode > 599) {
            return "unknown";
        }
        
        int category = statusCode / 100;
        return category + "xx";
    }

    /**
     * Checks if a status code indicates a successful response (2xx).
     * 
     * @param statusCode the status code to check
     * @return true if it's a success status code
     */
    public static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Checks if a status code indicates a client error (4xx).
     * 
     * @param statusCode the status code to check
     * @return true if it's a client error status code
     */
    public static boolean isClientError(int statusCode) {
        return statusCode >= 400 && statusCode < 500;
    }

    /**
     * Checks if a status code indicates a server error (5xx).
     * 
     * @param statusCode the status code to check
     * @return true if it's a server error status code
     */
    public static boolean isServerError(int statusCode) {
        return statusCode >= 500 && statusCode < 600;
    }

    /**
     * Checks if a status code indicates a redirection (3xx).
     * 
     * @param statusCode the status code to check
     * @return true if it's a redirection status code
     */
    public static boolean isRedirection(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
    }

    /**
     * Checks if a status code indicates an informational response (1xx).
     * 
     * @param statusCode the status code to check
     * @return true if it's an informational status code
     */
    public static boolean isInformational(int statusCode) {
        return statusCode >= 100 && statusCode < 200;
    }

    /**
     * Gets a human-readable description of common status codes.
     * 
     * @param statusCode the status code
     * @return description of the status code
     */
    public static String getStatusDescription(int statusCode) {
        switch (statusCode) {
            case 200: return "OK";
            case 201: return "Created";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 409: return "Conflict";
            case 422: return "Unprocessable Entity";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            case 504: return "Gateway Timeout";
            default:
                if (isValidHttpStatusCode(statusCode)) {
                    return getStatusCategory(statusCode) + " Response";
                } else {
                    return "Unknown Status";
                }
        }
    }

    /**
     * Validates that a status code is suitable for use as a metric label.
     * 
     * @param statusCode the status code to validate
     * @return true if status code is suitable for metrics
     */
    public static boolean isValidMetricStatusCode(int statusCode) {
        // Allow any reasonable status code for metrics
        return statusCode > 0 && statusCode <= 999;
    }

    /**
     * Gets statistics about the normalization cache.
     * 
     * @return cache statistics string
     */
    public static String getCacheStatistics() {
        return String.format("StatusCodeHandler Cache - Size: %d/%d entries", 
                           NORMALIZATION_CACHE.size(), MAX_CACHE_SIZE);
    }

    /**
     * Clears the normalization cache. Useful for testing or memory management.
     */
    public static void clearCache() {
        NORMALIZATION_CACHE.clear();
        log.debug("Cleared status code normalization cache");
    }

    /**
     * Gets the unknown status constant used for fallbacks.
     * 
     * @return unknown status string
     */
    public static String getUnknownStatus() {
        return UNKNOWN_STATUS;
    }
}