package org.kasbench.globeco_order_service.service;

import org.kasbench.globeco_order_service.dto.SecurityDTO;
import org.kasbench.globeco_order_service.dto.SecuritySearchResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SecurityServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(SecurityServiceClient.class);
    
    private final RestTemplate restTemplate;
    private final String securityServiceUrl;
    
    public SecurityServiceClient(
            RestTemplate restTemplate,
            @Value("${security.service.url:http://globeco-security-service:8000}") String securityServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.securityServiceUrl = securityServiceUrl;
    }
    
    /**
     * Retrieves security information by securityId from the Security Service.
     * 
     * @param securityId The security identifier to look up
     * @return SecurityDTO containing securityId and ticker, or null if not found
     * @throws SecurityServiceException if there's a service communication error
     */
    public SecurityDTO getSecurityBySecurityId(String securityId) {
        if (securityId == null || securityId.trim().isEmpty()) {
            logger.warn("Security service call skipped: securityId is null or empty");
            return null;
        }
        
        Instant startTime = Instant.now();
        String url = String.format("%s/api/v1/security/%s", securityServiceUrl, securityId);
        
        logger.debug("Calling security service for securityId: {} at URL: {}", securityId, url);
        
        try {
            ResponseEntity<SecurityDTO> response = restTemplate.getForEntity(url, SecurityDTO.class);
            
            Duration callDuration = Duration.between(startTime, Instant.now());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                SecurityDTO security = response.getBody();
                logger.debug("Security service call successful for securityId: {} -> ticker: {} ({}ms)", 
                        securityId, security.getTicker(), callDuration.toMillis());
                return security;
            } else {
                logger.warn("Security service returned empty response for securityId: {} ({}ms)", 
                        securityId, callDuration.toMillis());
                return null;
            }
            
        } catch (HttpClientErrorException.NotFound e) {
            Duration callDuration = Duration.between(startTime, Instant.now());
            logger.info("Security not found in security service for securityId: {} ({}ms)", 
                    securityId, callDuration.toMillis());
            return null;
            
        } catch (HttpClientErrorException e) {
            Duration callDuration = Duration.between(startTime, Instant.now());
            logger.error("Security service HTTP error for securityId: {} - Status: {}, Response: {} ({}ms)", 
                    securityId, e.getStatusCode(), e.getResponseBodyAsString(), callDuration.toMillis());
            throw new SecurityServiceException(
                    String.format("Security service HTTP error: %s", e.getStatusCode()), e);
                    
        } catch (ResourceAccessException e) {
            Duration callDuration = Duration.between(startTime, Instant.now());
            logger.error("Security service timeout/connection error for securityId: {} - Message: {} ({}ms)", 
                    securityId, e.getMessage(), callDuration.toMillis());
            throw new SecurityServiceException(
                    String.format("Security service connection error: %s", e.getMessage()), e);
                    
        } catch (Exception e) {
            Duration callDuration = Duration.between(startTime, Instant.now());
            logger.error("Unexpected error calling security service for securityId: {} - Message: {} ({}ms)", 
                    securityId, e.getMessage(), callDuration.toMillis(), e);
            throw new SecurityServiceException(
                    String.format("Unexpected security service error: %s", e.getMessage()), e);
        }
    }
    
    /**
     * Search securities by ticker using the Security Service v2 API.
     * 
     * @param tickers List of ticker symbols to search for
     * @return Map of ticker symbols to security IDs for found securities
     * @throws SecurityServiceException if there's a service communication error
     */
    public Map<String, String> searchSecuritiesByTickers(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            logger.debug("Security search skipped: no tickers provided");
            return new HashMap<>();
        }
        
        Map<String, String> resolvedIds = new HashMap<>();
        
        for (String ticker : tickers) {
            if (ticker == null || ticker.trim().isEmpty()) {
                logger.warn("Skipping empty ticker in search");
                continue;
            }
            
            String trimmedTicker = ticker.trim();
            Instant startTime = Instant.now();
            
            try {
                String url = UriComponentsBuilder.fromHttpUrl(securityServiceUrl + "/api/v2/securities")
                        .queryParam("ticker", trimmedTicker)
                        .build()
                        .toUriString();
                
                logger.debug("Searching security service for ticker: '{}' at URL: {}", trimmedTicker, url);
                
                ResponseEntity<SecuritySearchResponseDTO> response = restTemplate.getForEntity(
                        url, SecuritySearchResponseDTO.class);
                
                Duration callDuration = Duration.between(startTime, Instant.now());
                
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    SecuritySearchResponseDTO searchResponse = response.getBody();
                    
                    if (searchResponse.getSecurities() != null && !searchResponse.getSecurities().isEmpty()) {
                        // Take the first match (should be exact match due to "ticker" parameter)
                        SecuritySearchResponseDTO.SecuritySearchItemDTO security = searchResponse.getSecurities().get(0);
                        resolvedIds.put(trimmedTicker, security.getSecurityId());
                        
                        logger.debug("Security found for ticker: '{}' -> securityId: {} ({}ms)", 
                                trimmedTicker, security.getSecurityId(), callDuration.toMillis());
                    } else {
                        logger.info("Security not found for ticker: '{}' ({}ms)", 
                                trimmedTicker, callDuration.toMillis());
                    }
                } else {
                    logger.warn("Security search returned empty response for ticker: '{}' ({}ms)", 
                            trimmedTicker, callDuration.toMillis());
                }
                
            } catch (HttpClientErrorException.NotFound e) {
                Duration callDuration = Duration.between(startTime, Instant.now());
                logger.info("Security not found in search for ticker: '{}' ({}ms)", 
                        trimmedTicker, callDuration.toMillis());
                        
            } catch (HttpClientErrorException e) {
                Duration callDuration = Duration.between(startTime, Instant.now());
                logger.error("Security search HTTP error for ticker: '{}' - Status: {}, Response: {} ({}ms)", 
                        trimmedTicker, e.getStatusCode(), e.getResponseBodyAsString(), callDuration.toMillis());
                throw new SecurityServiceException(
                        String.format("Security search HTTP error: %s", e.getStatusCode()), e);
                        
            } catch (ResourceAccessException e) {
                Duration callDuration = Duration.between(startTime, Instant.now());
                logger.error("Security search timeout/connection error for ticker: '{}' - Message: {} ({}ms)", 
                        trimmedTicker, e.getMessage(), callDuration.toMillis());
                throw new SecurityServiceException(
                        String.format("Security search connection error: %s", e.getMessage()), e);
                        
            } catch (Exception e) {
                Duration callDuration = Duration.between(startTime, Instant.now());
                logger.error("Unexpected error in security search for ticker: '{}' - Message: {} ({}ms)", 
                        trimmedTicker, e.getMessage(), callDuration.toMillis(), e);
                throw new SecurityServiceException(
                        String.format("Unexpected security search error: %s", e.getMessage()), e);
            }
        }
        
        logger.info("Security search completed: {} of {} tickers resolved to IDs", 
                resolvedIds.size(), tickers.size());
        return resolvedIds;
    }
    
    /**
     * Custom exception for Security Service communication errors.
     */
    public static class SecurityServiceException extends RuntimeException {
        public SecurityServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
} 