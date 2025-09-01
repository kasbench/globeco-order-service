package org.kasbench.globeco_order_service.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TradeOrderResultDTOTest {

    @Test
    void testBuilderPattern() {
        // Given
        TradeOrderResponseDTO tradeOrder = TradeOrderResponseDTO.builder()
                .id(101)
                .orderId(12345)
                .portfolioId("PORTFOLIO_001")
                .orderType("BUY")
                .securityId("AAPL")
                .quantity(new BigDecimal("100.00"))
                .limitPrice(new BigDecimal("150.25"))
                .tradeTimestamp(OffsetDateTime.now())
                .quantitySent(new BigDecimal("0.00"))
                .submitted(false)
                .version(1)
                .build();

        // When
        TradeOrderResultDTO result = TradeOrderResultDTO.builder()
                .requestIndex(0)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(tradeOrder)
                .build();

        // Then
        assertNotNull(result);
        assertEquals(0, result.getRequestIndex());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("Trade order created successfully", result.getMessage());
        assertEquals(tradeOrder, result.getTradeOrder());
    }

    @Test
    void testSuccessFactoryMethod() {
        // Given
        TradeOrderResponseDTO tradeOrder = TradeOrderResponseDTO.builder()
                .id(101)
                .orderId(12345)
                .build();

        // When
        TradeOrderResultDTO result = TradeOrderResultDTO.success(0, tradeOrder);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getRequestIndex());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("Trade order created successfully", result.getMessage());
        assertEquals(tradeOrder, result.getTradeOrder());
        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
    }

    @Test
    void testFailureFactoryMethod() {
        // Given
        String errorMessage = "Invalid portfolio ID";

        // When
        TradeOrderResultDTO result = TradeOrderResultDTO.failure(1, errorMessage);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getRequestIndex());
        assertEquals("FAILURE", result.getStatus());
        assertEquals(errorMessage, result.getMessage());
        assertNull(result.getTradeOrder());
        assertFalse(result.isSuccess());
        assertTrue(result.isFailure());
    }

    @Test
    void testGetTradeOrderId() {
        // Given
        TradeOrderResponseDTO tradeOrder = TradeOrderResponseDTO.builder()
                .id(101)
                .build();

        TradeOrderResultDTO successResult = TradeOrderResultDTO.builder()
                .tradeOrder(tradeOrder)
                .build();

        TradeOrderResultDTO failureResult = TradeOrderResultDTO.builder()
                .tradeOrder(null)
                .build();

        // When & Then
        assertEquals(101, successResult.getTradeOrderId());
        assertNull(failureResult.getTradeOrderId());
    }

    @Test
    void testIsSuccessAndIsFailure() {
        // Given
        TradeOrderResultDTO successResult = TradeOrderResultDTO.builder()
                .status("SUCCESS")
                .build();

        TradeOrderResultDTO failureResult = TradeOrderResultDTO.builder()
                .status("FAILURE")
                .build();

        TradeOrderResultDTO unknownResult = TradeOrderResultDTO.builder()
                .status("UNKNOWN")
                .build();

        // When & Then
        assertTrue(successResult.isSuccess());
        assertFalse(successResult.isFailure());

        assertFalse(failureResult.isSuccess());
        assertTrue(failureResult.isFailure());

        assertFalse(unknownResult.isSuccess());
        assertFalse(unknownResult.isFailure());
    }
}