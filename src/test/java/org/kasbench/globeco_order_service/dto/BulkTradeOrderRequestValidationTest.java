package org.kasbench.globeco_order_service.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class BulkTradeOrderRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidRequest() {
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

        BulkTradeOrderRequestDTO request = BulkTradeOrderRequestDTO.builder()
                .tradeOrders(Arrays.asList(order))
                .build();

        // When
        Set<ConstraintViolation<BulkTradeOrderRequestDTO>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
    }

    @Test
    void testNullTradeOrdersList() {
        // Given
        BulkTradeOrderRequestDTO request = BulkTradeOrderRequestDTO.builder()
                .tradeOrders(null)
                .build();

        // When
        Set<ConstraintViolation<BulkTradeOrderRequestDTO>> violations = validator.validate(request);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Trade orders list is required")));
    }

    @Test
    void testEmptyTradeOrdersList() {
        // Given
        BulkTradeOrderRequestDTO request = BulkTradeOrderRequestDTO.builder()
                .tradeOrders(new ArrayList<>())
                .build();

        // When
        Set<ConstraintViolation<BulkTradeOrderRequestDTO>> violations = validator.validate(request);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Trade orders list must contain between 1 and 1000 orders")));
    }

    @Test
    void testMaxSizeExceeded() {
        // Given - Create 1001 orders (exceeds max of 1000)
        List<TradeOrderPostDTO> orders = IntStream.range(0, 1001)
                .mapToObj(i -> TradeOrderPostDTO.builder()
                        .orderId(i)
                        .portfolioId("PORTFOLIO_" + i)
                        .orderType("BUY")
                        .securityId("AAPL")
                        .quantity(new BigDecimal("100.00"))
                        .limitPrice(new BigDecimal("150.25"))
                        .tradeTimestamp(OffsetDateTime.now())
                        .blotterId(1)
                        .build())
                .toList();

        BulkTradeOrderRequestDTO request = BulkTradeOrderRequestDTO.builder()
                .tradeOrders(orders)
                .build();

        // When
        Set<ConstraintViolation<BulkTradeOrderRequestDTO>> violations = validator.validate(request);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Trade orders list must contain between 1 and 1000 orders")));
    }

    @Test
    void testMaxSizeAtLimit() {
        // Given - Create exactly 1000 orders (at the limit)
        List<TradeOrderPostDTO> orders = IntStream.range(0, 1000)
                .mapToObj(i -> TradeOrderPostDTO.builder()
                        .orderId(i)
                        .portfolioId("PORTFOLIO_" + i)
                        .orderType("BUY")
                        .securityId("AAPL")
                        .quantity(new BigDecimal("100.00"))
                        .limitPrice(new BigDecimal("150.25"))
                        .tradeTimestamp(OffsetDateTime.now())
                        .blotterId(1)
                        .build())
                .toList();

        BulkTradeOrderRequestDTO request = BulkTradeOrderRequestDTO.builder()
                .tradeOrders(orders)
                .build();

        // When
        Set<ConstraintViolation<BulkTradeOrderRequestDTO>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
    }
}