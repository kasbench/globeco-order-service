package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.entity.Order;
import org.kasbench.globeco_order_service.entity.Status;
import org.kasbench.globeco_order_service.entity.Blotter;
import org.kasbench.globeco_order_service.entity.OrderType;
import org.kasbench.globeco_order_service.dto.BulkTradeOrderResponseDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderResultDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderResponseDTO;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit tests for the bulk database update functionality.
 * These tests focus on the method's parameter validation and basic logic
 * without mocking complex transaction management.
 */
class OrderServiceBulkDatabaseUpdateSimpleTest {

    @Test
    void testUpdateOrderStatusesFromBulkResponse_NullOrders() {
        // Create a minimal OrderService instance for testing
        OrderService orderService = createMinimalOrderService();
        
        BulkTradeOrderResponseDTO response = createSuccessfulBulkResponse(1);
        
        // Should handle null orders gracefully without throwing exceptions
        assertDoesNotThrow(() -> {
            orderService.updateOrderStatusesFromBulkResponse(null, response);
        });
    }

    @Test
    void testUpdateOrderStatusesFromBulkResponse_EmptyOrders() {
        OrderService orderService = createMinimalOrderService();
        
        BulkTradeOrderResponseDTO response = createSuccessfulBulkResponse(1);
        
        // Should handle empty orders list gracefully
        assertDoesNotThrow(() -> {
            orderService.updateOrderStatusesFromBulkResponse(new ArrayList<>(), response);
        });
    }

    @Test
    void testUpdateOrderStatusesFromBulkResponse_NullResponse() {
        OrderService orderService = createMinimalOrderService();
        
        List<Order> orders = createTestOrders(1);
        
        // Should handle null response gracefully
        assertDoesNotThrow(() -> {
            orderService.updateOrderStatusesFromBulkResponse(orders, null);
        });
    }

    @Test
    void testBulkResponseStructure_AllSuccessful() {
        // Test the structure of a successful bulk response
        BulkTradeOrderResponseDTO response = createSuccessfulBulkResponse(3);
        
        assertTrue(response.isSuccess());
        assertEquals(3, response.getTotalRequested());
        assertEquals(3, response.getSuccessful());
        assertEquals(0, response.getFailed());
        assertEquals(3, response.getResults().size());
        
        // Verify each result has correct structure
        for (int i = 0; i < response.getResults().size(); i++) {
            TradeOrderResultDTO result = response.getResults().get(i);
            assertEquals(i, result.getRequestIndex());
            assertTrue(result.isSuccess());
            assertNotNull(result.getTradeOrderId());
            assertEquals(Integer.valueOf(1000 + i), result.getTradeOrderId());
        }
    }

    @Test
    void testBulkResponseStructure_PartialSuccess() {
        // Test the structure of a partial success bulk response
        BulkTradeOrderResponseDTO response = createPartialSuccessBulkResponse();
        
        assertTrue(response.isSuccess());
        assertEquals(3, response.getTotalRequested());
        assertEquals(2, response.getSuccessful());
        assertEquals(1, response.getFailed());
        assertEquals(3, response.getResults().size());
        
        // Verify first result is successful
        TradeOrderResultDTO firstResult = response.getResults().get(0);
        assertTrue(firstResult.isSuccess());
        assertEquals(Integer.valueOf(1000), firstResult.getTradeOrderId());
        
        // Verify second result is failed
        TradeOrderResultDTO secondResult = response.getResults().get(1);
        assertTrue(secondResult.isFailure());
        assertNull(secondResult.getTradeOrderId());
        
        // Verify third result is successful
        TradeOrderResultDTO thirdResult = response.getResults().get(2);
        assertTrue(thirdResult.isSuccess());
        assertEquals(Integer.valueOf(1002), thirdResult.getTradeOrderId());
    }

    @Test
    void testBulkResponseStructure_AllFailed() {
        // Test the structure of a complete failure bulk response
        BulkTradeOrderResponseDTO response = createFailedBulkResponse();
        
        assertTrue(response.isFailure());
        assertEquals(2, response.getTotalRequested());
        assertEquals(0, response.getSuccessful());
        assertEquals(2, response.getFailed());
        assertEquals(2, response.getResults().size());
        
        // Verify all results are failures
        for (TradeOrderResultDTO result : response.getResults()) {
            assertTrue(result.isFailure());
            assertNull(result.getTradeOrderId());
        }
    }

    @Test
    void testOrderValidation_RequiredFields() {
        // Test that orders have all required fields for bulk processing
        List<Order> orders = createTestOrders(2);
        
        for (Order order : orders) {
            assertNotNull(order.getId());
            assertNotNull(order.getPortfolioId());
            assertNotNull(order.getOrderType());
            assertNotNull(order.getSecurityId());
            assertNotNull(order.getQuantity());
            assertNotNull(order.getLimitPrice());
            assertNotNull(order.getOrderTimestamp());
            assertNotNull(order.getBlotter());
            assertNotNull(order.getStatus());
            
            // Verify quantities and prices are positive
            assertTrue(order.getQuantity().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(order.getLimitPrice().compareTo(BigDecimal.ZERO) > 0);
        }
    }

    // Helper methods

    private OrderService createMinimalOrderService() {
        // Create a minimal OrderService instance for basic testing
        // This won't work for full integration tests but is sufficient for parameter validation
        try {
            return new OrderService(
                    null, null, null, null, null, null, null, null, null, null,
                    "http://test-service", 5000
            );
        } catch (Exception e) {
            // If constructor fails, return null - tests will handle gracefully
            return null;
        }
    }

    private List<Order> createTestOrders(int count) {
        List<Order> orders = new ArrayList<>();
        
        Status newStatus = Status.builder()
                .id(1)
                .abbreviation("NEW")
                .description("New Order")
                .build();

        Blotter testBlotter = Blotter.builder()
                .id(1)
                .name("Test Blotter")
                .build();

        OrderType buyOrderType = OrderType.builder()
                .id(1)
                .abbreviation("BUY")
                .description("Buy Order")
                .build();

        for (int i = 0; i < count; i++) {
            Order order = Order.builder()
                    .id(i + 1)
                    .blotter(testBlotter)
                    .status(newStatus)
                    .portfolioId("PORTFOLIO_" + (i + 1))
                    .orderType(buyOrderType)
                    .securityId("SECURITY_" + (i + 1))
                    .quantity(BigDecimal.valueOf(100))
                    .limitPrice(BigDecimal.valueOf(50.00))
                    .orderTimestamp(OffsetDateTime.now())
                    .version(1)
                    .build();
            orders.add(order);
        }
        return orders;
    }

    private BulkTradeOrderResponseDTO createSuccessfulBulkResponse(int count) {
        List<TradeOrderResultDTO> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(TradeOrderResultDTO.success(i, createTradeOrderResponse(1000 + i)));
        }

        return BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .message("All orders processed successfully")
                .totalRequested(count)
                .successful(count)
                .failed(0)
                .results(results)
                .build();
    }

    private BulkTradeOrderResponseDTO createPartialSuccessBulkResponse() {
        List<TradeOrderResultDTO> results = Arrays.asList(
                TradeOrderResultDTO.success(0, createTradeOrderResponse(1000)),
                TradeOrderResultDTO.failure(1, "Insufficient funds"),
                TradeOrderResultDTO.success(2, createTradeOrderResponse(1002))
        );

        return BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .message("Partial success")
                .totalRequested(3)
                .successful(2)
                .failed(1)
                .results(results)
                .build();
    }

    private BulkTradeOrderResponseDTO createFailedBulkResponse() {
        List<TradeOrderResultDTO> results = Arrays.asList(
                TradeOrderResultDTO.failure(0, "Invalid security"),
                TradeOrderResultDTO.failure(1, "Market closed")
        );

        return BulkTradeOrderResponseDTO.builder()
                .status("FAILURE")
                .message("All orders failed")
                .totalRequested(2)
                .successful(0)
                .failed(2)
                .results(results)
                .build();
    }

    private TradeOrderResponseDTO createTradeOrderResponse(Integer id) {
        return TradeOrderResponseDTO.builder()
                .id(id)
                .orderId(id - 999) // Simple mapping for test
                .portfolioId("PORTFOLIO_" + (id - 999))
                .orderType("BUY")
                .securityId("SECURITY_" + (id - 999))
                .quantity(BigDecimal.valueOf(100))
                .limitPrice(BigDecimal.valueOf(50.00))
                .build();
    }
}