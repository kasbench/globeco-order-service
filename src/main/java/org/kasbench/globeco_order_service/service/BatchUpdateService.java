package org.kasbench.globeco_order_service.service;

import org.kasbench.globeco_order_service.entity.Order;
import org.kasbench.globeco_order_service.entity.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Service for performing efficient JDBC batch updates on orders.
 * Uses JDBC batch operations to minimize transaction overhead and improve throughput
 * for bulk order status updates.
 */
@Service
public class BatchUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(BatchUpdateService.class);
    private static final int DEFAULT_BATCH_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public BatchUpdateService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Update order statuses in batch using JDBC batch operations.
     * This method provides significant performance improvements over JPA saveAll()
     * by executing all updates in a single database round-trip with optimistic locking.
     * 
     * @param orders List of orders to update with their new status and trade order IDs
     * @param sentStatus The SENT status to apply to all orders
     * @return Total number of orders successfully updated
     * @throws RuntimeException if batch update fails or optimistic locking conflict occurs
     */
    @Transactional(timeout = 5)
    public int batchUpdateOrderStatuses(List<Order> orders, Status sentStatus) {
        long startTime = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        
        if (orders == null || orders.isEmpty()) {
            logger.warn("BATCH_UPDATE: No orders provided for batch update, thread={}", threadName);
            return 0;
        }

        if (sentStatus == null || sentStatus.getId() == null) {
            logger.error("BATCH_UPDATE: SENT status is null or has null ID, thread={}", threadName);
            throw new IllegalArgumentException("SENT status cannot be null");
        }

        int orderCount = orders.size();
        logger.debug("BATCH_UPDATE: Starting JDBC batch update for {} orders with status_id={}, thread={}, timestamp={}",
                orderCount, sentStatus.getId(), threadName, startTime);

        try {
            // SQL for batch update with optimistic locking
            String sql = "UPDATE \"order\" SET status_id = ?, trade_order_id = ?, version = version + 1 " +
                        "WHERE id = ? AND version = ?";

            // Process orders in batches to optimize memory usage
            int totalUpdated = 0;
            int batchCount = (int) Math.ceil((double) orderCount / DEFAULT_BATCH_SIZE);
            
            logger.debug("BATCH_UPDATE: Processing {} orders in {} batches of up to {} orders each, thread={}",
                    orderCount, batchCount, DEFAULT_BATCH_SIZE, threadName);

            for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
                int startIndex = batchIndex * DEFAULT_BATCH_SIZE;
                int endIndex = Math.min(startIndex + DEFAULT_BATCH_SIZE, orderCount);
                List<Order> batch = orders.subList(startIndex, endIndex);
                
                long batchStartTime = System.currentTimeMillis();
                logger.debug("BATCH_UPDATE: Processing batch {} of {}: orders {}-{}, thread={}",
                        batchIndex + 1, batchCount, startIndex + 1, endIndex, threadName);

                // Execute batch update
                int[][] updateCounts = jdbcTemplate.batchUpdate(sql, batch, batch.size(),
                        (PreparedStatement ps, Order order) -> {
                            try {
                                ps.setInt(1, sentStatus.getId());
                                ps.setObject(2, order.getTradeOrderId());
                                ps.setInt(3, order.getId());
                                ps.setInt(4, order.getVersion());
                                
                                logger.trace("BATCH_UPDATE: Prepared statement for order {}: status_id={}, trade_order_id={}, version={}, thread={}",
                                        order.getId(), sentStatus.getId(), order.getTradeOrderId(), order.getVersion(), threadName);
                            } catch (SQLException e) {
                                logger.error("BATCH_UPDATE: Failed to set parameters for order {}, thread={}, error={}",
                                        order.getId(), threadName, e.getMessage(), e);
                                throw new RuntimeException("Failed to prepare batch update statement for order " + order.getId(), e);
                            }
                        });

                // Validate update counts and handle optimistic locking failures
                // updateCounts is int[][] where outer array is batches, inner array is statements per batch
                int batchUpdated = 0;
                if (updateCounts.length > 0 && updateCounts[0] != null) {
                    int[] counts = updateCounts[0]; // Get the first (and only) batch result
                    for (int i = 0; i < counts.length; i++) {
                        int updateCount = counts[i];
                        
                        if (updateCount == 1) {
                            batchUpdated++;
                        } else if (updateCount == 0) {
                            Order order = batch.get(i);
                            logger.error("BATCH_UPDATE: Optimistic locking failure for order {} - version mismatch (expected version: {}), thread={}",
                                    order.getId(), order.getVersion(), threadName);
                            throw new RuntimeException(String.format(
                                    "Optimistic locking failure for order %d - version mismatch", order.getId()));
                        } else {
                            Order order = batch.get(i);
                            logger.error("BATCH_UPDATE: Unexpected update count {} for order {}, thread={}",
                                    updateCount, order.getId(), threadName);
                            throw new RuntimeException(String.format(
                                    "Unexpected update count %d for order %d", updateCount, order.getId()));
                        }
                    }
                }

                long batchDuration = System.currentTimeMillis() - batchStartTime;
                totalUpdated += batchUpdated;
                
                logger.debug("BATCH_UPDATE: Completed batch {} of {} in {}ms: {} orders updated, thread={}",
                        batchIndex + 1, batchCount, batchDuration, batchUpdated, threadName);
            }

            long totalDuration = System.currentTimeMillis() - startTime;
            double avgTimePerOrder = orderCount > 0 ? (double) totalDuration / orderCount : 0;
            
            logger.debug("BATCH_UPDATE: Successfully completed JDBC batch update in {}ms - {} orders updated (avg {:.2f}ms per order), thread={}",
                    totalDuration, totalUpdated, avgTimePerOrder, threadName);

            // Verify all orders were updated
            if (totalUpdated != orderCount) {
                logger.error("BATCH_UPDATE: Update count mismatch - expected {}, actual {}, thread={}",
                        orderCount, totalUpdated, threadName);
                throw new RuntimeException(String.format(
                        "Batch update count mismatch: expected %d, actual %d", orderCount, totalUpdated));
            }

            return totalUpdated;

        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            logger.error("BATCH_UPDATE: Failed to perform JDBC batch update after {}ms for {} orders, thread={}, error={}",
                    totalDuration, orderCount, threadName, e.getMessage(), e);
            throw new RuntimeException("JDBC batch update failed for " + orderCount + " orders", e);
        }
    }

    /**
     * Get the configured batch size for batch operations.
     * 
     * @return The batch size used for splitting large updates
     */
    public int getBatchSize() {
        return DEFAULT_BATCH_SIZE;
    }
}
