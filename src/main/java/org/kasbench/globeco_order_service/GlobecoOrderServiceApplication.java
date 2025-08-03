package org.kasbench.globeco_order_service;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class GlobecoOrderServiceApplication {
	
	private static final Logger logger = LoggerFactory.getLogger(GlobecoOrderServiceApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(GlobecoOrderServiceApplication.class, args);
	}

	@Bean
	public PoolingHttpClientConnectionManager connectionManager() {
		logger.error("=== Creating PoolingHttpClientConnectionManager bean ===");
		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
		connectionManager.setMaxTotal(200); // Maximum total connections
		connectionManager.setDefaultMaxPerRoute(20); // Maximum connections per route
		logger.error("=== PoolingHttpClientConnectionManager bean created successfully ===");
		return connectionManager;
	}

	@Bean
	public CloseableHttpClient httpClient(PoolingHttpClientConnectionManager connectionManager) {
		return HttpClients.custom()
				.setConnectionManager(connectionManager)
				.setConnectionManagerShared(true)
				.build();
	}

	@Bean
	public RestTemplate restTemplate(CloseableHttpClient httpClient,
									 @Value("${portfolio.service.url:http://globeco-portfolio-service:8000}") String portfolioServiceUrl,
									 @Value("${security.service.url:http://globeco-security-service:8000}") String securityServiceUrl) {
		
		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
		factory.setHttpClient(httpClient);
		factory.setConnectTimeout(5000);
		factory.setConnectionRequestTimeout(5000);
		
		RestTemplate restTemplate = new RestTemplate(factory);
		
		// Note: HTTP metrics registration is handled by MetricsConfiguration when metrics are enabled
		// This keeps the RestTemplate bean creation independent of metrics configuration
		
		return restTemplate;
	}

}
