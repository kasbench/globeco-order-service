package org.kasbench.globeco_order_service.repository;

import org.kasbench.globeco_order_service.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer>, JpaSpecificationExecutor<Order> {
    
    /**
     * Find all orders with paging support.
     * 
     * @param pageable Pagination and sorting information
     * @return Page of orders
     */
    Page<Order> findAll(Pageable pageable);
} 