package org.kasbench.globeco_order_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            // Extract request information
            String method = request.getMethod();
            String path = extractRoutePath(request, handler);
            
            // Create timing context and store in ThreadLocal
            RequestTimingContext context = new RequestTimingContext(method, path);
            REQUEST_CONTEXT.set(context);
            
            // Increment in-flight requests gauge
            metricsService.incrementInFlightRequests();
            
            log.trace("Started HTTP request metrics collection for {} {}", method, path);
            
            return true; // Continue processing
            
        } catch (Exception e) {
            log.warn("Failed to start HTTP request metrics collection: {}", e.getMessage());
            return true; // Continue processing even if metrics fail
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
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                              Object handler, Exception ex) {
        try {
            RequestTimingContext context = REQUEST_CONTEXT.get();
            if (context != null) {
                // Calculate duration and record metrics
                long durationNanos = System.nanoTime() - context.getStartTime();
                int statusCode = response.getStatus();
                
                metricsService.recordRequest(
                    context.getMethod(), 
                    context.getPath(), 
                    statusCode, 
                    durationNanos
                );
                
                log.trace("Completed HTTP request metrics collection for {} {} - {} ({}ns)", 
                         context.getMethod(), context.getPath(), statusCode, durationNanos);
            }
            
        } catch (Exception e) {
            log.warn("Failed to complete HTTP request metrics collection: {}", e.getMessage());
        } finally {
            // Always decrement in-flight requests gauge and clean up ThreadLocal
            try {
                metricsService.decrementInFlightRequests();
            } catch (Exception e) {
                log.warn("Failed to decrement in-flight requests: {}", e.getMessage());
            }
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
            // Try to get the best matching pattern from HandlerMapping
            String bestMatchingPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (bestMatchingPattern != null && !bestMatchingPattern.isEmpty()) {
                return HttpRequestMetricsService.sanitizeRoutePath(bestMatchingPattern);
            }
            
            // Try to get path within handler mapping
            String pathWithinHandlerMapping = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            if (pathWithinHandlerMapping != null && !pathWithinHandlerMapping.isEmpty()) {
                return HttpRequestMetricsService.sanitizeRoutePath(pathWithinHandlerMapping);
            }
            
            // For HandlerMethod, try to extract pattern from mapping
            if (handler instanceof HandlerMethod) {
                HandlerMethod handlerMethod = (HandlerMethod) handler;
                // Try to get mapping information if available
                String methodPattern = extractPatternFromHandlerMethod(handlerMethod);
                if (methodPattern != null) {
                    return HttpRequestMetricsService.sanitizeRoutePath(methodPattern);
                }
            }
            
            // Fallback to request URI (will be sanitized)
            String requestURI = request.getRequestURI();
            if (requestURI != null) {
                return HttpRequestMetricsService.sanitizeRoutePath(requestURI);
            }
            
            // Final fallback
            return "/unknown";
            
        } catch (Exception e) {
            log.debug("Failed to extract route pattern, using fallback: {}", e.getMessage());
            // Fallback to sanitized request URI
            String requestURI = request.getRequestURI();
            return requestURI != null ? 
                HttpRequestMetricsService.sanitizeRoutePath(requestURI) : "/unknown";
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
     * Inner class to hold request timing context information.
     * Used with ThreadLocal to maintain state across interceptor method calls.
     * Package-private for testing access.
     */
    static class RequestTimingContext {
        private final long startTime;
        private final String method;
        private final String path;

        public RequestTimingContext(String method, String path) {
            this.startTime = System.nanoTime();
            this.method = method;
            this.path = path;
        }

        public long getStartTime() {
            return startTime;
        }

        public String getMethod() {
            return method;
        }

        public String getPath() {
            return path;
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