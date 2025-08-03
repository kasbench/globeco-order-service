package org.kasbench.globeco_order_service.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.pool.PoolStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;

/**
 * Service for collecting and exposing HTTP connection pool metrics.
 * Provides infrastructure for HTTP connection pool monitoring with protocol
 * labels.
 * This implementation provides a foundation that can be enhanced with actual
 * connection pool integration when Apache HttpClient is properly configured.
 */
@Service
@ConditionalOnProperty(name = "metrics.custom.enabled", havingValue = "true", matchIfMissing = false)
public class HttpMetricsService {
    private static final Logger logger = LoggerFactory.getLogger(HttpMetricsService.class);

    private final MeterRegistry meterRegistry;
    private final PoolingHttpClientConnectionManager connectionManager;
    private final Map<String, String> serviceProtocols;
    private final Map<String, HttpConnectionPoolMetrics> serviceMetrics;
    private final ScheduledExecutorService scheduler;

    public HttpMetricsService(MeterRegistry meterRegistry,
            @Autowired(required = false) PoolingHttpClientConnectionManager connectionManager) {
        logger.info("HttpMetricsService constructor called - MeterRegistry: {}, ConnectionManager: {}",
                meterRegistry != null ? meterRegistry.getClass().getSimpleName() : "null",
                connectionManager != null ? "available" : "null");

        this.meterRegistry = meterRegistry;
        this.connectionManager = connectionManager;
        this.serviceProtocols = new ConcurrentHashMap<>();
        this.serviceMetrics = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "http-metrics-updater");
            t.setDaemon(true);
            return t;
        });

        logger.info("HttpMetricsService constructor completed successfully");
    }

    /**
     * Registers HTTP connection pool metrics for a service.
     * This method creates the metrics infrastructure and can be enhanced
     * to integrate with actual connection pool managers.
     * 
     * @param serviceName Name of the service (e.g., "portfolio-service",
     *                    "security-service")
     * @param serviceUrl  The service URL to determine protocol
     */
    public void registerHttpConnectionPoolMetrics(String serviceName, String serviceUrl) {
        if (serviceName == null || serviceName.trim().isEmpty()) {
            logger.warn("Cannot register HTTP metrics: serviceName is null or empty");
            return;
        }

        // Use trimmed service name for consistency
        String trimmedServiceName = serviceName.trim();

        // Check if metrics are already registered for this service
        if (serviceMetrics.containsKey(trimmedServiceName)) {
            logger.debug("HTTP metrics already registered for service: {}, skipping duplicate registration",
                    trimmedServiceName);
            return;
        }

        try {
            // Determine protocol from service URL
            String protocol = determineProtocol(serviceUrl);

            // Create metrics for this service
            HttpConnectionPoolMetrics metrics = new HttpConnectionPoolMetrics();

            // Try to register gauges first - if this fails, don't add to maps
            registerHttpConnectionPoolGauges(trimmedServiceName, metrics, protocol);

            // Only add to maps if gauge registration succeeded
            serviceProtocols.put(trimmedServiceName, protocol);
            serviceMetrics.put(trimmedServiceName, metrics);

            logger.info("HTTP connection pool metrics registered for service: {} with protocol: {}",
                    trimmedServiceName, protocol);

        } catch (Exception e) {
            logger.error("Failed to register HTTP connection pool metrics for service: {}",
                    trimmedServiceName, e);
        }
    }

    /**
     * Registers HTTP connection pool gauge metrics for a specific service.
     */
    private void registerHttpConnectionPoolGauges(String serviceName,
            HttpConnectionPoolMetrics metrics,
            String protocol) {

        try {
            // Check if gauges already exist before registering
            String totalMetricName = "http_pool_connections_total";
            if (meterRegistry.find(totalMetricName).tag("service", serviceName).tag("protocol", protocol)
                    .gauge() == null) {
                Gauge.builder(totalMetricName, metrics, m -> m.getTotalConnections())
                        .description("Maximum HTTP connections allowed in the pool")
                        .tag("service", serviceName)
                        .tag("protocol", protocol)
                        .register(meterRegistry);
            } else {
                logger.debug("Gauge {} already exists for service: {}, protocol: {}", totalMetricName, serviceName,
                        protocol);
            }

            String activeMetricName = "http_pool_connections_active";
            if (meterRegistry.find(activeMetricName).tag("service", serviceName).tag("protocol", protocol)
                    .gauge() == null) {
                Gauge.builder(activeMetricName, metrics, m -> m.getActiveConnections())
                        .description("Currently active HTTP connections in the pool")
                        .tag("service", serviceName)
                        .tag("protocol", protocol)
                        .register(meterRegistry);
            } else {
                logger.debug("Gauge {} already exists for service: {}, protocol: {}", activeMetricName, serviceName,
                        protocol);
            }

            String idleMetricName = "http_pool_connections_idle";
            if (meterRegistry.find(idleMetricName).tag("service", serviceName).tag("protocol", protocol)
                    .gauge() == null) {
                Gauge.builder(idleMetricName, metrics, m -> m.getIdleConnections())
                        .description("Currently idle HTTP connections in the pool")
                        .tag("service", serviceName)
                        .tag("protocol", protocol)
                        .register(meterRegistry);
            } else {
                logger.debug("Gauge {} already exists for service: {}, protocol: {}", idleMetricName, serviceName,
                        protocol);
            }

            logger.debug("Registered HTTP connection pool gauges for service: {} with protocol: {}",
                    serviceName, protocol);

        } catch (Exception e) {
            logger.error("Failed to register HTTP connection pool gauges for service: {} with protocol: {}: {}",
                    serviceName, protocol, e.getMessage());
            throw e; // Re-throw to be handled by caller
        }
    }

    /**
     * Updates connection pool metrics for a service.
     * This method can be called to update the metrics values.
     */
    public void updateConnectionPoolMetrics(String serviceName, int total, int active, int idle) {
        logger.info("Updating HTTP connection pool metrics for service: {} - Total: {}, Active: {}, Idle: {}",
                serviceName, total, active, idle);
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
     * This method reads real connection pool statistics from the
     * PoolingHttpClientConnectionManager
     * and updates the metrics for all registered services.
     */
    public void updateConnectionPoolMetricsFromManager() {
        logger.error("=== updateConnectionPoolMetricsFromManager() called ===");
        logger.info("Updating HTTP connection pool metrics from manager");
        logger.info("Registered services count: {}", serviceMetrics.size());

        if (connectionManager == null) {
            logger.error("Connection manager not available, using default values for HTTP metrics");
            updateWithDefaultValues();
            return;
        }

        try {
            // Get total pool statistics
            PoolStats totalStats = connectionManager.getTotalStats();

            for (String serviceName : serviceMetrics.keySet()) {
                HttpConnectionPoolMetrics metrics = serviceMetrics.get(serviceName);
                if (metrics != null) {
                    // Use actual connection pool statistics
                    int maxTotal = connectionManager.getMaxTotal();
                    int leased = totalStats.getLeased();
                    int available = totalStats.getAvailable();
                    int pending = totalStats.getPending();

                    // Calculate idle connections (available connections that are not pending)
                    int idle = Math.max(0, available - pending);

                    metrics.setTotalConnections(maxTotal);
                    metrics.setActiveConnections(leased);
                    metrics.setIdleConnections(idle);

                    logger.debug(
                            "Updated HTTP connection pool metrics for service: {} - Total: {}, Active: {}, Idle: {}, Available: {}, Pending: {}",
                            serviceName, maxTotal, leased, idle, available, pending);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to update connection pool metrics from manager: {}", e.getMessage());
            // Fallback to default values if there's an error accessing the connection
            // manager
            updateWithDefaultValues();
        }
    }

    /**
     * Updates metrics with default values when the actual connection manager is not
     * available.
     * This provides a fallback mechanism to ensure metrics are still populated.
     */
    private void updateWithDefaultValues() {
        logger.error("=== updateWithDefaultValues() called ===");
        logger.info("Updating {} services with default values", serviceMetrics.size());

        try {
            for (String serviceName : serviceMetrics.keySet()) {
                HttpConnectionPoolMetrics metrics = serviceMetrics.get(serviceName);
                if (metrics != null) {
                    // Set realistic default values based on typical HTTP client configuration
                    metrics.setTotalConnections(20); // Default max connections per route
                    metrics.setActiveConnections(0); // No active connections when manager unavailable
                    metrics.setIdleConnections(0); // No idle connections when manager unavailable

                    logger.error(
                            "Updated HTTP connection pool metrics for service: {} with default values - Total: {}, Active: {}, Idle: {}",
                            serviceName, 20, 0, 0);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to update connection pool metrics with default values: {}", e.getMessage());
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
     * Checks if metrics are already registered for a specific service.
     */
    public boolean isServiceRegistered(String serviceName) {
        return serviceName != null && serviceMetrics.containsKey(serviceName.trim());
    }

    /**
     * Gets the current metrics for a service.
     */
    public HttpConnectionPoolMetrics getServiceMetrics(String serviceName) {
        return serviceMetrics.get(serviceName);
    }

    /**
     * Updates connection pool metrics for a specific service using per-route
     * statistics.
     * This method provides more accurate metrics by looking at route-specific
     * connection usage.
     */
    public void updateServiceSpecificMetrics(String serviceName, String serviceUrl) {
        logger.info("Updating service-specific metrics for service: {}", serviceName);
        if (connectionManager == null || serviceName == null || serviceUrl == null) {
            logger.error("Cannot update service-specific metrics: missing connection manager or service info");
            return;
        }

        try {
            HttpConnectionPoolMetrics metrics = serviceMetrics.get(serviceName);
            if (metrics == null) {
                logger.warn("No metrics found for service: {}", serviceName);
                return;
            }

            // Parse the service URL to create a route
            URI uri = URI.create(serviceUrl);
            HttpHost httpHost = new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());

            // Get route-specific statistics using HttpRoute
            HttpRoute route = new HttpRoute(httpHost);
            PoolStats routeStats = connectionManager.getStats(route);
            int maxPerRoute = connectionManager.getDefaultMaxPerRoute();

            // Update metrics with route-specific data
            metrics.setTotalConnections(maxPerRoute);
            metrics.setActiveConnections(routeStats.getLeased());
            metrics.setIdleConnections(Math.max(0, routeStats.getAvailable() - routeStats.getPending()));

            logger.info("Updated service-specific HTTP metrics for {}: Total: {}, Active: {}, Idle: {}",
                    serviceName, maxPerRoute, routeStats.getLeased(),
                    Math.max(0, routeStats.getAvailable() - routeStats.getPending()));

        } catch (Exception e) {
            logger.error("Failed to update service-specific metrics for {}: {}", serviceName, e.getMessage());
            // Fall back to total pool statistics
            updateConnectionPoolMetricsFromManager();
        }
    }

    /**
     * Gets detailed connection pool information for debugging and monitoring.
     */
    public String getConnectionPoolStatus() {
        if (connectionManager == null) {
            return "Connection manager not available";
        }

        try {
            PoolStats totalStats = connectionManager.getTotalStats();
            StringBuilder status = new StringBuilder();
            status.append("HTTP Connection Pool Status:\n");
            status.append("  Total Max: ").append(connectionManager.getMaxTotal()).append("\n");
            status.append("  Default Max Per Route: ").append(connectionManager.getDefaultMaxPerRoute()).append("\n");
            status.append("  Total Leased: ").append(totalStats.getLeased()).append("\n");
            status.append("  Total Available: ").append(totalStats.getAvailable()).append("\n");
            status.append("  Total Pending: ").append(totalStats.getPending()).append("\n");
            status.append("  Total Max: ").append(totalStats.getMax()).append("\n");
            status.append("  Registered Services: ").append(serviceMetrics.size());

            return status.toString();
        } catch (Exception e) {
            return "Failed to get connection pool status: " + e.getMessage();
        }
    }

    /**
     * Forces an immediate update of all HTTP connection pool metrics.
     * This method is useful for testing and debugging purposes.
     */
    public void forceMetricsUpdate() {
        logger.info("Forcing immediate HTTP metrics update");
        updateConnectionPoolMetricsFromManager();
    }

    /**
     * Initializes the HTTP metrics service and starts the periodic update task.
     * This method is called explicitly from MetricsConfiguration to ensure proper
     * initialization.
     */
    public void initializeHttpMetrics() {
        logger.error("=== HttpMetricsService initializeHttpMetrics() method called ===");
        logger.info("HttpMetricsService initialized and ready to register HTTP connection pool metrics");
        logger.info("Connection manager available: {}", connectionManager != null);
        logger.info("MeterRegistry available: {}", meterRegistry != null);

        // Start periodic metrics update task
        scheduler.scheduleAtFixedRate(this::updateConnectionPoolMetricsFromManager,
                10, // Initial delay
                30, // Update every 30 seconds
                TimeUnit.SECONDS);

        logger.error("=== HTTP metrics periodic update task started (30 second interval) ===");
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down HTTP metrics service");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("HTTP metrics service shutdown complete");
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