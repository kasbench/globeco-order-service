package org.kasbench.globeco_order_service.service;

import org.kasbench.globeco_order_service.repository.BlotterRepository;
import org.kasbench.globeco_order_service.repository.StatusRepository;
import org.kasbench.globeco_order_service.repository.OrderTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cache service to reduce database calls during batch validation.
 * Caches existence checks for blotters, statuses, and order types.
 */
@Service
public class ValidationCacheService {
    private static final Logger logger = LoggerFactory.getLogger(ValidationCacheService.class);
    
    private final BlotterRepository blotterRepository;
    private final StatusRepository statusRepository;
    private final OrderTypeRepository orderTypeRepository;
    
    // Cache sets for existence checks
    private final Set<Integer> validBlotterIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> validStatusIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> validOrderTypeIds = ConcurrentHashMap.newKeySet();
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean cacheInitialized = false;
    
    @Autowired
    public ValidationCacheService(BlotterRepository blotterRepository,
                                StatusRepository statusRepository,
                                OrderTypeRepository orderTypeRepository) {
        this.blotterRepository = blotterRepository;
        this.statusRepository = statusRepository;
        this.orderTypeRepository = orderTypeRepository;
        
        // Initialize cache on startup
        initializeCache();
        
        // Refresh cache every 5 minutes
        scheduler.scheduleAtFixedRate(this::refreshCache, 5, 5, TimeUnit.MINUTES);
    }
    
    /**
     * Check if a blotter ID exists (cached).
     */
    public boolean blotterExists(Integer blotterId) {
        if (!cacheInitialized) {
            // Fallback to database if cache not ready
            return blotterRepository.existsById(blotterId);
        }
        return validBlotterIds.contains(blotterId);
    }
    
    /**
     * Check if a status ID exists (cached).
     */
    public boolean statusExists(Integer statusId) {
        if (!cacheInitialized) {
            // Fallback to database if cache not ready
            return statusRepository.existsById(statusId);
        }
        return validStatusIds.contains(statusId);
    }
    
    /**
     * Check if an order type ID exists (cached).
     */
    public boolean orderTypeExists(Integer orderTypeId) {
        if (!cacheInitialized) {
            // Fallback to database if cache not ready
            return orderTypeRepository.existsById(orderTypeId);
        }
        return validOrderTypeIds.contains(orderTypeId);
    }
    
    /**
     * Initialize the cache with all valid IDs.
     */
    private void initializeCache() {
        try {
            logger.info("Initializing validation cache...");
            
            // Load all valid IDs
            blotterRepository.findAll().forEach(blotter -> validBlotterIds.add(blotter.getId()));
            statusRepository.findAll().forEach(status -> validStatusIds.add(status.getId()));
            orderTypeRepository.findAll().forEach(orderType -> validOrderTypeIds.add(orderType.getId()));
            
            cacheInitialized = true;
            
            logger.info("Validation cache initialized: {} blotters, {} statuses, {} order types",
                    validBlotterIds.size(), validStatusIds.size(), validOrderTypeIds.size());
                    
        } catch (Exception e) {
            logger.error("Failed to initialize validation cache: {}", e.getMessage(), e);
            cacheInitialized = false;
        }
    }
    
    /**
     * Refresh the cache periodically.
     */
    private void refreshCache() {
        try {
            logger.debug("Refreshing validation cache...");
            
            // Clear and reload
            validBlotterIds.clear();
            validStatusIds.clear();
            validOrderTypeIds.clear();
            
            initializeCache();
            
        } catch (Exception e) {
            logger.warn("Failed to refresh validation cache: {}", e.getMessage());
            // Keep existing cache if refresh fails
        }
    }
    
    /**
     * Check if cache is ready for use.
     */
    public boolean isCacheReady() {
        return cacheInitialized;
    }
}