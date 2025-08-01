package org.kasbench.globeco_order_service;

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
	public RestTemplate restTemplate(@Value("${portfolio.service.url:http://globeco-portfolio-service:8000}") String portfolioServiceUrl,
									 @Value("${security.service.url:http://globeco-security-service:8000}") String securityServiceUrl) {
		
		RestTemplate restTemplate = new RestTemplate();
		
		// Note: HTTP metrics registration is handled by MetricsConfiguration when metrics are enabled
		// This keeps the RestTemplate bean creation independent of metrics configuration
		
		return restTemplate;
	}

}
