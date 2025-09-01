package org.kasbench.globeco_order_service.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BulkTradeOrderRequestDTOTest {

    @Test
    void testBuilderPattern() {
        // Given
        TradeOrderPostDTO order1 = TradeOrderPostDTO.builder()
                .orderId(1)
                .portfolioId("PORTFOLIO_001")
                .orderType("BUY")
                .securityId("AAPL")
                .quantity(new BigDecimal("100.00"))
                .limitPrice(new BigDecimal("150.25"))
                .tradeTimestamp(OffsetDateTime.now())
                .blotterId(1)
                .build();

        TradeOrderPostDTO order2 = TradeOrderPostDTO.builder()
                .orderId(2)
                .portfolioId("PORTFOLIO_002")
                .orderType("SELL")
                .securityId("GOOGL")
                .quantity(new BigDecimal("50.00"))
                .limitPrice(new BigDecimal("2500.75"))
                .tradeTimestamp(OffsetDateTime.now())
                .blotterId(1)
                .build();

        List<TradeOrderPostDTO> orders = Arrays.asList(order1, order2);

        // When
        BulkTradeOrderRequestDTO request = BulkTradeOrderRequestDTO.builder()
                .tradeOrders(orders)
                .build();

        // Then
        assertNotNull(request);
        assertEquals(orders, request.getTradeOrders());
        assertEquals(2, request.getOrderCount());
    }

    @Test
    void testFactoryMethod() {
        // Given
        TradeOrderPostDTO order = TradeOrderPostDTO.builder()
                .orderId(1)
                .portfolioId("PORTFOLIO_001")
                .orderType("BUY")
                .securityId("AAPL")
                .quantity(new BigDecimal("100.00"))
                .limitPrice(new BigDecimal("150.25"))
                .tradeTimestamp(OffsetDateTime.now())
                .blotterId(1)
                .build();

        List<TradeOrderPostDTO> orders = Arrays.asList(order);

        // When
        BulkTradeOrderRequestDTO request = BulkTradeOrderRequestDTO.of(orders);

        // Then
        assertNotNull(request);
        assertEquals(orders, request.getTradeOrders());
        assertEquals(1, request.getOrderCount());
    }

    @Test
    void testGetOrderCountWithNullList() {
        // Given
        BulkTradeOrderRequestDTO request = BulkTradeOrderRequestDTO.builder()
                .tradeOrders(null)
                .build();

        // When & Then
        assertEquals(0, request.getOrderCount());
    }

    @Test
    void testGetOrderCountWithEmptyList() {
        // Given
        BulkTradeOrderRequestDTO request = BulkTradeOrderRequestDTO.builder()
                .tradeOrders(Arrays.asList())
                .build();

        // When & Then
        assertEquals(0, request.getOrderCount());
    }
}