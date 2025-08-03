package org.kasbench.globeco_order_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTP request metrics interceptor that captures timing and request information
 * for all HTTP requests processed by the application.
 * 
 * This interceptor:
 * - Starts timing when request processing begins (preHandle)
 * - Extracts route patterns from Spring HandlerMapping
 * - Records metrics when request processing completes (afterCompletion)
 * - Uses ThreadLocal to maintain timing context across interceptor methods
 * - Handles errors gracefully without impacting request processing
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "metrics.custom.enabled", havingValue = "true", matchIfMissing = false)
public class HttpRequestMetricsInterceptor implements HandlerInterceptor {

    private final HttpRequestMetricsService metricsService;
    
    // ThreadLocal to store request timing context across interceptor methods
    private static final ThreadLocal<RequestTimingContext> REQUEST_CONTEXT = new ThreadLocal<>();

    @Autowired
    public HttpRequestMetricsInterceptor(HttpRequestMetricsService metricsService) {
        this.metricsService = metricsService;
        log.info("HttpRequestMetricsInterceptor initialized with metrics service: {}", 
                metricsService != null ? "enabled" : "disabled");
    }

    /**
     * Called before the handler method is invoked.
     * Starts request timing and increments in-flight requests gauge.
     * 
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler chosen handler to execute
     * @return true to continue processing, false to abort
     */
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        try {
            // Validate that metrics service is available
            if (metricsService == null) {
                log.debug("HttpRequestMetricsService is null, skipping metrics collection");
                return true;
            }
            
            // Extract request information with fallbacks
            String method = extractHttpMethod(request);
            String path = extractRoutePath(request, handler);
            
            // Validate extracted information
            if (method == null || path == null) {
                log.debug("Failed to extract valid method or path (method: {}, path: {}), using fallbacks", method, path);
                method = method != null ? method : "UNKNOWN";
                path = path != null ? path : "/unknown";
            }
            
            // Create timing context and store in ThreadLocal
            RequestTimingContext context = new RequestTimingContext(method, path, metricsService);
            REQUEST_CONTEXT.set(context);
            
            log.trace("Started HTTP request metrics collection for {} {}", method, path);
            
            return true; // Continue processing
            
        } catch (Exception e) {
            log.warn("Failed to start HTTP request metrics collection for {} {}: {}", 
                    safeGetMethod(request), safeGetRequestURI(request), e.getMessage());
            
            // Ensure ThreadLocal is cleaned up even if setup fails
            REQUEST_CONTEXT.remove();
            
            // Log additional context for debugging
            if (log.isDebugEnabled()) {
                log.debug("Error details during preHandle", e);
            }
            
            return true; // Always continue processing even if metrics fail
        }
    }

    /**
     * Called after the complete request has finished.
     * Records metrics and decrements in-flight requests gauge.
     * 
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler the handler that was executed
     * @param ex exception thrown on handler execution, if any
     */
    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, 
                              @NonNull Object handler, @Nullable Exception ex) {
        RequestTimingContext context = null;
        try {
            context = REQUEST_CONTEXT.get();
            if (context != null) {
                // Extract status code with fallback handling
                int statusCode = extractStatusCode(response, ex);
                
                // Complete the timing context - this handles metric recording and in-flight decrement
                context.complete(statusCode);
                
                log.trace("Completed HTTP request metrics collection for {} {} - {}", 
                         context.getMethod(), context.getPath(), statusCode);
            } else {
                // Handle case where context is missing - this could happen if preHandle failed
                log.debug("No RequestTimingContext found in ThreadLocal for request completion - " +
                         "this may indicate preHandle failed or was not called");
                
                // Only attempt fallback recording if there are signs that a request was actually processed
                // (i.e., response has a meaningful status code set by the application)
                if (shouldRecordFallbackMetric(response, ex)) {
                    recordFallbackMetric(request, response, ex);
                }
            }
            
        } catch (Exception completionError) {
            log.warn("Failed to complete HTTP request metrics collection for {} {}: {}", 
                    safeGetMethod(request), safeGetRequestURI(request), completionError.getMessage());
            
            // Ensure cleanup even if completion fails
            if (context != null) {
                try {
                    context.cleanup();
                } catch (Exception cleanupEx) {
                    log.warn("Failed to cleanup RequestTimingContext during error recovery: {}", cleanupEx.getMessage());
                    if (log.isDebugEnabled()) {
                        log.debug("Cleanup error details", cleanupEx);
                    }
                }
            }
            
            // Log additional context for debugging
            if (log.isDebugEnabled()) {
                log.debug("Error details during afterCompletion", completionError);
            }
            
        } finally {
            // Always clean up ThreadLocal to prevent memory leaks
            REQUEST_CONTEXT.remove();
        }
    }

    /**
     * Extracts the route pattern from the request and handler information.
     * Uses Spring's HandlerMapping to get route patterns when available,
     * falls back to request URI with sanitization.
     * 
     * @param request the HTTP request
     * @param handler the handler object
     * @return sanitized route pattern
     */
    private String extractRoutePath(HttpServletRequest request, Object handler) {
        try {
            // Try multiple extraction strategies with fallbacks
            String routePath = tryExtractFromHandlerMapping(request);
            if (routePath != null) {
                return routePath;
            }
            
            routePath = tryExtractFromHandlerMethod(handler);
            if (routePath != null) {
                return routePath;
            }
            
            routePath = tryExtractFromRequestURI(request);
            if (routePath != null) {
                return routePath;
            }
            
            // Final fallback
            log.debug("All route extraction methods failed, using unknown path fallback");
            return RoutePatternSanitizer.getUnknownPath();
            
        } catch (Exception e) {
            log.warn("Exception during route pattern extraction, using fallback: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Route extraction error details", e);
            }
            
            // Emergency fallback - try to get something useful
            return safeGetRequestURI(request);
        }
    }
    
    /**
     * Attempts to extract route pattern from Spring HandlerMapping attributes.
     */
    private String tryExtractFromHandlerMapping(HttpServletRequest request) {
        try {
            // Try to get the best matching pattern from HandlerMapping
            String bestMatchingPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (bestMatchingPattern != null && !bestMatchingPattern.trim().isEmpty()) {
                String sanitized = RoutePatternSanitizer.sanitize(bestMatchingPattern);
                log.trace("Extracted route pattern from BEST_MATCHING_PATTERN_ATTRIBUTE: {}", sanitized);
                return sanitized;
            }
            
            // Try to get path within handler mapping
            String pathWithinHandlerMapping = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            if (pathWithinHandlerMapping != null && !pathWithinHandlerMapping.trim().isEmpty()) {
                String sanitized = RoutePatternSanitizer.sanitize(pathWithinHandlerMapping);
                log.trace("Extracted route pattern from PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE: {}", sanitized);
                return sanitized;
            }
            
            return null;
        } catch (Exception e) {
            log.debug("Failed to extract route pattern from HandlerMapping attributes: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Attempts to extract route pattern from HandlerMethod.
     */
    private String tryExtractFromHandlerMethod(Object handler) {
        try {
            if (handler instanceof HandlerMethod) {
                HandlerMethod handlerMethod = (HandlerMethod) handler;
                String methodPattern = extractPatternFromHandlerMethod(handlerMethod);
                if (methodPattern != null && !methodPattern.trim().isEmpty()) {
                    String sanitized = RoutePatternSanitizer.sanitize(methodPattern);
                    log.trace("Extracted route pattern from HandlerMethod: {}", sanitized);
                    return sanitized;
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("Failed to extract route pattern from HandlerMethod: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Attempts to extract route pattern from request URI as fallback.
     */
    private String tryExtractFromRequestURI(HttpServletRequest request) {
        try {
            String requestURI = request.getRequestURI();
            if (requestURI != null && !requestURI.trim().isEmpty()) {
                String sanitized = RoutePatternSanitizer.sanitize(requestURI);
                log.trace("Using sanitized request URI as route pattern: {}", sanitized);
                return sanitized;
            }
            return null;
        } catch (Exception e) {
            log.debug("Failed to extract route pattern from request URI: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Attempts to extract route pattern from HandlerMethod.
     * This is a best-effort approach that may not work in all cases.
     * 
     * @param handlerMethod the handler method
     * @return route pattern if extractable, null otherwise
     */
    private String extractPatternFromHandlerMethod(HandlerMethod handlerMethod) {
        try {
            // This is a simplified approach - in a real implementation,
            // you might need to inspect the method annotations more thoroughly
            // For now, we'll return null and rely on the HandlerMapping attributes
            return null;
        } catch (Exception e) {
            log.trace("Could not extract pattern from HandlerMethod: {}", e.getMessage());
            return null;
        }
    }



    /**
     * Safely extracts HTTP method from request with fallback.
     */
    private String extractHttpMethod(HttpServletRequest request) {
        try {
            String method = request.getMethod();
            return HttpMethodNormalizer.normalize(method);
        } catch (Exception e) {
            log.debug("Failed to extract HTTP method, using fallback: {}", e.getMessage());
            return HttpMethodNormalizer.getUnknownMethod();
        }
    }
    
    /**
     * Safely extracts status code from response with fallback handling.
     */
    private int extractStatusCode(HttpServletResponse response, Exception requestException) {
        try {
            int statusCode = response.getStatus();
            
            // If status is 0 or invalid, try to infer from exception
            if (statusCode <= 0 && requestException != null) {
                statusCode = inferStatusCodeFromException(requestException);
                log.debug("Inferred status code {} from exception: {}", statusCode, requestException.getClass().getSimpleName());
            }
            
            // Final fallback for invalid status codes
            if (statusCode <= 0) {
                statusCode = 500; // Internal Server Error as fallback
                log.debug("Using fallback status code 500 for invalid response status");
            }
            
            return statusCode;
        } catch (Exception e) {
            log.debug("Failed to extract status code, using fallback: {}", e.getMessage());
            return 500; // Internal Server Error as fallback
        }
    }
    
    /**
     * Attempts to infer status code from exception type.
     */
    private int inferStatusCodeFromException(Exception ex) {
        if (ex == null) {
            return 500;
        }
        
        String exceptionName = ex.getClass().getSimpleName().toLowerCase();
        
        // Common exception patterns
        if (exceptionName.contains("notfound") || exceptionName.contains("nosuch")) {
            return 404;
        } else if (exceptionName.contains("unauthorized") || exceptionName.contains("authentication")) {
            return 401;
        } else if (exceptionName.contains("forbidden") || exceptionName.contains("access")) {
            return 403;
        } else if (exceptionName.contains("badrequest") || exceptionName.contains("illegal") || exceptionName.contains("invalid")) {
            return 400;
        } else if (exceptionName.contains("timeout")) {
            return 504;
        } else if (exceptionName.contains("unavailable")) {
            return 503;
        }
        
        return 500; // Default to internal server error
    }
    
    /**
     * Determines if a fallback metric should be recorded based on response state.
     */
    private boolean shouldRecordFallbackMetric(HttpServletResponse response, Exception ex) {
        try {
            // If there's an exception, it suggests the request was processed
            if (ex != null) {
                return true;
            }
            
            // If response has a non-default status code, it suggests processing occurred
            int statusCode = response.getStatus();
            if (statusCode != 0 && statusCode != 200) {
                return true;
            }
            
            // For default status codes, be conservative and don't record
            // This avoids recording metrics for cases where afterCompletion
            // is called without actual request processing
            return false;
            
        } catch (Exception e) {
            log.debug("Failed to determine if fallback metric should be recorded: {}", e.getMessage());
            return false; // Be conservative on errors
        }
    }
    
    /**
     * Records a fallback metric when normal processing fails.
     */
    private void recordFallbackMetric(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        try {
            if (metricsService == null) {
                return;
            }
            
            String method = safeGetMethod(request);
            String path = safeGetRequestURI(request);
            int statusCode = extractStatusCode(response, ex);
            
            // Record with minimal timing (0 duration since we don't have start time)
            metricsService.recordRequest(method, path, statusCode, 0);
            
            log.debug("Recorded fallback metric for {} {} - {}", method, path, statusCode);
            
        } catch (Exception fallbackError) {
            log.debug("Failed to record fallback metric: {}", fallbackError.getMessage());
            // Don't log full stack trace for fallback failures to avoid noise
        }
    }
    
    /**
     * Safely gets HTTP method from request with fallback.
     */
    private String safeGetMethod(HttpServletRequest request) {
        try {
            String method = request != null ? request.getMethod() : null;
            return HttpMethodNormalizer.normalize(method);
        } catch (Exception e) {
            return HttpMethodNormalizer.getUnknownMethod();
        }
    }
    
    /**
     * Safely gets request URI from request with fallback.
     */
    private String safeGetRequestURI(HttpServletRequest request) {
        try {
            String uri = request != null ? request.getRequestURI() : null;
            return RoutePatternSanitizer.sanitize(uri);
        } catch (Exception e) {
            return RoutePatternSanitizer.getUnknownPath();
        }
    }

    /**
     * Gets the current request context for testing purposes.
     * Package-private for testing access.
     */
    static RequestTimingContext getCurrentContext() {
        return REQUEST_CONTEXT.get();
    }

    /**
     * Clears the current request context for testing purposes.
     * Package-private for testing access.
     */
    static void clearCurrentContext() {
        REQUEST_CONTEXT.remove();
    }
}