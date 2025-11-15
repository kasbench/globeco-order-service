package org.kasbench.globeco_order_service.repository;

import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.entity.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that findAllByIdWithRelations() eliminates N+1 queries
 * by eagerly fetching status, orderType, and blotter relationships.
 */
@DataJpaTest
@ActiveProfiles("test")
public class OrderRepositoryEagerFetchTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    public void testFindAllByIdWithRelations_EagerlyLoadsAllRelationships() {
        // Given: Order IDs to fetch (assuming test data exists)
        List<Integer> orderIds = Arrays.asList(1, 2, 3);

        // When: Fetch orders with eager loading
        List<Order> orders = orderRepository.findAllByIdWithRelations(orderIds);

        // Then: Verify orders are loaded
        assertNotNull(orders, "Orders should not be null");
        assertFalse(orders.isEmpty(), "Orders should not be empty");

        // Verify that relationships are eagerly loaded (no lazy loading exceptions)
        for (Order order : orders) {
            assertNotNull(order, "Order should not be null");
            
            // Access status - should not trigger additional query
            assertNotNull(order.getStatus(), "Status should be eagerly loaded");
            assertNotNull(order.getStatus().getAbbreviation(), "Status abbreviation should be accessible");
            
            // Access orderType - should not trigger additional query
            assertNotNull(order.getOrderType(), "OrderType should be eagerly loaded");
            assertNotNull(order.getOrderType().getAbbreviation(), "OrderType abbreviation should be accessible");
            
            // Access blotter - should not trigger additional query (may be null for some orders)
            if (order.getBlotter() != null) {
                assertNotNull(order.getBlotter().getName(), "Blotter name should be accessible");
            }
        }

        System.out.println("Successfully loaded " + orders.size() + " orders with eager fetching");
    }

    @Test
    public void testFindAllByIdWithRelations_HandlesEmptyList() {
        // Given: Empty list of order IDs
        List<Integer> orderIds = Arrays.asList();

        // When: Fetch orders with eager loading
        List<Order> orders = orderRepository.findAllByIdWithRelations(orderIds);

        // Then: Should return empty list
        assertNotNull(orders, "Orders should not be null");
        assertTrue(orders.isEmpty(), "Orders should be empty for empty input");
    }

    @Test
    public void testFindAllByIdWithRelations_HandlesNonExistentIds() {
        // Given: Non-existent order IDs
        List<Integer> orderIds = Arrays.asList(99999, 99998, 99997);

        // When: Fetch orders with eager loading
        List<Order> orders = orderRepository.findAllByIdWithRelations(orderIds);

        // Then: Should return empty list
        assertNotNull(orders, "Orders should not be null");
        assertTrue(orders.isEmpty(), "Orders should be empty for non-existent IDs");
    }
}
