package org.kasbench.globeco_order_service.service;

import org.kasbench.globeco_order_service.dto.OrderPostDTO;
import org.kasbench.globeco_order_service.dto.OrderPostResponseDTO;
import org.kasbench.globeco_order_service.dto.OrderListResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Service to handle batch processing with connection pool protection.
 * Limits concurrent database operations to prevent pool exhaustion.
 */
@Service
public class BatchProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(BatchProcessingService.class);
    
    // Limit concurrent database operations to prevent pool exhaustion
    private static final int MAX_CONCURRENT_DB_OPERATIONS = 25;
    private static final int BATCH_CHUNK_SIZE = 50;
    
    private final Semaphore dbOperationSemaphore = new Semaphore(MAX_CONCURRENT_DB_OPERATIONS);
    private final ExecutorService executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_DB_OPERATIONS);
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private ConnectionPoolCircuitBreaker circuitBreaker;
    
    /**
     * Process orders in controlled batches to prevent connection pool exhaustion.
     */
    public OrderListResponseDTO processOrdersWithConnectionControl(List<OrderPostDTO> orders) {
        logger.info("Processing {} orders with connection control (max concurrent: {})", 
                orders.size(), MAX_CONCURRENT_DB_OPERATIONS);
        
        // Check circuit breaker before processing
        if (!circuitBreaker.allowOperation()) {
            logger.error("Circuit breaker OPEN - rejecting batch of {} orders to protect system", orders.size());
            return OrderListResponseDTO.validationFailure(
                "System temporarily overloaded - please retry in a few minutes");
        }
        
        List<OrderPostResponseDTO> allResults = new ArrayList<>();
        
        // Process in chunks to avoid overwhelming the system
        for (int i = 0; i < orders.size(); i += BATCH_CHUNK_SIZE) {
            int endIndex = Math.min(i + BATCH_CHUNK_SIZE, orders.size());
            List<OrderPostDTO> chunk = orders.subList(i, endIndex);
            
            logger.debug("Processing chunk {}-{} of {} orders", i, endIndex - 1, orders.size());
            
            List<OrderPostResponseDTO> chunkResults = processChunkWithSemaphore(chunk, i);
            allResults.addAll(chunkResults);
            
            // Small delay between chunks to allow connection pool to recover
            if (endIndex < orders.size()) {
                try {
                    Thread.sleep(100); // 100ms delay between chunks
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Batch processing interrupted");
                    break;
                }
            }
        }
        
        return OrderListResponseDTO.fromResults(allResults);
    }
    
    /**
     * Process a chunk of orders with semaphore control.
     */
    private List<OrderPostResponseDTO> processChunkWithSemaphore(List<OrderPostDTO> chunk, int baseIndex) {
        List<CompletableFuture<OrderPostResponseDTO>> futures = new ArrayList<>();
        
        for (int i = 0; i < chunk.size(); i++) {
            final int orderIndex = baseIndex + i;
            final OrderPostDTO order = chunk.get(i);
            
            CompletableFuture<OrderPostResponseDTO> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Acquire semaphore before database operation
                    if (!dbOperationSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                        logger.warn("Timeout waiting for database semaphore for order at index {}", orderIndex);
                        return OrderPostResponseDTO.failure(
                            "System overloaded - database operation timeout", orderIndex);
                    }
                    
                    try {
                        // Process the order
                        OrderPostResponseDTO result = orderService.processIndividualOrderInTransaction(order, orderIndex);
                        
                        // Record success/failure for circuit breaker
                        if (result.isSuccess()) {
                            circuitBreaker.recordSuccess();
                        } else if (result.getMessage() != null && 
                                  result.getMessage().contains("Connection is not available")) {
                            circuitBreaker.recordFailure();
                        }
                        
                        return result;
                    } finally {
                        // Always release semaphore
                        dbOperationSemaphore.release();
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Order processing interrupted at index {}", orderIndex);
                    return OrderPostResponseDTO.failure("Processing interrupted", orderIndex);
                } catch (Exception e) {
                    logger.error("Unexpected error processing order at index {}: {}", orderIndex, e.getMessage());
                    return OrderPostResponseDTO.failure(
                        "Unexpected error: " + e.getMessage(), orderIndex);
                }
            }, executorService);
            
            futures.add(future);
        }
        
        // Wait for all futures to complete
        List<OrderPostResponseDTO> results = new ArrayList<>();
        for (CompletableFuture<OrderPostResponseDTO> future : futures) {
            try {
                results.add(future.get(30, TimeUnit.SECONDS)); // 30 second timeout per order
            } catch (Exception e) {
                logger.error("Failed to get result from future: {}", e.getMessage());
                results.add(OrderPostResponseDTO.failure("Future execution failed: " + e.getMessage(), -1));
            }
        }
        
        return results;
    }
    
    /**
     * Get current semaphore status for monitoring.
     */
    public int getAvailablePermits() {
        return dbOperationSemaphore.availablePermits();
    }
    
    /**
     * Get queue length for monitoring.
     */
    public int getQueueLength() {
        return dbOperationSemaphore.getQueueLength();
    }
}