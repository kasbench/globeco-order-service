package org.kasbench.globeco_order_service.service;

import org.kasbench.globeco_order_service.dto.PortfolioDTO;
import org.kasbench.globeco_order_service.dto.PortfolioSearchResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(PortfolioServiceClient.class);
    
    private final RestTemplate restTemplate;
    private final String portfolioServiceUrl;
    private final HttpMetricsService httpMetricsService;
    
    public PortfolioServiceClient(
            RestTemplate restTemplate,
            @Value("${portfolio.service.url:http://globeco-portfolio-service:8000}") String portfolioServiceUrl,
            @Autowired(required = false) HttpMetricsService httpMetricsService
    ) {
        this.restTemplate = restTemplate;
        this.portfolioServiceUrl = portfolioServiceUrl;
        this.httpMetricsService = httpMetricsService;
    }
    
    @PostConstruct
    public void initializeMetrics() {
        if (httpMetricsService != null) {
            try {
                httpMetricsService.registerHttpConnectionPoolMetrics("portfolio-service", portfolioServiceUrl);
                logger.debug("HTTP metrics registered for Portfolio Service");
            } catch (Exception e) {
                logger.warn("Failed to register HTTP metrics for Portfolio Service: {}", e.getMessage());
            }
        }
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
            // Update HTTP metrics before making the call
            if (httpMetricsService != null) {
                httpMetricsService.updateServiceSpecificMetrics("portfolio-service", portfolioServiceUrl);
            }
            
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
     * Search portfolios by name using the Portfolio Service v2 API.
     * 
     * @param portfolioNames List of portfolio names to search for
     * @return Map of portfolio names to portfolio IDs for found portfolios
     * @throws PortfolioServiceException if there's a service communication error
     */
    public Map<String, String> searchPortfoliosByNames(List<String> portfolioNames) {
        if (portfolioNames == null || portfolioNames.isEmpty()) {
            logger.debug("Portfolio search skipped: no portfolio names provided");
            return new HashMap<>();
        }
        
        Map<String, String> resolvedIds = new HashMap<>();
        
        for (String portfolioName : portfolioNames) {
            if (portfolioName == null || portfolioName.trim().isEmpty()) {
                logger.warn("Skipping empty portfolio name in search");
                continue;
            }
            
            String trimmedName = portfolioName.trim();
            Instant startTime = Instant.now();
            
            try {
                String url = UriComponentsBuilder.fromHttpUrl(portfolioServiceUrl + "/api/v2/portfolios")
                        .queryParam("name", trimmedName)
                        .build()
                        .toUriString();
                
                logger.debug("Searching portfolio service for name: '{}' at URL: {}", trimmedName, url);
                
                // Update HTTP metrics before making the call
                if (httpMetricsService != null) {
                    httpMetricsService.updateServiceSpecificMetrics("portfolio-service", portfolioServiceUrl);
                }
                
                ResponseEntity<PortfolioSearchResponseDTO> response = restTemplate.getForEntity(
                        url, PortfolioSearchResponseDTO.class);
                
                Duration callDuration = Duration.between(startTime, Instant.now());
                
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    PortfolioSearchResponseDTO searchResponse = response.getBody();
                    
                    if (searchResponse.getPortfolios() != null && !searchResponse.getPortfolios().isEmpty()) {
                        // Take the first match (should be exact match due to "name" parameter)
                        PortfolioSearchResponseDTO.PortfolioSearchItemDTO portfolio = searchResponse.getPortfolios().get(0);
                        resolvedIds.put(trimmedName, portfolio.getPortfolioId());
                        
                        logger.debug("Portfolio found for name: '{}' -> portfolioId: {} ({}ms)", 
                                trimmedName, portfolio.getPortfolioId(), callDuration.toMillis());
                    } else {
                        logger.info("Portfolio not found for name: '{}' ({}ms)", 
                                trimmedName, callDuration.toMillis());
                    }
                } else {
                    logger.warn("Portfolio search returned empty response for name: '{}' ({}ms)", 
                            trimmedName, callDuration.toMillis());
                }
                
            } catch (HttpClientErrorException.NotFound e) {
                Duration callDuration = Duration.between(startTime, Instant.now());
                logger.info("Portfolio not found in search for name: '{}' ({}ms)", 
                        trimmedName, callDuration.toMillis());
                        
            } catch (HttpClientErrorException e) {
                Duration callDuration = Duration.between(startTime, Instant.now());
                logger.error("Portfolio search HTTP error for name: '{}' - Status: {}, Response: {} ({}ms)", 
                        trimmedName, e.getStatusCode(), e.getResponseBodyAsString(), callDuration.toMillis());
                throw new PortfolioServiceException(
                        String.format("Portfolio search HTTP error: %s", e.getStatusCode()), e);
                        
            } catch (ResourceAccessException e) {
                Duration callDuration = Duration.between(startTime, Instant.now());
                logger.error("Portfolio search timeout/connection error for name: '{}' - Message: {} ({}ms)", 
                        trimmedName, e.getMessage(), callDuration.toMillis());
                throw new PortfolioServiceException(
                        String.format("Portfolio search connection error: %s", e.getMessage()), e);
                        
            } catch (Exception e) {
                Duration callDuration = Duration.between(startTime, Instant.now());
                logger.error("Unexpected error in portfolio search for name: '{}' - Message: {} ({}ms)", 
                        trimmedName, e.getMessage(), callDuration.toMillis(), e);
                throw new PortfolioServiceException(
                        String.format("Unexpected portfolio search error: %s", e.getMessage()), e);
            }
        }
        
        logger.info("Portfolio search completed: {} of {} names resolved to IDs", 
                resolvedIds.size(), portfolioNames.size());
        return resolvedIds;
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