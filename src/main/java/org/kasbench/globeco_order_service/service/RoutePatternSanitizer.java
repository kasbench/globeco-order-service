package org.kasbench.globeco_order_service.service;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Utility class for sanitizing route patterns to prevent high cardinality issues
 * in HTTP request metrics while maintaining observability.
 * 
 * This sanitizer:
 * - Removes query parameters and fragments
 * - Limits path segments to prevent metric explosion
 * - Preserves route patterns like /api/users/{id}
 * - Handles null/empty paths gracefully
 * - Provides caching for performance optimization
 */
@Slf4j
public final class RoutePatternSanitizer {

    // Constants for sanitization
    private static final String UNKNOWN_PATH = "/unknown";
    private static final int MAX_PATH_SEGMENTS = 10;
    private static final String TRUNCATION_SUFFIX = "/...";
    
    // Patterns for sanitization
    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{[^}]+\\}");
    private static final Pattern MULTIPLE_SLASHES_PATTERN = Pattern.compile("/+");
    
    // Cache for sanitized paths to improve performance
    private static final ConcurrentHashMap<String, String> SANITIZATION_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;

    // Private constructor to prevent instantiation
    private RoutePatternSanitizer() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Sanitizes a route path to prevent high cardinality issues.
     * This method is thread-safe and uses caching for performance.
     * 
     * @param path the original path to sanitize
     * @return sanitized path suitable for metrics labeling
     */
    public static String sanitize(String path) {
        if (path == null || path.trim().isEmpty()) {
            return UNKNOWN_PATH;
        }
        
        // Check cache first
        String cachedResult = SANITIZATION_CACHE.get(path);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        try {
            String sanitized = performSanitization(path.trim());
            
            // Cache the result if cache is not too large
            if (SANITIZATION_CACHE.size() < MAX_CACHE_SIZE) {
                SANITIZATION_CACHE.put(path, sanitized);
            }
            
            return sanitized;
            
        } catch (Exception e) {
            log.warn("Failed to sanitize path '{}', using fallback: {}", path, e.getMessage());
            return UNKNOWN_PATH;
        }
    }

    /**
     * Performs the actual sanitization logic.
     * 
     * @param path the trimmed path to sanitize
     * @return sanitized path
     */
    private static String performSanitization(String path) {
        String sanitized = path;
        
        // Remove query parameters
        int queryIndex = sanitized.indexOf('?');
        if (queryIndex > 0) {
            sanitized = sanitized.substring(0, queryIndex);
        }
        
        // Remove fragment
        int fragmentIndex = sanitized.indexOf('#');
        if (fragmentIndex > 0) {
            sanitized = sanitized.substring(0, fragmentIndex);
        }
        
        // Normalize multiple slashes to single slash
        sanitized = MULTIPLE_SLASHES_PATTERN.matcher(sanitized).replaceAll("/");
        
        // Ensure path starts with /
        if (!sanitized.startsWith("/")) {
            sanitized = "/" + sanitized;
        }
        
        // Remove trailing slash unless it's the root path
        if (sanitized.length() > 1 && sanitized.endsWith("/")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        
        // Limit path segments to prevent high cardinality
        sanitized = limitPathSegments(sanitized);
        
        return sanitized;
    }

    /**
     * Limits the number of path segments to prevent metric explosion.
     * 
     * @param path the path to limit
     * @return path with limited segments
     */
    private static String limitPathSegments(String path) {
        String[] segments = path.split("/");
        
        if (segments.length <= MAX_PATH_SEGMENTS) {
            return path;
        }
        
        // Take first MAX_PATH_SEGMENTS segments and add truncation indicator
        String[] limitedSegments = Arrays.copyOf(segments, MAX_PATH_SEGMENTS);
        return String.join("/", limitedSegments) + TRUNCATION_SUFFIX;
    }

    /**
     * Checks if a path contains path variables (e.g., {id}, {userId}).
     * 
     * @param path the path to check
     * @return true if path contains path variables
     */
    public static boolean containsPathVariables(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return PATH_VARIABLE_PATTERN.matcher(path).find();
    }

    /**
     * Extracts the base path without path variables for grouping similar endpoints.
     * For example: "/api/users/{id}/orders/{orderId}" becomes "/api/users/orders"
     * 
     * @param path the path to process
     * @return base path without path variables
     */
    public static String extractBasePath(String path) {
        if (path == null || path.isEmpty()) {
            return UNKNOWN_PATH;
        }
        
        try {
            // Remove path variables
            String basePath = PATH_VARIABLE_PATTERN.matcher(path).replaceAll("");
            
            // Clean up double slashes that might result from variable removal
            basePath = MULTIPLE_SLASHES_PATTERN.matcher(basePath).replaceAll("/");
            
            // Remove trailing slash unless it's root
            if (basePath.length() > 1 && basePath.endsWith("/")) {
                basePath = basePath.substring(0, basePath.length() - 1);
            }
            
            return basePath.isEmpty() ? "/" : basePath;
            
        } catch (Exception e) {
            log.warn("Failed to extract base path from '{}': {}", path, e.getMessage());
            return UNKNOWN_PATH;
        }
    }

    /**
     * Validates that a path is suitable for use as a metric label.
     * 
     * @param path the path to validate
     * @return true if path is suitable for metrics
     */
    public static boolean isValidMetricPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        
        // Check for reasonable length
        if (path.length() > 200) {
            return false;
        }
        
        // Check for reasonable number of segments
        String[] segments = path.split("/");
        if (segments.length > MAX_PATH_SEGMENTS) {
            return false;
        }
        
        // Check for suspicious patterns that might indicate unsanitized data
        return !path.contains("?") && !path.contains("#") && !path.contains(" ");
    }

    /**
     * Gets statistics about the sanitization cache.
     * 
     * @return cache statistics string
     */
    public static String getCacheStatistics() {
        return String.format("RoutePatternSanitizer Cache - Size: %d/%d entries", 
                           SANITIZATION_CACHE.size(), MAX_CACHE_SIZE);
    }

    /**
     * Clears the sanitization cache. Useful for testing or memory management.
     */
    public static void clearCache() {
        SANITIZATION_CACHE.clear();
        log.debug("Cleared route pattern sanitization cache");
    }

    /**
     * Gets the maximum number of path segments allowed.
     * 
     * @return maximum path segments
     */
    public static int getMaxPathSegments() {
        return MAX_PATH_SEGMENTS;
    }

    /**
     * Gets the unknown path constant used for fallbacks.
     * 
     * @return unknown path string
     */
    public static String getUnknownPath() {
        return UNKNOWN_PATH;
    }
}