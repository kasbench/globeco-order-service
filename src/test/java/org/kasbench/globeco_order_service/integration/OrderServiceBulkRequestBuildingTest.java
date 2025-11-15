package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.dto.BulkTradeOrderRequestDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderPostDTO;
import org.kasbench.globeco_order_service.entity.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the bulk request building functionality in OrderService.
 * These tests focus specifically on the buildBulkTradeOrderRequest method
 * and its validation logic.
 */
public class OrderServiceBulkRequestBuildingTest {

    private OrderService orderService;

    private Blotter blotter;
    private Status newStatus;
    private OrderType buyOrderType;
    private OrderType sellOrderType;
    private OffsetDateTime testTimestamp;

    @BeforeEach
    void setUp() {
        // Setup test entities
        blotter = Blotter.builder()
                .id(1)
                .name("Test Blotter")
                .version(1)
                .build();

        newStatus = Status.builder()
                .id(1)
                .abbreviation("NEW")
                .description("New Order")
                .version(1)
                .build();

        buyOrderType = OrderType.builder()
                .id(1)
                .abbreviation("BUY")
                .description("Buy Order")
                .version(1)
                .build();

        sellOrderType = OrderType.builder()
                .id(2)
                .abbreviation("SELL")
                .description("Sell Order")
                .version(1)
                .build();

        testTimestamp = OffsetDateTime.now();

        // Create OrderService instance manually with null mocks for dependencies we don't use
        orderService = new OrderService(
                null, // orderRepository - not used in buildBulkTradeOrderRequest
                null, // statusRepository - not used in buildBulkTradeOrderRequest
                null, // blotterRepository - not used in buildBulkTradeOrderRequest
                null, // orderTypeRepository - not used in buildBulkTradeOrderRequest
                null, // restTemplate - not used in buildBulkTradeOrderRequest
                null, // securityCacheService - not used in buildBulkTradeOrderRequest
                null, // portfolioCacheService - not used in buildBulkTradeOrderRequest
                null, // portfolioServiceClient - not used in buildBulkTradeOrderRequest
                null, // securityServiceClient - not used in buildBulkTradeOrderRequest
                null, // transactionManager - not used in buildBulkTradeOrderRequest
                null, // meterRegistry - not used in buildBulkTradeOrderRequest
                null, // performanceMonitor - not used in buildBulkTradeOrderRequest
                null, // batchUpdateService - not used in buildBulkTradeOrderRequest
                "http://test-trade-service:8082"
        );
    }

    @Test
    void testBuildBulkTradeOrderRequest_Success() {
        // Given: A list of valid orders
        List<Order> orders = createValidOrders();

        // When: Building bulk request
        BulkTradeOrderRequestDTO result = orderService.buildBulkTradeOrderRequest(orders);

        // Then: Verify the result
        assertNotNull(result);
        assertNotNull(result.getTradeOrders());
        assertEquals(2, result.getOrderCount());

        // Verify first order mapping
        TradeOrderPostDTO firstTradeOrder = result.getTradeOrders().get(0);
        assertEquals(Integer.valueOf(1), firstTradeOrder.getOrderId());
        assertEquals("PORTFOLIO_001", firstTradeOrder.getPortfolioId());
        assertEquals("BUY", firstTradeOrder.getOrderType());
        assertEquals("AAPL", firstTradeOrder.getSecurityId());
        assertEquals(new BigDecimal("100.00"), firstTradeOrder.getQuantity());
        assertEquals(new BigDecimal("150.25"), firstTradeOrder.getLimitPrice());
        assertEquals(testTimestamp, firstTradeOrder.getTradeTimestamp());
        assertEquals(Integer.valueOf(1), firstTradeOrder.getBlotterId());

        // Verify second order mapping
        TradeOrderPostDTO secondTradeOrder = result.getTradeOrders().get(1);
        assertEquals(Integer.valueOf(2), secondTradeOrder.getOrderId());
        assertEquals("PORTFOLIO_002", secondTradeOrder.getPortfolioId());
        assertEquals("SELL", secondTradeOrder.getOrderType());
        assertEquals("GOOGL", secondTradeOrder.getSecurityId());
        assertEquals(new BigDecimal("50.00"), secondTradeOrder.getQuantity());
        assertEquals(new BigDecimal("2500.75"), secondTradeOrder.getLimitPrice());
        assertEquals(testTimestamp, secondTradeOrder.getTradeTimestamp());
        assertEquals(Integer.valueOf(1), secondTradeOrder.getBlotterId());
    }

    @Test
    void testBuildBulkTradeOrderRequest_WithNullBlotter() {
        // Given: An order with null blotter
        Order order = createValidOrder(1, "PORTFOLIO_001", buyOrderType, "AAPL", 
                new BigDecimal("100.00"), new BigDecimal("150.25"));
        order.setBlotter(null); // Set blotter to null

        List<Order> orders = Collections.singletonList(order);

        // When: Building bulk request
        BulkTradeOrderRequestDTO result = orderService.buildBulkTradeOrderRequest(orders);

        // Then: Verify blotterId is null
        assertNotNull(result);
        assertEquals(1, result.getOrderCount());
        TradeOrderPostDTO tradeOrder = result.getTradeOrders().get(0);
        assertNull(tradeOrder.getBlotterId());
    }

    @Test
    void testBuildBulkTradeOrderRequest_NullOrdersList() {
        // When & Then: Null orders list should throw exception
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(null)
        );
        assertEquals("Orders list cannot be null or empty", exception.getMessage());
    }

    @Test
    void testBuildBulkTradeOrderRequest_EmptyOrdersList() {
        // When & Then: Empty orders list should throw exception
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(new ArrayList<>())
        );
        assertEquals("Orders list cannot be null or empty", exception.getMessage());
    }

    @Test
    void testValidateOrderForBulkSubmission_NullOrder() {
        // Given: A list with null order
        List<Order> orders = Collections.singletonList(null);

        // When & Then: Should throw exception for null order
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(orders)
        );
        assertEquals("Order cannot be null", exception.getMessage());
    }

    @Test
    void testValidateOrderForBulkSubmission_NullOrderId() {
        // Given: Order with null ID
        Order order = createValidOrder(null, "PORTFOLIO_001", buyOrderType, "AAPL", 
                new BigDecimal("100.00"), new BigDecimal("150.25"));
        List<Order> orders = Collections.singletonList(order);

        // When & Then: Should throw exception for null order ID
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(orders)
        );
        assertEquals("Order ID is required", exception.getMessage());
    }

    @Test
    void testValidateOrderForBulkSubmission_NullPortfolioId() {
        // Given: Order with null portfolio ID
        Order order = createValidOrder(1, null, buyOrderType, "AAPL", 
                new BigDecimal("100.00"), new BigDecimal("150.25"));
        List<Order> orders = Collections.singletonList(order);

        // When & Then: Should throw exception for null portfolio ID
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(orders)
        );
        assertEquals("Portfolio ID is required for order 1", exception.getMessage());
    }

    @Test
    void testValidateOrderForBulkSubmission_EmptyPortfolioId() {
        // Given: Order with empty portfolio ID
        Order order = createValidOrder(1, "   ", buyOrderType, "AAPL", 
                new BigDecimal("100.00"), new BigDecimal("150.25"));
        List<Order> orders = Collections.singletonList(order);

        // When & Then: Should throw exception for empty portfolio ID
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(orders)
        );
        assertEquals("Portfolio ID is required for order 1", exception.getMessage());
    }

    @Test
    void testValidateOrderForBulkSubmission_NullOrderType() {
        // Given: Order with null order type
        Order order = createValidOrder(1, "PORTFOLIO_001", null, "AAPL", 
                new BigDecimal("100.00"), new BigDecimal("150.25"));
        List<Order> orders = Collections.singletonList(order);

        // When & Then: Should throw exception for null order type
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(orders)
        );
        assertEquals("Order type is required for order 1", exception.getMessage());
    }

    @Test
    void testValidateOrderForBulkSubmission_OrderTypeWithNullAbbreviation() {
        // Given: Order type with null abbreviation
        OrderType invalidOrderType = OrderType.builder()
                .id(1)
                .abbreviation(null)
                .description("Invalid Order Type")
                .version(1)
                .build();

        Order order = createValidOrder(1, "PORTFOLIO_001", invalidOrderType, "AAPL", 
                new BigDecimal("100.00"), new BigDecimal("150.25"));
        List<Order> orders = Collections.singletonList(order);

        // When & Then: Should throw exception for null order type abbreviation
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(orders)
        );
        assertEquals("Order type is required for order 1", exception.getMessage());
    }

    @Test
    void testValidateOrderForBulkSubmission_NullSecurityId() {
        // Given: Order with null security ID
        Order order = createValidOrder(1, "PORTFOLIO_001", buyOrderType, null, 
                new BigDecimal("100.00"), new BigDecimal("150.25"));
        List<Order> orders = Collections.singletonList(order);

        // When & Then: Should throw exception for null security ID
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(orders)
        );
        assertEquals("Security ID is required for order 1", exception.getMessage());
    }

    @Test
    void testValidateOrderForBulkSubmission_EmptySecurityId() {
        // Given: Order with empty security ID
        Order order = createValidOrder(1, "PORTFOLIO_001", buyOrderType, "   ", 
                new BigDecimal("100.00"), new BigDecimal("150.25"));
        List<Order> orders = Collections.singletonList(order);

        // When & Then: Should throw exception for empty security ID
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(orders)
        );
        assertEquals("Security ID is required for order 1", exception.getMessage());
    }

    @Test
    void testValidateOrderForBulkSubmission_NullQuantity() {
        // Given: Order with null quantity
        Order order = createValidOrder(1, "PORTFOLIO_001", buyOrderType, "AAPL", 
                null, new BigDecimal("150.25"));
        List<Order> orders = Collections.singletonList(order);

        // When & Then: Should throw exception for null quantity
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(orders)
        );
        assertEquals("Quantity must be positive for order 1", exception.getMessage());
    }

    @Test
    void testValidateOrderForBulkSubmission_ZeroQuantity() {
        // Given: Order with zero quantity
        Order order = createValidOrder(1, "PORTFOLIO_001", buyOrderType, "AAPL", 
                BigDecimal.ZERO, new BigDecimal("150.25"));
        List<Order> orders = Collections.singletonList(order);

        // When & Then: Should throw exception for zero quantity
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(orders)
        );
        assertEquals("Quantity must be positive for order 1", exception.getMessage());
    }

    @Test
    void testValidateOrderForBulkSubmission_NegativeQuantity() {
        // Given: Order with negative quantity
        Order order = createValidOrder(1, "PORTFOLIO_001", buyOrderType, "AAPL", 
                new BigDecimal("-10.00"), new BigDecimal("150.25"));
        List<Order> orders = Collections.singletonList(order);

        // When & Then: Should throw exception for negative quantity
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(orders)
        );
        assertEquals("Quantity must be positive for order 1", exception.getMessage());
    }

    @Test
    void testValidateOrderForBulkSubmission_NullLimitPrice() {
        // Given: Order with null limit price
        Order order = createValidOrder(1, "PORTFOLIO_001", buyOrderType, "AAPL", 
                new BigDecimal("100.00"), null);
        List<Order> orders = Collections.singletonList(order);

        // When & Then: Should throw exception for null limit price
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(orders)
        );
        assertEquals("Limit price must be positive for order 1", exception.getMessage());
    }

    @Test
    void testValidateOrderForBulkSubmission_ZeroLimitPrice() {
        // Given: Order with zero limit price
        Order order = createValidOrder(1, "PORTFOLIO_001", buyOrderType, "AAPL", 
                new BigDecimal("100.00"), BigDecimal.ZERO);
        List<Order> orders = Collections.singletonList(order);

        // When & Then: Should throw exception for zero limit price
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(orders)
        );
        assertEquals("Limit price must be positive for order 1", exception.getMessage());
    }

    @Test
    void testValidateOrderForBulkSubmission_NegativeLimitPrice() {
        // Given: Order with negative limit price
        Order order = createValidOrder(1, "PORTFOLIO_001", buyOrderType, "AAPL", 
                new BigDecimal("100.00"), new BigDecimal("-150.25"));
        List<Order> orders = Collections.singletonList(order);

        // When & Then: Should throw exception for negative limit price
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(orders)
        );
        assertEquals("Limit price must be positive for order 1", exception.getMessage());
    }

    @Test
    void testValidateOrderForBulkSubmission_NullOrderTimestamp() {
        // Given: Order with null timestamp
        Order order = createValidOrder(1, "PORTFOLIO_001", buyOrderType, "AAPL", 
                new BigDecimal("100.00"), new BigDecimal("150.25"));
        order.setOrderTimestamp(null);
        List<Order> orders = Collections.singletonList(order);

        // When & Then: Should throw exception for null timestamp
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(orders)
        );
        assertEquals("Order timestamp is required for order 1", exception.getMessage());
    }

    @Test
    void testBuildBulkTradeOrderRequest_LargeOrderList() {
        // Given: A large list of valid orders
        List<Order> orders = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            orders.add(createValidOrder(i, "PORTFOLIO_" + String.format("%03d", i), 
                    i % 2 == 0 ? buyOrderType : sellOrderType, 
                    "SEC" + String.format("%03d", i),
                    new BigDecimal(i * 10), new BigDecimal(i * 1.5)));
        }

        // When: Building bulk request
        BulkTradeOrderRequestDTO result = orderService.buildBulkTradeOrderRequest(orders);

        // Then: Verify the result
        assertNotNull(result);
        assertEquals(100, result.getOrderCount());
        assertEquals(100, result.getTradeOrders().size());

        // Verify first and last orders
        TradeOrderPostDTO firstOrder = result.getTradeOrders().get(0);
        assertEquals(Integer.valueOf(1), firstOrder.getOrderId());
        assertEquals("SELL", firstOrder.getOrderType());

        TradeOrderPostDTO lastOrder = result.getTradeOrders().get(99);
        assertEquals(Integer.valueOf(100), lastOrder.getOrderId());
        assertEquals("BUY", lastOrder.getOrderType());
    }

    // Helper methods

    private List<Order> createValidOrders() {
        Order order1 = createValidOrder(1, "PORTFOLIO_001", buyOrderType, "AAPL", 
                new BigDecimal("100.00"), new BigDecimal("150.25"));
        Order order2 = createValidOrder(2, "PORTFOLIO_002", sellOrderType, "GOOGL", 
                new BigDecimal("50.00"), new BigDecimal("2500.75"));
        return Arrays.asList(order1, order2);
    }

    private Order createValidOrder(Integer id, String portfolioId, OrderType orderType, 
                                 String securityId, BigDecimal quantity, BigDecimal limitPrice) {
        return Order.builder()
                .id(id)
                .blotter(blotter)
                .status(newStatus)
                .portfolioId(portfolioId)
                .orderType(orderType)
                .securityId(securityId)
                .quantity(quantity)
                .limitPrice(limitPrice)
                .orderTimestamp(testTimestamp)
                .version(1)
                .build();
    }
}