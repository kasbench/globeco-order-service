package org.kasbench.globeco_order_service.config;

import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * Configuration class for HTTP client components used for external service communication.
 * This configuration provides optimized HTTP connection pooling for Portfolio Service,
 * Security Service, and Trade Service calls with proper connection reuse and keep-alive.
 */
@Configuration
public class HttpClientConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpClientConfiguration.class);
    
    // Connection pool configuration
    private static final int MAX_TOTAL_CONNECTIONS = 50;
    private static final int MAX_CONNECTIONS_PER_ROUTE = 25;
    private static final int KEEP_ALIVE_DURATION_MS = 30000; // 30 seconds
    
    // Timeout configuration
    private static final int CONNECT_TIMEOUT_MS = 5000; // 5 seconds
    private static final int READ_TIMEOUT_MS = 45000; // 45 seconds

    /**
     * Creates and configures a PoolingHttpClientConnectionManager for HTTP connection pooling.
     * This connection manager enables connection reuse across external service calls,
     * reducing connection establishment overhead.
     * 
     * @return configured PoolingHttpClientConnectionManager
     */
    @Bean
    @Primary
    public PoolingHttpClientConnectionManager poolingHttpClientConnectionManager() {
        logger.info("Creating PoolingHttpClientConnectionManager for external services");
        
        try {
            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
            
            // Configure connection pool settings for optimal performance
            connectionManager.setMaxTotal(MAX_TOTAL_CONNECTIONS);
            connectionManager.setDefaultMaxPerRoute(MAX_CONNECTIONS_PER_ROUTE);
            
            logger.info("PoolingHttpClientConnectionManager created successfully - maxTotal={}, maxPerRoute={}", 
                    MAX_TOTAL_CONNECTIONS, MAX_CONNECTIONS_PER_ROUTE);
            return connectionManager;
            
        } catch (Exception e) {
            logger.error("Failed to create PoolingHttpClientConnectionManager: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to configure HTTP connection manager", e);
        }
    }

    /**
     * Creates a keep-alive strategy that maintains connections for reuse.
     * If the server specifies a keep-alive timeout, it uses that value.
     * Otherwise, defaults to 30 seconds.
     * 
     * @return configured ConnectionKeepAliveStrategy
     */
    @Bean
    public ConnectionKeepAliveStrategy connectionKeepAliveStrategy() {
        return new ConnectionKeepAliveStrategy() {
            @Override
            public TimeValue getKeepAliveDuration(HttpResponse response, HttpContext context) {
                // Check if server specifies a keep-alive timeout
                Iterator<HeaderElement> it = MessageSupport.iterate(response, HeaderElements.KEEP_ALIVE);
                while (it.hasNext()) {
                    HeaderElement he = it.next();
                    String param = he.getName();
                    String value = he.getValue();
                    if (value != null && param.equalsIgnoreCase("timeout")) {
                        try {
                            return TimeValue.of(Long.parseLong(value), TimeUnit.SECONDS);
                        } catch (NumberFormatException ignore) {
                            // Ignore invalid timeout values
                        }
                    }
                }
                // Default to 30 seconds if not specified by server
                return TimeValue.of(KEEP_ALIVE_DURATION_MS, TimeUnit.MILLISECONDS);
            }
        };
    }

    /**
     * Creates a CloseableHttpClient using the configured connection manager and keep-alive strategy.
     * This client is used by RestTemplate for all external service calls.
     * 
     * @param connectionManager the connection manager to use
     * @param keepAliveStrategy the keep-alive strategy to use
     * @return configured CloseableHttpClient
     */
    @Bean
    @Primary
    public CloseableHttpClient httpClient(
            PoolingHttpClientConnectionManager connectionManager,
            ConnectionKeepAliveStrategy keepAliveStrategy) {
        logger.info("Creating CloseableHttpClient with connection pooling and keep-alive");
        
        try {
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .setConnectionManagerShared(true)
                    .setKeepAliveStrategy(keepAliveStrategy)
                    .build();
                    
            logger.info("CloseableHttpClient created successfully with {}s keep-alive", 
                    KEEP_ALIVE_DURATION_MS / 1000);
            return httpClient;
            
        } catch (Exception e) {
            logger.error("Failed to create CloseableHttpClient: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to configure HTTP client", e);
        }
    }

    /**
     * Creates a RestTemplate configured with the pooled HTTP client.
     * This RestTemplate is used by all external service clients (Portfolio, Security, Trade).
     * 
     * @param httpClient the configured HTTP client with connection pooling
     * @return configured RestTemplate
     */
    @Bean
    @Primary
    public RestTemplate restTemplate(CloseableHttpClient httpClient) {
        logger.info("Creating RestTemplate with pooled HTTP client");
        
        try {
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
            factory.setConnectionRequestTimeout(CONNECT_TIMEOUT_MS);
            factory.setReadTimeout(READ_TIMEOUT_MS);
            
            RestTemplate restTemplate = new RestTemplate(factory);
            
            logger.info("RestTemplate created successfully - connectTimeout={}ms, readTimeout={}ms", 
                    CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
            return restTemplate;
            
        } catch (Exception e) {
            logger.error("Failed to create RestTemplate: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to configure RestTemplate", e);
        }
    }

    /**
     * Creates a separate PoolingHttpClientConnectionManager for metrics monitoring.
     * This is isolated from the main connection pool to avoid interference.
     * 
     * @return configured PoolingHttpClientConnectionManager for metrics
     */
    @Bean("metricsConnectionManager")
    @ConditionalOnProperty(name = "metrics.custom.http.enabled", havingValue = "true", matchIfMissing = true)
    public PoolingHttpClientConnectionManager metricsConnectionManager() {
        logger.info("Creating separate PoolingHttpClientConnectionManager for metrics monitoring");
        
        try {
            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
            connectionManager.setMaxTotal(10);
            connectionManager.setDefaultMaxPerRoute(5);
            
            logger.info("Metrics PoolingHttpClientConnectionManager created successfully");
            return connectionManager;
            
        } catch (Exception e) {
            logger.error("Failed to create metrics PoolingHttpClientConnectionManager: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to configure metrics HTTP connection manager", e);
        }
    }

    /**
     * Creates a CloseableHttpClient specifically for metrics monitoring.
     * This client is isolated from the main HTTP client to avoid interference.
     * 
     * @param metricsConnectionManager the connection manager for metrics
     * @return configured CloseableHttpClient for metrics
     */
    @Bean("metricsHttpClient")
    @ConditionalOnProperty(name = "metrics.custom.http.enabled", havingValue = "true", matchIfMissing = true)
    public CloseableHttpClient metricsHttpClient(
            PoolingHttpClientConnectionManager metricsConnectionManager) {
        logger.info("Creating CloseableHttpClient for metrics monitoring");
        
        try {
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(metricsConnectionManager)
                    .setConnectionManagerShared(true)
                    .build();
                    
            logger.info("Metrics CloseableHttpClient created successfully");
            return httpClient;
            
        } catch (Exception e) {
            logger.error("Failed to create metrics CloseableHttpClient: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to configure metrics HTTP client", e);
        }
    }
}