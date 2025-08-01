package org.kasbench.globeco_order_service.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for collecting and exposing HTTP connection pool metrics.
 * Provides infrastructure for HTTP connection pool monitoring with protocol labels.
 * This implementation provides a foundation that can be enhanced with actual
 * connection pool integration when Apache HttpClient is properly configured.
 */
@Service
@ConditionalOnProperty(name = "metrics.custom.http.enabled", havingValue = "true", matchIfMissing = false)
public class HttpMetricsService {
    private static final Logger logger = LoggerFactory.getLogger(HttpMetricsService.class);
    
    private final MeterRegistry meterRegistry;
    private final Map<String, String> serviceProtocols;
    private final Map<String, HttpConnectionPoolMetrics> serviceMetrics;
    
    public HttpMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.serviceProtocols = new ConcurrentHashMap<>();
        this.serviceMetrics = new ConcurrentHashMap<>();
    }
    
    /**
     * Registers HTTP connection pool metrics for a service.
     * This method creates the metrics infrastructure and can be enhanced
     * to integrate with actual connection pool managers.
     * 
     * @param serviceName Name of the service (e.g., "portfolio-service", "security-service")
     * @param serviceUrl The service URL to determine protocol
     */
    public void registerHttpConnectionPoolMetrics(String serviceName, String serviceUrl) {
        if (serviceName == null || serviceName.trim().isEmpty()) {
            logger.warn("Cannot register HTTP metrics: serviceName is null or empty");
            return;
        }
        
        try {
            // Determine protocol from service URL
            String protocol = determineProtocol(serviceUrl);
            serviceProtocols.put(serviceName, protocol);
            
            // Create metrics for this service
            HttpConnectionPoolMetrics metrics = new HttpConnectionPoolMetrics();
            serviceMetrics.put(serviceName, metrics);
            
            registerHttpConnectionPoolGauges(serviceName, metrics, protocol);
            
            logger.info("HTTP connection pool metrics registered for service: {} with protocol: {}", 
                       serviceName, protocol);
                       
        } catch (Exception e) {
            logger.error("Failed to register HTTP connection pool metrics for service: {}", 
                        serviceName, e);
        }
    }
    
    /**
     * Registers HTTP connection pool gauge metrics for a specific service.
     */
    private void registerHttpConnectionPoolGauges(String serviceName, 
                                                 HttpConnectionPoolMetrics metrics,
                                                 String protocol) {
        
        // Total connections gauge
        Gauge.builder("http_pool_connections_total", metrics, m -> m.getTotalConnections())
                .description("Maximum HTTP connections allowed in the pool")
                .tag("service", serviceName)
                .tag("protocol", protocol)
                .register(meterRegistry);
        
        // Active connections gauge
        Gauge.builder("http_pool_connections_active", metrics, m -> m.getActiveConnections())
                .description("Currently active HTTP connections in the pool")
                .tag("service", serviceName)
                .tag("protocol", protocol)
                .register(meterRegistry);
        
        // Idle connections gauge
        Gauge.builder("http_pool_connections_idle", metrics, m -> m.getIdleConnections())
                .description("Currently idle HTTP connections in the pool")
                .tag("service", serviceName)
                .tag("protocol", protocol)
                .register(meterRegistry);
        
        logger.debug("Registered HTTP connection pool gauges for service: {} with protocol: {}", 
                    serviceName, protocol);
    }
    
    /**
     * Updates connection pool metrics for a service.
     * This method can be called to update the metrics values.
     */
    public void updateConnectionPoolMetrics(String serviceName, int total, int active, int idle) {
        HttpConnectionPoolMetrics metrics = serviceMetrics.get(serviceName);
        if (metrics != null) {
            metrics.setTotalConnections(total);
            metrics.setActiveConnections(active);
            metrics.setIdleConnections(idle);
            logger.debug("Updated HTTP connection pool metrics for service: {} - Total: {}, Active: {}, Idle: {}", 
                        serviceName, total, active, idle);
        }
    }
    
    /**
     * Updates connection pool metrics from the actual connection manager.
     * This method provides a placeholder for future integration with actual connection pool statistics.
     * Currently, it sets default values to demonstrate the metrics infrastructure.
     */
    public void updateConnectionPoolMetricsFromManager() {
        try {
            for (String serviceName : serviceMetrics.keySet()) {
                HttpConnectionPoolMetrics metrics = serviceMetrics.get(serviceName);
                if (metrics != null) {
                    // Set default values for demonstration
                    // In a real implementation, this would read from the actual connection pool
                    metrics.setTotalConnections(20); // Default max connections per route
                    metrics.setActiveConnections(2);  // Simulated active connections
                    metrics.setIdleConnections(5);    // Simulated idle connections
                    
                    logger.debug("Updated HTTP connection pool metrics for service: {} - Total: {}, Active: {}, Idle: {}", 
                                serviceName, 20, 2, 5);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to update connection pool metrics: {}", e.getMessage());
        }
    }
    
    /**
     * Determines the protocol (http/https) from a service URL.
     */
    private String determineProtocol(String serviceUrl) {
        if (serviceUrl == null || serviceUrl.trim().isEmpty()) {
            return "unknown";
        }
        
        try {
            URI uri = URI.create(serviceUrl.trim());
            String scheme = uri.getScheme();
            
            if ("https".equalsIgnoreCase(scheme)) {
                return "https";
            } else if ("http".equalsIgnoreCase(scheme)) {
                return "http";
            } else {
                return "unknown";
            }
        } catch (Exception e) {
            logger.debug("Failed to parse protocol from URL '{}': {}", serviceUrl, e.getMessage());
            return "unknown";
        }
    }
    
    /**
     * Gets the protocol for a specific service.
     */
    public String getServiceProtocol(String serviceName) {
        return serviceProtocols.get(serviceName);
    }
    
    /**
     * Gets all registered service names.
     */
    public java.util.Set<String> getRegisteredServices() {
        return serviceProtocols.keySet();
    }
    
    /**
     * Gets the current metrics for a service.
     */
    public HttpConnectionPoolMetrics getServiceMetrics(String serviceName) {
        return serviceMetrics.get(serviceName);
    }
    
    @PostConstruct
    public void initialize() {
        logger.info("HttpMetricsService initialized and ready to register HTTP connection pool metrics");
    }
    
    /**
     * Internal class to hold HTTP connection pool metrics.
     */
    public static class HttpConnectionPoolMetrics {
        private final AtomicInteger totalConnections = new AtomicInteger(0);
        private final AtomicInteger activeConnections = new AtomicInteger(0);
        private final AtomicInteger idleConnections = new AtomicInteger(0);
        
        public int getTotalConnections() {
            return totalConnections.get();
        }
        
        public void setTotalConnections(int total) {
            totalConnections.set(total);
        }
        
        public int getActiveConnections() {
            return activeConnections.get();
        }
        
        public void setActiveConnections(int active) {
            activeConnections.set(active);
        }
        
        public int getIdleConnections() {
            return idleConnections.get();
        }
        
        public void setIdleConnections(int idle) {
            idleConnections.set(idle);
        }
    }
}