package org.kasbench.globeco_order_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GlobecoOrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(GlobecoOrderServiceApplication.class, args);
	}

	// HTTP client configuration moved to HttpClientConfiguration class
	// This provides centralized configuration for connection pooling,
	// keep-alive strategy, and timeout settings for all external services

}
