package org.kasbench.globeco_order_service.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Arrays;

@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${cache.security.ttl:300}")  // Default: 5 minutes (300 seconds)
    private int securityCacheTtlSeconds;
    
    @Value("${cache.portfolio.ttl:300}")  // Default: 5 minutes (300 seconds)
    private int portfolioCacheTtlSeconds;
    
    @Value("${cache.security.size:1000}")  // Default: 1000 entries
    private int securityCacheSize;
    
    @Value("${cache.portfolio.size:1000}")  // Default: 1000 entries
    private int portfolioCacheSize;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Configure default cache settings
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(securityCacheTtlSeconds))
                .maximumSize(securityCacheSize)
                .recordStats());  // Enable statistics for monitoring
        
        // Register cache names
        cacheManager.setCacheNames(Arrays.asList("security-cache", "portfolio-cache"));
        
        return cacheManager;
    }
    
    @Bean("securityCaffeine")
    public Caffeine<Object, Object> securityCaffeineConfig() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(securityCacheTtlSeconds))
                .maximumSize(securityCacheSize)
                .recordStats();
    }
    
    @Bean("portfolioCaffeine")
    public Caffeine<Object, Object> portfolioCaffeineConfig() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(portfolioCacheTtlSeconds))
                .maximumSize(portfolioCacheSize)
                .recordStats();
    }
} 