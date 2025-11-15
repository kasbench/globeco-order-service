package org.kasbench.globeco_order_service.repository;

import org.kasbench.globeco_order_service.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer>, JpaSpecificationExecutor<Order> {
    
    /**
     * Find all orders with paging support.
     * 
     * @param pageable Pagination and sorting information
     * @return Page of orders
     */
    Page<Order> findAll(Pageable pageable);
    
    /**
     * Atomically reserve an order for submission by setting a unique temporary processing flag.
     * This prevents race conditions by using a database-level atomic operation.
     * Uses negative order ID as reservation value to ensure uniqueness.
     * Only updates orders that are in NEW status and don't already have a tradeOrderId.
     * 
     * @param orderId The order ID to reserve
     * @return Number of rows updated (1 if successful, 0 if already reserved or invalid)
     */
    @Modifying
    @Query("UPDATE Order o SET o.tradeOrderId = -o.id WHERE o.id = :orderId AND o.status.abbreviation = 'NEW' AND o.tradeOrderId IS NULL")
    int atomicallyReserveForSubmission(@Param("orderId") Integer orderId);
    
    /**
     * Update the reserved order with the actual trade order ID after successful submission.
     * This replaces the temporary negative reservation with the real trade order ID.
     * 
     * @param orderId The order ID to update
     * @param tradeOrderId The actual trade order ID from the trade service
     * @return Number of rows updated (1 if successful, 0 if not found or not reserved)
     */
    @Modifying
    @Query("UPDATE Order o SET o.tradeOrderId = :tradeOrderId WHERE o.id = :orderId AND o.tradeOrderId = -:orderId")
    int updateReservedOrderWithTradeOrderId(@Param("orderId") Integer orderId, @Param("tradeOrderId") Integer tradeOrderId);
    
    /**
     * Release a reserved order back to available state if submission fails.
     * This removes the temporary negative reservation.
     * 
     * @param orderId The order ID to release
     * @return Number of rows updated (1 if successful, 0 if not found or not reserved)
     */
    @Modifying
    @Query("UPDATE Order o SET o.tradeOrderId = NULL WHERE o.id = :orderId AND o.tradeOrderId = -:orderId")
    int releaseReservedOrder(@Param("orderId") Integer orderId);
    
    /**
     * Find all orders by IDs with eager fetching of related entities.
     * This method eliminates N+1 query problems by using JOIN FETCH to load
     * status, orderType, and blotter in a single query.
     * 
     * @param ids List of order IDs to fetch
     * @return List of orders with all relations eagerly loaded
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.status " +
           "LEFT JOIN FETCH o.orderType " +
           "LEFT JOIN FETCH o.blotter " +
           "WHERE o.id IN :ids")
    List<Order> findAllByIdWithRelations(@Param("ids") List<Integer> ids);
} 