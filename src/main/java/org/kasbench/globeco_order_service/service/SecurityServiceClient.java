package org.kasbench.globeco_order_service.service;

import org.kasbench.globeco_order_service.dto.SecurityDTO;
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
     * Custom exception for Security Service communication errors.
     */
    public static class SecurityServiceException extends RuntimeException {
        public SecurityServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
} 