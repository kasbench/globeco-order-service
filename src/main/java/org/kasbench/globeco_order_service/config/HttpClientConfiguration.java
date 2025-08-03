package org.kasbench.globeco_order_service.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for HTTP client components used by the HttpMetricsService.
 * This configuration provides the necessary HTTP client infrastructure for
 * connection pool monitoring and metrics collection.
 */
@Configuration
@ConditionalOnProperty(name = "metrics.custom.enabled", havingValue = "true", matchIfMissing = false)
public class HttpClientConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpClientConfiguration.class);

    /**
     * Creates and configures a PoolingHttpClientConnectionManager for HTTP connection pooling.
     * This connection manager is used by the HttpMetricsService to monitor connection pool metrics.
     * 
     * @return configured PoolingHttpClientConnectionManager
     */
    @Bean
    @ConditionalOnProperty(name = "metrics.custom.http.enabled", havingValue = "true", matchIfMissing = true)
    public PoolingHttpClientConnectionManager poolingHttpClientConnectionManager() {
        logger.info("Creating PoolingHttpClientConnectionManager for HTTP metrics monitoring");
        
        try {
            // Create the connection manager with default configuration
            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
            
            // Configure connection pool settings
            connectionManager.setMaxTotal(100); // Maximum total connections
            connectionManager.setDefaultMaxPerRoute(20); // Maximum connections per route
            
            logger.info("PoolingHttpClientConnectionManager created successfully with maxTotal=100, maxPerRoute=20");
            return connectionManager;
            
        } catch (Exception e) {
            logger.error("Failed to create PoolingHttpClientConnectionManager: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to configure HTTP connection manager", e);
        }
    }

    /**
     * Creates a CloseableHttpClient using the configured connection manager.
     * This client is specifically used for metrics monitoring and HTTP connectivity.
     * 
     * @param connectionManager the connection manager to use
     * @return configured CloseableHttpClient for metrics
     */
    @Bean("metricsHttpClient")
    @ConditionalOnProperty(name = "metrics.custom.http.enabled", havingValue = "true", matchIfMissing = true)
    public CloseableHttpClient metricsHttpClient(PoolingHttpClientConnectionManager connectionManager) {
        logger.info("Creating CloseableHttpClient with connection pooling");
        
        try {
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .setConnectionManagerShared(true) // Allow sharing the connection manager
                    .build();
                    
            logger.info("CloseableHttpClient created successfully");
            return httpClient;
            
        } catch (Exception e) {
            logger.error("Failed to create CloseableHttpClient: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to configure HTTP client", e);
        }
    }
}