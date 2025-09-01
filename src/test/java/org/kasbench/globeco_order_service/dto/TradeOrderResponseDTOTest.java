package org.kasbench.globeco_order_service.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TradeOrderResponseDTOTest {

    @Test
    void testBuilderPattern() {
        // Given
        BlotterDTO blotter = BlotterDTO.builder()
                .id(1)
                .abbreviation("EQ")
                .name("Equity")
                .version(1)
                .build();

        OffsetDateTime timestamp = OffsetDateTime.now();

        // When
        TradeOrderResponseDTO tradeOrder = TradeOrderResponseDTO.builder()
                .id(101)
                .orderId(12345)
                .portfolioId("PORTFOLIO_001")
                .orderType("BUY")
                .securityId("AAPL")
                .quantity(new BigDecimal("100.00"))
                .limitPrice(new BigDecimal("150.25"))
                .tradeTimestamp(timestamp)
                .quantitySent(new BigDecimal("0.00"))
                .submitted(false)
                .version(1)
                .blotter(blotter)
                .build();

        // Then
        assertNotNull(tradeOrder);
        assertEquals(101, tradeOrder.getId());
        assertEquals(12345, tradeOrder.getOrderId());
        assertEquals("PORTFOLIO_001", tradeOrder.getPortfolioId());
        assertEquals("BUY", tradeOrder.getOrderType());
        assertEquals("AAPL", tradeOrder.getSecurityId());
        assertEquals(new BigDecimal("100.00"), tradeOrder.getQuantity());
        assertEquals(new BigDecimal("150.25"), tradeOrder.getLimitPrice());
        assertEquals(timestamp, tradeOrder.getTradeTimestamp());
        assertEquals(new BigDecimal("0.00"), tradeOrder.getQuantitySent());
        assertFalse(tradeOrder.getSubmitted());
        assertEquals(1, tradeOrder.getVersion());
        assertEquals(blotter, tradeOrder.getBlotter());
    }

    @Test
    void testIsSubmitted() {
        // Given
        TradeOrderResponseDTO submittedOrder = TradeOrderResponseDTO.builder()
                .submitted(true)
                .build();

        TradeOrderResponseDTO notSubmittedOrder = TradeOrderResponseDTO.builder()
                .submitted(false)
                .build();

        TradeOrderResponseDTO nullSubmittedOrder = TradeOrderResponseDTO.builder()
                .submitted(null)
                .build();

        // When & Then
        assertTrue(submittedOrder.isSubmitted());
        assertFalse(notSubmittedOrder.isSubmitted());
        assertFalse(nullSubmittedOrder.isSubmitted());
    }

    @Test
    void testGetRemainingQuantity() {
        // Given
        TradeOrderResponseDTO orderWithRemaining = TradeOrderResponseDTO.builder()
                .quantity(new BigDecimal("100.00"))
                .quantitySent(new BigDecimal("30.00"))
                .build();

        TradeOrderResponseDTO orderFullySent = TradeOrderResponseDTO.builder()
                .quantity(new BigDecimal("100.00"))
                .quantitySent(new BigDecimal("100.00"))
                .build();

        TradeOrderResponseDTO orderWithNullQuantity = TradeOrderResponseDTO.builder()
                .quantity(null)
                .quantitySent(new BigDecimal("30.00"))
                .build();

        TradeOrderResponseDTO orderWithNullQuantitySent = TradeOrderResponseDTO.builder()
                .quantity(new BigDecimal("100.00"))
                .quantitySent(null)
                .build();

        // When & Then
        assertEquals(new BigDecimal("70.00"), orderWithRemaining.getRemainingQuantity());
        assertEquals(new BigDecimal("0.00"), orderFullySent.getRemainingQuantity());
        assertNull(orderWithNullQuantity.getRemainingQuantity());
        assertNull(orderWithNullQuantitySent.getRemainingQuantity());
    }

    @Test
    void testAllFieldsCanBeNull() {
        // When
        TradeOrderResponseDTO tradeOrder = TradeOrderResponseDTO.builder().build();

        // Then - Should not throw any exceptions
        assertNotNull(tradeOrder);
        assertNull(tradeOrder.getId());
        assertNull(tradeOrder.getOrderId());
        assertNull(tradeOrder.getPortfolioId());
        assertNull(tradeOrder.getOrderType());
        assertNull(tradeOrder.getSecurityId());
        assertNull(tradeOrder.getQuantity());
        assertNull(tradeOrder.getLimitPrice());
        assertNull(tradeOrder.getTradeTimestamp());
        assertNull(tradeOrder.getQuantitySent());
        assertNull(tradeOrder.getSubmitted());
        assertNull(tradeOrder.getVersion());
        assertNull(tradeOrder.getBlotter());
        assertFalse(tradeOrder.isSubmitted());
        assertNull(tradeOrder.getRemainingQuantity());
    }
}