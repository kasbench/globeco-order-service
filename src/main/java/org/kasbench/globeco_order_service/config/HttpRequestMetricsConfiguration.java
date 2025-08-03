package org.kasbench.globeco_order_service.config;

import lombok.extern.slf4j.Slf4j;
import org.kasbench.globeco_order_service.service.HttpRequestMetricsInterceptor;
import org.kasbench.globeco_order_service.service.HttpRequestMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class for HTTP request metrics collection.
 * 
 * This configuration:
 * - Registers HttpRequestMetricsInterceptor with Spring's InterceptorRegistry
 * - Provides conditional enablement based on metrics.custom.enabled property
 * - Integrates with existing MetricsConfiguration and MetricsProperties
 * - Ensures HTTP request metrics are collected for all endpoints
 * 
 * The interceptor is registered with high precedence to ensure it captures
 * all HTTP requests before other interceptors process them.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
    value = {
        "metrics.custom.enabled",
        "metrics.custom.http.enabled", 
        "metrics.custom.http.request.enabled"
    },
    havingValue = "true"
)
public class HttpRequestMetricsConfiguration implements WebMvcConfigurer {

    private final HttpRequestMetricsInterceptor httpRequestMetricsInterceptor;
    private final HttpRequestMetricsService httpRequestMetricsService;
    private final MetricsProperties metricsProperties;

    @Autowired
    public HttpRequestMetricsConfiguration(
            HttpRequestMetricsInterceptor httpRequestMetricsInterceptor,
            HttpRequestMetricsService httpRequestMetricsService,
            MetricsProperties metricsProperties) {
        
        this.httpRequestMetricsInterceptor = httpRequestMetricsInterceptor;
        this.httpRequestMetricsService = httpRequestMetricsService;
        this.metricsProperties = metricsProperties;
        
        log.info("HttpRequestMetricsConfiguration initialized with interceptor: {}, service: {}", 
                httpRequestMetricsInterceptor != null ? "enabled" : "disabled",
                httpRequestMetricsService != null ? "enabled" : "disabled");
    }

    /**
     * Registers the HTTP request metrics interceptor with Spring's InterceptorRegistry.
     * The interceptor is added with high precedence (order 0) to ensure it runs
     * before other interceptors and captures all HTTP requests.
     * 
     * @param registry the interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (httpRequestMetricsInterceptor != null) {
            registry.addInterceptor(httpRequestMetricsInterceptor)
                    .addPathPatterns("/**")  // Apply to all paths
                    .order(0);  // High precedence to run first
            
            log.info("Registered HttpRequestMetricsInterceptor for all paths with order 0");
        } else {
            log.warn("HttpRequestMetricsInterceptor is not available, skipping registration");
        }
    }

    /**
     * Initializes HTTP request metrics configuration after dependency injection.
     * Validates that all required components are available and properly configured.
     */
    @PostConstruct
    public void initializeHttpRequestMetricsConfiguration() {
        log.info("Initializing HTTP request metrics configuration");
        
        try {
            // Validate required components
            validateConfiguration();
            
            // Log configuration details
            logConfigurationDetails();
            
            log.info("HTTP request metrics configuration initialized successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize HTTP request metrics configuration: {}", e.getMessage(), e);
            
            // Check if we should fail on error
            if (metricsProperties.getInitialization().isFailOnError()) {
                throw new RuntimeException("HTTP request metrics configuration initialization failed", e);
            }
        }
    }

    /**
     * Validates that all required components are available and properly configured.
     */
    private void validateConfiguration() {
        if (httpRequestMetricsInterceptor == null) {
            throw new IllegalStateException("HttpRequestMetricsInterceptor is not available");
        }
        
        if (httpRequestMetricsService == null) {
            throw new IllegalStateException("HttpRequestMetricsService is not available");
        }
        
        if (!httpRequestMetricsService.isInitialized()) {
            log.warn("HttpRequestMetricsService is not properly initialized");
        }
        
        log.debug("HTTP request metrics configuration validation successful");
    }

    /**
     * Logs configuration details for debugging and monitoring purposes.
     */
    private void logConfigurationDetails() {
        log.info("HTTP Request Metrics Configuration:");
        log.info("  - Interceptor enabled: {}", httpRequestMetricsInterceptor != null);
        log.info("  - Service enabled: {}", httpRequestMetricsService != null);
        log.info("  - Service initialized: {}", 
                httpRequestMetricsService != null ? httpRequestMetricsService.isInitialized() : false);
        
        if (metricsProperties.getInitialization().isVerboseLogging()) {
            log.info("  - HTTP metrics enabled: {}", metricsProperties.getHttp().isEnabled());
            log.info("  - Collection interval: {}", metricsProperties.getEffectiveHttpCollectionInterval());
            log.info("  - Per-route metrics: {}", metricsProperties.getHttp().isPerRouteMetricsEnabled());
            log.info("  - Connection timing: {}", metricsProperties.getHttp().isConnectionTimingEnabled());
            log.info("  - Max monitored services: {}", metricsProperties.getHttp().getMaxMonitoredServices());
            log.info("  - Utilization warning threshold: {}", metricsProperties.getHttp().getUtilizationWarningThreshold());
            log.info("  - Statistics timeout: {}ms", metricsProperties.getHttp().getStatisticsTimeoutMs());
        }
        
        if (httpRequestMetricsService != null) {
            log.debug("  - Service status: {}", httpRequestMetricsService.getServiceStatus());
            log.debug("  - Cache statistics: {}", httpRequestMetricsService.getCacheStatistics());
        }
    }

    /**
     * Gets the current status of HTTP request metrics configuration.
     * 
     * @return status string describing the current configuration state
     */
    public String getConfigurationStatus() {
        StringBuilder status = new StringBuilder();
        status.append("HTTP Request Metrics Configuration Status:\n");
        status.append("  - Configuration enabled: true\n");
        status.append("  - Interceptor available: ").append(httpRequestMetricsInterceptor != null).append("\n");
        status.append("  - Service available: ").append(httpRequestMetricsService != null).append("\n");
        
        if (httpRequestMetricsService != null) {
            status.append("  - Service initialized: ").append(httpRequestMetricsService.isInitialized()).append("\n");
            status.append("  - In-flight requests: ").append(httpRequestMetricsService.getInFlightRequests()).append("\n");
        }
        
        status.append("  - HTTP metrics enabled in properties: ").append(metricsProperties.getHttp().isEnabled()).append("\n");
        status.append("  - Collection interval: ").append(metricsProperties.getEffectiveHttpCollectionInterval()).append("\n");
        
        return status.toString();
    }

    /**
     * Checks if HTTP request metrics are fully operational.
     * 
     * @return true if all components are available and initialized
     */
    public boolean isOperational() {
        return httpRequestMetricsInterceptor != null && 
               httpRequestMetricsService != null && 
               httpRequestMetricsService.isInitialized() &&
               metricsProperties.getHttp().isEnabled();
    }

    /**
     * Gets the metrics properties for external access.
     * 
     * @return the metrics properties configuration
     */
    public MetricsProperties getMetricsProperties() {
        return metricsProperties;
    }
}