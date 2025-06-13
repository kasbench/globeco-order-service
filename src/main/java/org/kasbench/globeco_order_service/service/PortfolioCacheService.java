package org.kasbench.globeco_order_service.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.kasbench.globeco_order_service.dto.PortfolioDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;

@Service
public class PortfolioCacheService {
    private static final Logger logger = LoggerFactory.getLogger(PortfolioCacheService.class);
    
    private final PortfolioServiceClient portfolioServiceClient;
    private final Caffeine<Object, Object> caffeineConfig;
    private Cache<String, PortfolioDTO> portfolioCache;
    
    public PortfolioCacheService(
            PortfolioServiceClient portfolioServiceClient,
            @Qualifier("portfolioCaffeine") Caffeine<Object, Object> caffeineConfig
    ) {
        this.portfolioServiceClient = portfolioServiceClient;
        this.caffeineConfig = caffeineConfig;
    }
    
    @PostConstruct
    public void initializeCache() {
        this.portfolioCache = caffeineConfig.build();
        logger.info("Portfolio cache initialized with TTL and size limits");
    }
    
    /**
     * Get portfolio information by portfolioId, using cache when possible.
     * 
     * @param portfolioId The portfolio identifier to look up
     * @return PortfolioDTO containing portfolioId and name, or null if not found
     */
    public PortfolioDTO getPortfolioByPortfolioId(String portfolioId) {
        if (portfolioId == null || portfolioId.trim().isEmpty()) {
            logger.debug("Portfolio cache lookup skipped: portfolioId is null or empty");
            return null;
        }
        
        try {
            // Try to get from cache first, load from service if not present
            PortfolioDTO portfolio = portfolioCache.get(portfolioId, key -> {
                logger.debug("Cache miss for portfolioId: {}, loading from service", key);
                return portfolioServiceClient.getPortfolioByPortfolioId(key);
            });
            
            if (portfolio != null) {
                logger.debug("Portfolio cache hit for portfolioId: {} -> name: {}", 
                        portfolioId, portfolio.getName());
            } else {
                logger.debug("Portfolio not found for portfolioId: {}", portfolioId);
            }
            
            return portfolio;
            
        } catch (Exception e) {
            logger.error("Error retrieving portfolio from cache for portfolioId: {} - {}", 
                    portfolioId, e.getMessage(), e);
            
            // Fallback to direct service call if cache fails
            logger.info("Falling back to direct service call for portfolioId: {}", portfolioId);
            return portfolioServiceClient.getPortfolioByPortfolioId(portfolioId);
        }
    }
    
    /**
     * Asynchronously refresh portfolio data in cache.
     * 
     * @param portfolioId The portfolio identifier to refresh
     * @return CompletableFuture that completes when refresh is done
     */
    public CompletableFuture<PortfolioDTO> refreshPortfolioAsync(String portfolioId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Async refresh started for portfolioId: {}", portfolioId);
                PortfolioDTO portfolio = portfolioServiceClient.getPortfolioByPortfolioId(portfolioId);
                
                if (portfolio != null) {
                    portfolioCache.put(portfolioId, portfolio);
                    logger.debug("Async refresh completed for portfolioId: {} -> name: {}", 
                            portfolioId, portfolio.getName());
                } else {
                    // Remove from cache if not found in service
                    portfolioCache.invalidate(portfolioId);
                    logger.debug("Async refresh completed - portfolio not found, removed from cache: {}", 
                            portfolioId);
                }
                
                return portfolio;
                
            } catch (Exception e) {
                logger.error("Async refresh failed for portfolioId: {} - {}", 
                        portfolioId, e.getMessage(), e);
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
        return portfolioCache.stats();
    }
    
    /**
     * Get current cache size.
     * 
     * @return Number of entries currently in cache
     */
    public long getCacheSize() {
        return portfolioCache.estimatedSize();
    }
    
    /**
     * Manually invalidate a cache entry.
     * 
     * @param portfolioId The portfolio identifier to remove from cache
     */
    public void invalidateCache(String portfolioId) {
        portfolioCache.invalidate(portfolioId);
        logger.debug("Cache invalidated for portfolioId: {}", portfolioId);
    }
    
    /**
     * Clear all cache entries.
     */
    public void clearCache() {
        portfolioCache.invalidateAll();
        logger.info("Portfolio cache cleared");
    }
} 