package org.kasbench.globeco_order_service.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.kasbench.globeco_order_service.dto.OrderPostDTO;
import org.kasbench.globeco_order_service.dto.OrderPostResponseDTO;
import org.kasbench.globeco_order_service.dto.OrderListResponseDTO;
import org.kasbench.globeco_order_service.exception.SystemOverloadException;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to handle batch processing with connection pool protection.
 * Limits concurrent database operations to prevent pool exhaustion.
 */
@Service
public class BatchProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(BatchProcessingService.class);
    
    // Limit concurrent database operations to prevent pool exhaustion
    // Reduced from 25 to 15 to align with optimized connection pool configuration
    private static final int MAX_CONCURRENT_DB_OPERATIONS = 15;
    private static final int BATCH_CHUNK_SIZE = 50;
    private static final long SEMAPHORE_WAIT_THRESHOLD_MS = 1000; // 1 second threshold for warning
    
    private final Semaphore dbOperationSemaphore = new Semaphore(MAX_CONCURRENT_DB_OPERATIONS);
    private final ExecutorService executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_DB_OPERATIONS);
    
    // Metrics tracking
    private final Counter semaphoreWaitCounter;
    private final Timer semaphoreWaitTimer;
    private final AtomicLong totalWaitTimeMs = new AtomicLong(0);
    private final AtomicInteger totalWaitEvents = new AtomicInteger(0);
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private ConnectionPoolCircuitBreaker circuitBreaker;
    
    /**
     * Constructor with metrics initialization.
     */
    @Autowired
    public BatchProcessingService(MeterRegistry meterRegistry) {
        // Initialize semaphore wait counter
        this.semaphoreWaitCounter = Counter.builder("semaphore.wait.events")
                .description("Number of times threads waited for semaphore permits")
                .tag("service", "batch_processing")
                .register(meterRegistry);
        
        // Initialize semaphore wait timer
        this.semaphoreWaitTimer = Timer.builder("semaphore.wait.duration")
                .description("Duration of semaphore wait times")
                .tag("service", "batch_processing")
                .register(meterRegistry);
        
        // Initialize available permits gauge
        Gauge.builder("semaphore.available.permits", dbOperationSemaphore, Semaphore::availablePermits)
                .description("Number of available semaphore permits")
                .tag("service", "batch_processing")
                .register(meterRegistry);
    }
    
    /**
     * Process orders in controlled batches to prevent connection pool exhaustion.
     */
    public OrderListResponseDTO processOrdersWithConnectionControl(List<OrderPostDTO> orders) {
        logger.info("Processing {} orders with connection control (max concurrent: {})", 
                orders.size(), MAX_CONCURRENT_DB_OPERATIONS);
        
        // Check circuit breaker before processing
        if (!circuitBreaker.allowOperation()) {
            logger.error("Circuit breaker OPEN - rejecting batch of {} orders to protect system", orders.size());
            // Throw SystemOverloadException to return 503 status code instead of 400
            throw new SystemOverloadException(
                "System temporarily overloaded - please retry in a few minutes",
                180, // 3 minutes retry delay
                "circuit_breaker_open"
            );
        }
        
        List<OrderPostResponseDTO> allResults = new ArrayList<>();
        int totalChunks = (orders.size() + BATCH_CHUNK_SIZE - 1) / BATCH_CHUNK_SIZE;
        
        // Process in chunks to avoid overwhelming the system
        for (int i = 0; i < orders.size(); i += BATCH_CHUNK_SIZE) {
            int endIndex = Math.min(i + BATCH_CHUNK_SIZE, orders.size());
            List<OrderPostDTO> chunk = orders.subList(i, endIndex);
            int chunkNumber = (i / BATCH_CHUNK_SIZE) + 1;
            
            if (logger.isDebugEnabled()) {
                logger.debug("Processing chunk {}/{} ({}-{} of {} orders)", 
                        chunkNumber, totalChunks, i, endIndex - 1, orders.size());
            }
            
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
        
        // Summary log instead of per-order logs
        long successCount = allResults.stream().filter(OrderPostResponseDTO::isSuccess).count();
        long failureCount = allResults.size() - successCount;
        logger.info("Batch processing completed: {} total orders, {} successful, {} failed", 
                allResults.size(), successCount, failureCount);
        
        return OrderListResponseDTO.fromResults(allResults);
    }
    
    /**
     * Process a chunk of orders with semaphore control.
     */
    private List<OrderPostResponseDTO> processChunkWithSemaphore(List<OrderPostDTO> chunk, int baseIndex) {
        List<CompletableFuture<OrderPostResponseDTO>> futures = new ArrayList<>();
        int semaphoreTimeouts = 0;
        int connectionFailures = 0;
        
        for (int i = 0; i < chunk.size(); i++) {
            final int orderIndex = baseIndex + i;
            final OrderPostDTO order = chunk.get(i);
            
            CompletableFuture<OrderPostResponseDTO> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Track semaphore wait time
                    long waitStartTime = System.currentTimeMillis();
                    
                    // Acquire semaphore before database operation
                    if (!dbOperationSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Timeout waiting for database semaphore for order at index {}", orderIndex);
                        }
                        return OrderPostResponseDTO.failure(
                            "System overloaded - database operation timeout", orderIndex);
                    }
                    
                    // Calculate and record wait time
                    long waitDuration = System.currentTimeMillis() - waitStartTime;
                    if (waitDuration > 0) {
                        // Record wait event
                        semaphoreWaitCounter.increment();
                        semaphoreWaitTimer.record(waitDuration, TimeUnit.MILLISECONDS);
                        
                        // Track for average calculation
                        totalWaitTimeMs.addAndGet(waitDuration);
                        totalWaitEvents.incrementAndGet();
                        
                        // Log warning if wait time exceeds threshold
                        if (waitDuration > SEMAPHORE_WAIT_THRESHOLD_MS) {
                            logger.warn("Semaphore wait time exceeded threshold: {}ms for order at index {} " +
                                    "(available permits: {}, queue length: {})",
                                    waitDuration, orderIndex, 
                                    dbOperationSemaphore.availablePermits(),
                                    dbOperationSemaphore.getQueueLength());
                        }
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
                    if (logger.isDebugEnabled()) {
                        logger.debug("Order processing interrupted at index {}", orderIndex);
                    }
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
        int futureFailures = 0;
        for (CompletableFuture<OrderPostResponseDTO> future : futures) {
            try {
                OrderPostResponseDTO result = future.get(30, TimeUnit.SECONDS);
                results.add(result);
                
                // Track failure types for summary logging
                if (!result.isSuccess()) {
                    if (result.getMessage() != null) {
                        if (result.getMessage().contains("semaphore") || result.getMessage().contains("timeout")) {
                            semaphoreTimeouts++;
                        } else if (result.getMessage().contains("Connection")) {
                            connectionFailures++;
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to get result from future: {}", e.getMessage());
                results.add(OrderPostResponseDTO.failure("Future execution failed: " + e.getMessage(), -1));
                futureFailures++;
            }
        }
        
        // Summary log for chunk processing
        long successCount = results.stream().filter(OrderPostResponseDTO::isSuccess).count();
        if (logger.isDebugEnabled()) {
            logger.debug("Chunk processing summary: {} total, {} successful, {} failed " +
                    "(semaphore timeouts: {}, connection failures: {}, future failures: {})",
                    results.size(), successCount, results.size() - successCount,
                    semaphoreTimeouts, connectionFailures, futureFailures);
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
    
    /**
     * Get average wait time for health checks.
     * Returns the average time threads have waited for semaphore permits.
     * 
     * @return average wait time in milliseconds, or 0 if no wait events recorded
     */
    public long getAverageWaitTime() {
        int events = totalWaitEvents.get();
        if (events == 0) {
            return 0;
        }
        return totalWaitTimeMs.get() / events;
    }
    
    /**
     * Get total number of semaphore wait events.
     * 
     * @return total count of wait events
     */
    public int getTotalWaitEvents() {
        return totalWaitEvents.get();
    }
    
    /**
     * Get total accumulated wait time.
     * 
     * @return total wait time in milliseconds
     */
    public long getTotalWaitTime() {
        return totalWaitTimeMs.get();
    }
}