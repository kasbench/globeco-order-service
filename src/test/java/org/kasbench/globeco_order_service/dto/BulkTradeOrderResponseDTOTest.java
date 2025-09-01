package org.kasbench.globeco_order_service.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BulkTradeOrderResponseDTOTest {

    @Test
    void testBuilderPattern() {
        // Given
        TradeOrderResultDTO result1 = TradeOrderResultDTO.builder()
                .requestIndex(0)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .build();

        TradeOrderResultDTO result2 = TradeOrderResultDTO.builder()
                .requestIndex(1)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .build();

        List<TradeOrderResultDTO> results = Arrays.asList(result1, result2);

        // When
        BulkTradeOrderResponseDTO response = BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .message("All trade orders created successfully")
                .totalRequested(2)
                .successful(2)
                .failed(0)
                .results(results)
                .build();

        // Then
        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("All trade orders created successfully", response.getMessage());
        assertEquals(2, response.getTotalRequested());
        assertEquals(2, response.getSuccessful());
        assertEquals(0, response.getFailed());
        assertEquals(results, response.getResults());
    }

    @Test
    void testIsSuccess() {
        // Given
        BulkTradeOrderResponseDTO successResponse = BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .build();

        BulkTradeOrderResponseDTO failureResponse = BulkTradeOrderResponseDTO.builder()
                .status("FAILURE")
                .build();

        // When & Then
        assertTrue(successResponse.isSuccess());
        assertFalse(successResponse.isFailure());
        
        assertFalse(failureResponse.isSuccess());
        assertTrue(failureResponse.isFailure());
    }

    @Test
    void testGetSuccessRate() {
        // Given
        BulkTradeOrderResponseDTO response = BulkTradeOrderResponseDTO.builder()
                .totalRequested(10)
                .successful(7)
                .failed(3)
                .build();

        // When
        double successRate = response.getSuccessRate();

        // Then
        assertEquals(0.7, successRate, 0.001);
    }

    @Test
    void testGetSuccessRateWithZeroTotal() {
        // Given
        BulkTradeOrderResponseDTO response = BulkTradeOrderResponseDTO.builder()
                .totalRequested(0)
                .successful(0)
                .failed(0)
                .build();

        // When
        double successRate = response.getSuccessRate();

        // Then
        assertEquals(0.0, successRate, 0.001);
    }

    @Test
    void testGetSuccessRateWithNullTotal() {
        // Given
        BulkTradeOrderResponseDTO response = BulkTradeOrderResponseDTO.builder()
                .totalRequested(null)
                .successful(5)
                .failed(0)
                .build();

        // When
        double successRate = response.getSuccessRate();

        // Then
        assertEquals(0.0, successRate, 0.001);
    }

    @Test
    void testIsCompleteSuccess() {
        // Given
        BulkTradeOrderResponseDTO completeSuccess = BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .totalRequested(5)
                .successful(5)
                .failed(0)
                .build();

        BulkTradeOrderResponseDTO partialSuccess = BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .totalRequested(5)
                .successful(3)
                .failed(2)
                .build();

        // When & Then
        assertTrue(completeSuccess.isCompleteSuccess());
        assertFalse(partialSuccess.isCompleteSuccess());
    }

    @Test
    void testIsCompleteFailure() {
        // Given
        BulkTradeOrderResponseDTO completeFailure = BulkTradeOrderResponseDTO.builder()
                .status("FAILURE")
                .totalRequested(5)
                .successful(0)
                .failed(5)
                .build();

        BulkTradeOrderResponseDTO partialFailure = BulkTradeOrderResponseDTO.builder()
                .status("FAILURE")
                .totalRequested(5)
                .successful(2)
                .failed(3)
                .build();

        // When & Then
        assertTrue(completeFailure.isCompleteFailure());
        assertFalse(partialFailure.isCompleteFailure());
    }
}