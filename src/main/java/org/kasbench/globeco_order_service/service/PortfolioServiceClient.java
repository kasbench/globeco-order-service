package org.kasbench.globeco_order_service.service;

import org.kasbench.globeco_order_service.dto.PortfolioDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;

@Service
public class PortfolioServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(PortfolioServiceClient.class);
    
    private final RestTemplate restTemplate;
    private final String portfolioServiceUrl;
    
    public PortfolioServiceClient(
            RestTemplate restTemplate,
            @Value("${portfolio.service.url:http://globeco-portfolio-service:8001}") String portfolioServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.portfolioServiceUrl = portfolioServiceUrl;
    }
    
    /**
     * Retrieves portfolio information by portfolioId from the Portfolio Service.
     * 
     * @param portfolioId The portfolio identifier to look up
     * @return PortfolioDTO containing portfolioId and name, or null if not found
     * @throws PortfolioServiceException if there's a service communication error
     */
    public PortfolioDTO getPortfolioByPortfolioId(String portfolioId) {
        if (portfolioId == null || portfolioId.trim().isEmpty()) {
            logger.warn("Portfolio service call skipped: portfolioId is null or empty");
            return null;
        }
        
        Instant startTime = Instant.now();
        String url = String.format("%s/api/v1/portfolio/%s", portfolioServiceUrl, portfolioId);
        
        logger.debug("Calling portfolio service for portfolioId: {} at URL: {}", portfolioId, url);
        
        try {
            ResponseEntity<PortfolioDTO> response = restTemplate.getForEntity(url, PortfolioDTO.class);
            
            Duration callDuration = Duration.between(startTime, Instant.now());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                PortfolioDTO portfolio = response.getBody();
                logger.debug("Portfolio service call successful for portfolioId: {} -> name: {} ({}ms)", 
                        portfolioId, portfolio.getName(), callDuration.toMillis());
                return portfolio;
            } else {
                logger.warn("Portfolio service returned empty response for portfolioId: {} ({}ms)", 
                        portfolioId, callDuration.toMillis());
                return null;
            }
            
        } catch (HttpClientErrorException.NotFound e) {
            Duration callDuration = Duration.between(startTime, Instant.now());
            logger.info("Portfolio not found in portfolio service for portfolioId: {} ({}ms)", 
                    portfolioId, callDuration.toMillis());
            return null;
            
        } catch (HttpClientErrorException e) {
            Duration callDuration = Duration.between(startTime, Instant.now());
            logger.error("Portfolio service HTTP error for portfolioId: {} - Status: {}, Response: {} ({}ms)", 
                    portfolioId, e.getStatusCode(), e.getResponseBodyAsString(), callDuration.toMillis());
            throw new PortfolioServiceException(
                    String.format("Portfolio service HTTP error: %s", e.getStatusCode()), e);
                    
        } catch (ResourceAccessException e) {
            Duration callDuration = Duration.between(startTime, Instant.now());
            logger.error("Portfolio service timeout/connection error for portfolioId: {} - Message: {} ({}ms)", 
                    portfolioId, e.getMessage(), callDuration.toMillis());
            throw new PortfolioServiceException(
                    String.format("Portfolio service connection error: %s", e.getMessage()), e);
                    
        } catch (Exception e) {
            Duration callDuration = Duration.between(startTime, Instant.now());
            logger.error("Unexpected error calling portfolio service for portfolioId: {} - Message: {} ({}ms)", 
                    portfolioId, e.getMessage(), callDuration.toMillis(), e);
            throw new PortfolioServiceException(
                    String.format("Unexpected portfolio service error: %s", e.getMessage()), e);
        }
    }
    
    /**
     * Custom exception for Portfolio Service communication errors.
     */
    public static class PortfolioServiceException extends RuntimeException {
        public PortfolioServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
} 