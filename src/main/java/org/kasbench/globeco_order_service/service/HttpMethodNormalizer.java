package org.kasbench.globeco_order_service.service;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for normalizing HTTP methods to ensure consistent formatting
 * in HTTP request metrics.
 * 
 * This normalizer:
 * - Converts methods to uppercase format
 * - Handles null/empty values gracefully
 * - Validates against standard HTTP methods
 * - Provides caching for performance optimization
 * - Ensures consistent labeling for metrics
 */
@Slf4j
public final class HttpMethodNormalizer {

    // Standard HTTP methods as defined in RFC 7231 and common extensions
    private static final Set<String> STANDARD_HTTP_METHODS = Set.of(
        "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE", "CONNECT"
    );
    
    // Constants
    private static final String UNKNOWN_METHOD = "UNKNOWN";
    
    // Cache for normalized methods to improve performance
    private static final ConcurrentHashMap<String, String> NORMALIZATION_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 50; // HTTP methods are limited, so small cache is sufficient

    // Private constructor to prevent instantiation
    private HttpMethodNormalizer() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Normalizes an HTTP method to ensure consistent formatting.
     * This method is thread-safe and uses caching for performance.
     * 
     * @param method the HTTP method to normalize
     * @return normalized HTTP method in uppercase
     */
    public static String normalize(String method) {
        // Handle null/empty cases first
        if (method == null) {
            log.trace("Method is null, using unknown method fallback");
            return UNKNOWN_METHOD;
        }
        
        String trimmedMethod = method.trim();
        if (trimmedMethod.isEmpty()) {
            log.trace("Method is empty after trimming, using unknown method fallback");
            return UNKNOWN_METHOD;
        }
        
        // Check cache first with error handling
        try {
            String cachedResult = NORMALIZATION_CACHE.get(method);
            if (cachedResult != null) {
                log.trace("Using cached normalization result for method: {}", method);
                return cachedResult;
            }
        } catch (Exception cacheError) {
            log.debug("Cache lookup failed for method '{}': {}", method, cacheError.getMessage());
            // Continue with normalization even if cache fails
        }
        
        try {
            String normalized = performNormalization(trimmedMethod);
            
            // Cache the result if cache is not too large and caching doesn't fail
            try {
                if (NORMALIZATION_CACHE.size() < MAX_CACHE_SIZE) {
                    NORMALIZATION_CACHE.put(method, normalized);
                    log.trace("Cached normalization result for method: {}", method);
                }
            } catch (Exception cacheError) {
                log.debug("Failed to cache normalization result for method '{}': {}", method, cacheError.getMessage());
                // Continue even if caching fails
            }
            
            return normalized;
            
        } catch (Exception e) {
            log.warn("Failed to normalize HTTP method '{}', using fallback: {}", method, e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Method normalization error details", e);
            }
            return UNKNOWN_METHOD;
        }
    }

    /**
     * Performs the actual normalization logic.
     * 
     * @param method the trimmed method to normalize
     * @return normalized method
     */
    private static String performNormalization(String method) {
        if (method == null || method.isEmpty()) {
            return UNKNOWN_METHOD;
        }
        
        String normalized;
        
        try {
            // Convert to uppercase
            normalized = method.toUpperCase();
        } catch (Exception e) {
            log.debug("Failed to convert method '{}' to uppercase: {}", method, e.getMessage());
            return UNKNOWN_METHOD;
        }
        
        try {
            // Validate against standard methods and handle edge cases
            if (isStandardHttpMethod(normalized)) {
                return normalized;
            }
        } catch (Exception e) {
            log.debug("Failed to check if method '{}' is standard: {}", normalized, e.getMessage());
        }
        
        try {
            // Handle common variations and typos
            normalized = handleCommonVariations(normalized);
            
            // Check again after handling variations
            if (isStandardHttpMethod(normalized)) {
                return normalized;
            }
        } catch (Exception e) {
            log.debug("Failed to handle variations for method '{}': {}", normalized, e.getMessage());
        }
        
        try {
            // If still not standard, check if it's a reasonable custom method
            if (isReasonableCustomMethod(normalized)) {
                log.debug("Using non-standard HTTP method: {}", normalized);
                return normalized;
            }
        } catch (Exception e) {
            log.debug("Failed to validate custom method '{}': {}", normalized, e.getMessage());
        }
        
        // Fallback for unrecognized methods
        log.debug("Unrecognized HTTP method '{}', using fallback", method);
        return UNKNOWN_METHOD;
    }

    /**
     * Checks if a method is a standard HTTP method.
     * 
     * @param method the method to check (should be uppercase)
     * @return true if it's a standard HTTP method
     */
    private static boolean isStandardHttpMethod(String method) {
        return STANDARD_HTTP_METHODS.contains(method);
    }

    /**
     * Handles common variations and typos in HTTP method names.
     * 
     * @param method the method to process
     * @return corrected method or original if no correction needed
     */
    private static String handleCommonVariations(String method) {
        // Handle common typos or variations
        switch (method) {
            case "GETS":
                return "GET";
            case "POSTS":
                return "POST";
            case "PUTS":
                return "PUT";
            case "DELETES":
                return "DELETE";
            case "PATCHES":
                return "PATCH";
            case "OPTION":
                return "OPTIONS";
            default:
                return method;
        }
    }

    /**
     * Checks if a non-standard method is reasonable for use in metrics.
     * 
     * @param method the method to check
     * @return true if it's a reasonable custom method
     */
    private static boolean isReasonableCustomMethod(String method) {
        // Check basic criteria for custom methods
        if (method.length() > 20) {
            return false; // Too long
        }
        
        if (!method.matches("^[A-Z][A-Z0-9_-]*$")) {
            return false; // Invalid characters
        }
        
        // Allow reasonable custom methods (e.g., WebDAV methods like PROPFIND, MKCOL)
        return true;
    }

    /**
     * Validates that a method is suitable for use as a metric label.
     * 
     * @param method the method to validate
     * @return true if method is suitable for metrics
     */
    public static boolean isValidMetricMethod(String method) {
        if (method == null || method.isEmpty()) {
            return false;
        }
        
        // Check for reasonable length
        if (method.length() > 20) {
            return false;
        }
        
        // Check for valid characters (uppercase letters, numbers, underscore, hyphen)
        return method.matches("^[A-Z][A-Z0-9_-]*$");
    }

    /**
     * Gets all standard HTTP methods.
     * 
     * @return set of standard HTTP methods
     */
    public static Set<String> getStandardHttpMethods() {
        return Set.copyOf(STANDARD_HTTP_METHODS);
    }

    /**
     * Checks if a method is a standard HTTP method.
     * 
     * @param method the method to check
     * @return true if it's a standard HTTP method
     */
    public static boolean isStandard(String method) {
        if (method == null) {
            return false;
        }
        return STANDARD_HTTP_METHODS.contains(method.toUpperCase());
    }

    /**
     * Gets statistics about the normalization cache.
     * 
     * @return cache statistics string
     */
    public static String getCacheStatistics() {
        return String.format("HttpMethodNormalizer Cache - Size: %d/%d entries", 
                           NORMALIZATION_CACHE.size(), MAX_CACHE_SIZE);
    }

    /**
     * Clears the normalization cache. Useful for testing or memory management.
     */
    public static void clearCache() {
        NORMALIZATION_CACHE.clear();
        log.debug("Cleared HTTP method normalization cache");
    }

    /**
     * Gets the unknown method constant used for fallbacks.
     * 
     * @return unknown method string
     */
    public static String getUnknownMethod() {
        return UNKNOWN_METHOD;
    }
}