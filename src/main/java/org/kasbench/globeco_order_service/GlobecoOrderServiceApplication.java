package org.kasbench.globeco_order_service;

import org.kasbench.globeco_order_service.service.HttpMetricsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class GlobecoOrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(GlobecoOrderServiceApplication.class, args);
	}

	@Bean
	public RestTemplate restTemplate(HttpMetricsService httpMetricsService,
									 @Value("${portfolio.service.url:http://globeco-portfolio-service:8000}") String portfolioServiceUrl,
									 @Value("${security.service.url:http://globeco-security-service:8000}") String securityServiceUrl) {
		
		RestTemplate restTemplate = new RestTemplate();
		
		// Register HTTP connection pool metrics for service clients
		httpMetricsService.registerHttpConnectionPoolMetrics("portfolio-service", portfolioServiceUrl);
		httpMetricsService.registerHttpConnectionPoolMetrics("security-service", securityServiceUrl);
		
		return restTemplate;
	}

}
