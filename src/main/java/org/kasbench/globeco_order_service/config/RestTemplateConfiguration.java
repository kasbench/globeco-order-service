package org.kasbench.globeco_order_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration class for RestTemplate with external service timeouts.
 */
@Configuration
public class RestTemplateConfiguration {

    @Value("${security.service.timeout:5000}")
    private int securityServiceTimeout;

    @Value("${portfolio.service.timeout:5000}")
    private int portfolioServiceTimeout;

    @Value("${trade.service.timeout:5000}")
    private int tradeServiceTimeout;

    /**
     * Creates a RestTemplate with configured timeouts for external services.
     * Uses the maximum timeout value among all services to ensure compatibility.
     * 
     * @param builder RestTemplateBuilder provided by Spring Boot
     * @return configured RestTemplate
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Use the maximum timeout to ensure all services work
        int maxTimeout = Math.max(Math.max(securityServiceTimeout, portfolioServiceTimeout), tradeServiceTimeout);
        
        return builder
                .connectTimeout(Duration.ofMillis(maxTimeout))
                .readTimeout(Duration.ofMillis(maxTimeout))
                .build();
    }
}