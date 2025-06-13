package org.kasbench.globeco_order_service.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.kasbench.globeco_order_service.dto.SecurityDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;

@Service
public class SecurityCacheService {
    private static final Logger logger = LoggerFactory.getLogger(SecurityCacheService.class);
    
    private final SecurityServiceClient securityServiceClient;
    private final Caffeine<Object, Object> caffeineConfig;
    private Cache<String, SecurityDTO> securityCache;
    
    public SecurityCacheService(
            SecurityServiceClient securityServiceClient,
            @Qualifier("securityCaffeine") Caffeine<Object, Object> caffeineConfig
    ) {
        this.securityServiceClient = securityServiceClient;
        this.caffeineConfig = caffeineConfig;
    }
    
    @PostConstruct
    public void initializeCache() {
        this.securityCache = caffeineConfig.build();
        logger.info("Security cache initialized with TTL and size limits");
    }
    
    /**
     * Get security information by securityId, using cache when possible.
     * 
     * @param securityId The security identifier to look up
     * @return SecurityDTO containing securityId and ticker, or null if not found
     */
    public SecurityDTO getSecurityBySecurityId(String securityId) {
        if (securityId == null || securityId.trim().isEmpty()) {
            logger.debug("Security cache lookup skipped: securityId is null or empty");
            return null;
        }
        
        try {
            // Try to get from cache first, load from service if not present
            SecurityDTO security = securityCache.get(securityId, key -> {
                logger.debug("Cache miss for securityId: {}, loading from service", key);
                return securityServiceClient.getSecurityBySecurityId(key);
            });
            
            if (security != null) {
                logger.debug("Security cache hit for securityId: {} -> ticker: {}", 
                        securityId, security.getTicker());
            } else {
                logger.debug("Security not found for securityId: {}", securityId);
            }
            
            return security;
            
        } catch (Exception e) {
            logger.error("Error retrieving security from cache for securityId: {} - {}", 
                    securityId, e.getMessage(), e);
            
            // Fallback to direct service call if cache fails
            logger.info("Falling back to direct service call for securityId: {}", securityId);
            return securityServiceClient.getSecurityBySecurityId(securityId);
        }
    }
    
    /**
     * Asynchronously refresh security data in cache.
     * 
     * @param securityId The security identifier to refresh
     * @return CompletableFuture that completes when refresh is done
     */
    public CompletableFuture<SecurityDTO> refreshSecurityAsync(String securityId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Async refresh started for securityId: {}", securityId);
                SecurityDTO security = securityServiceClient.getSecurityBySecurityId(securityId);
                
                if (security != null) {
                    securityCache.put(securityId, security);
                    logger.debug("Async refresh completed for securityId: {} -> ticker: {}", 
                            securityId, security.getTicker());
                } else {
                    // Remove from cache if not found in service
                    securityCache.invalidate(securityId);
                    logger.debug("Async refresh completed - security not found, removed from cache: {}", 
                            securityId);
                }
                
                return security;
                
            } catch (Exception e) {
                logger.error("Async refresh failed for securityId: {} - {}", 
                        securityId, e.getMessage(), e);
                return null;
            }
        });
    }
    
    /**
     * Get cache statistics for monitoring.
     * 
     * @return CacheStats containing hit rate, eviction count, etc.
     */
    public CacheStats getCacheStats() {
        return securityCache.stats();
    }
    
    /**
     * Get current cache size.
     * 
     * @return Number of entries currently in cache
     */
    public long getCacheSize() {
        return securityCache.estimatedSize();
    }
    
    /**
     * Manually invalidate a cache entry.
     * 
     * @param securityId The security identifier to remove from cache
     */
    public void invalidateCache(String securityId) {
        securityCache.invalidate(securityId);
        logger.debug("Cache invalidated for securityId: {}", securityId);
    }
    
    /**
     * Clear all cache entries.
     */
    public void clearCache() {
        securityCache.invalidateAll();
        logger.info("Security cache cleared");
    }
} 