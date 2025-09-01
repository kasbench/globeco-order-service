package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.dto.BatchSubmitResponseDTO;
import org.kasbench.globeco_order_service.dto.OrderSubmitResultDTO;
import org.kasbench.globeco_order_service.dto.BulkTradeOrderResponseDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderResultDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderResponseDTO;
import org.kasbench.globeco_order_service.repository.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceBulkResponseTransformationTest {
    
    @Mock private OrderRepository orderRepository;
    @Mock private BlotterRepository blotterRepository;
    @Mock private StatusRepository statusRepository;
    @Mock private OrderTypeRepository orderTypeRepository;
    @Mock private RestTemplate restTemplate;
    @Mock private SecurityCacheService securityCacheService;
    @Mock private PortfolioCacheService portfolioCacheService;
    @Mock private PortfolioServiceClient portfolioServiceClient;
    @Mock private SecurityServiceClient securityServiceClient;
    @Mock private PlatformTransactionManager transactionManager;
    
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // Manually create OrderService with all required dependencies
        orderService = new OrderService(
                orderRepository,
                statusRepository,
                blotterRepository,
                orderTypeRepository,
                restTemplate,
                securityCacheService,
                portfolioCacheService,
                portfolioServiceClient,
                securityServiceClient,
                transactionManager,
                "http://test-trade-service:8082",
                5000
        );
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_CompleteSuccess() {
        // Arrange
        List<Integer> originalOrderIds = Arrays.asList(101, 102, 103);
        
        List<TradeOrderResultDTO> tradeResults = Arrays.asList(
            TradeOrderResultDTO.builder()
                .requestIndex(0)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(TradeOrderResponseDTO.builder().id(1001).build())
                .build(),
            TradeOrderResultDTO.builder()
                .requestIndex(1)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(TradeOrderResponseDTO.builder().id(1002).build())
                .build(),
            TradeOrderResultDTO.builder()
                .requestIndex(2)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(TradeOrderResponseDTO.builder().id(1003).build())
                .build()
        );
        
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .message("All orders processed successfully")
                .totalRequested(3)
                .successful(3)
                .failed(0)
                .results(tradeResults)
                .build();

        // Act
        BatchSubmitResponseDTO result = orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, originalOrderIds);

        // Assert
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("All 3 orders submitted successfully", result.getMessage());
        assertEquals(3, result.getTotalRequested());
        assertEquals(3, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertEquals(3, result.getResults().size());

        // Verify individual results
        OrderSubmitResultDTO result1 = result.getResults().get(0);
        assertEquals(101, result1.getOrderId());
        assertEquals("SUCCESS", result1.getStatus());
        assertEquals("Order submitted successfully", result1.getMessage());
        assertEquals(1001, result1.getTradeOrderId());
        assertEquals(0, result1.getRequestIndex());

        OrderSubmitResultDTO result2 = result.getResults().get(1);
        assertEquals(102, result2.getOrderId());
        assertEquals("SUCCESS", result2.getStatus());
        assertEquals(1002, result2.getTradeOrderId());
        assertEquals(1, result2.getRequestIndex());

        OrderSubmitResultDTO result3 = result.getResults().get(2);
        assertEquals(103, result3.getOrderId());
        assertEquals("SUCCESS", result3.getStatus());
        assertEquals(1003, result3.getTradeOrderId());
        assertEquals(2, result3.getRequestIndex());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_CompleteFailure() {
        // Arrange
        List<Integer> originalOrderIds = Arrays.asList(201, 202);
        
        List<TradeOrderResultDTO> tradeResults = Arrays.asList(
            TradeOrderResultDTO.builder()
                .requestIndex(0)
                .status("FAILURE")
                .message("Invalid portfolio ID")
                .tradeOrder(null)
                .build(),
            TradeOrderResultDTO.builder()
                .requestIndex(1)
                .status("FAILURE")
                .message("Insufficient funds")
                .tradeOrder(null)
                .build()
        );
        
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("FAILURE")
                .message("All orders failed validation")
                .totalRequested(2)
                .successful(0)
                .failed(2)
                .results(tradeResults)
                .build();

        // Act
        BatchSubmitResponseDTO result = orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, originalOrderIds);

        // Assert
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals("All orders failed validation", result.getMessage());
        assertEquals(2, result.getTotalRequested());
        assertEquals(0, result.getSuccessful());
        assertEquals(2, result.getFailed());
        assertEquals(2, result.getResults().size());

        // Verify individual results
        OrderSubmitResultDTO result1 = result.getResults().get(0);
        assertEquals(201, result1.getOrderId());
        assertEquals("FAILURE", result1.getStatus());
        assertEquals("Invalid portfolio ID", result1.getMessage());
        assertNull(result1.getTradeOrderId());
        assertEquals(0, result1.getRequestIndex());

        OrderSubmitResultDTO result2 = result.getResults().get(1);
        assertEquals(202, result2.getOrderId());
        assertEquals("FAILURE", result2.getStatus());
        assertEquals("Insufficient funds", result2.getMessage());
        assertNull(result2.getTradeOrderId());
        assertEquals(1, result2.getRequestIndex());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_PartialSuccess() {
        // Arrange
        List<Integer> originalOrderIds = Arrays.asList(301, 302, 303);
        
        List<TradeOrderResultDTO> tradeResults = Arrays.asList(
            TradeOrderResultDTO.builder()
                .requestIndex(0)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(TradeOrderResponseDTO.builder().id(2001).build())
                .build(),
            TradeOrderResultDTO.builder()
                .requestIndex(1)
                .status("FAILURE")
                .message("Security not found")
                .tradeOrder(null)
                .build(),
            TradeOrderResultDTO.builder()
                .requestIndex(2)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(TradeOrderResponseDTO.builder().id(2003).build())
                .build()
        );
        
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("PARTIAL")
                .message("Some orders processed successfully")
                .totalRequested(3)
                .successful(2)
                .failed(1)
                .results(tradeResults)
                .build();

        // Act
        BatchSubmitResponseDTO result = orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, originalOrderIds);

        // Assert
        assertNotNull(result);
        assertEquals("PARTIAL", result.getStatus());
        assertEquals("2 of 3 orders submitted successfully, 1 failed", result.getMessage());
        assertEquals(3, result.getTotalRequested());
        assertEquals(2, result.getSuccessful());
        assertEquals(1, result.getFailed());
        assertEquals(3, result.getResults().size());

        // Verify mixed results
        OrderSubmitResultDTO result1 = result.getResults().get(0);
        assertEquals(301, result1.getOrderId());
        assertEquals("SUCCESS", result1.getStatus());
        assertEquals(2001, result1.getTradeOrderId());

        OrderSubmitResultDTO result2 = result.getResults().get(1);
        assertEquals(302, result2.getOrderId());
        assertEquals("FAILURE", result2.getStatus());
        assertEquals("Security not found", result2.getMessage());
        assertNull(result2.getTradeOrderId());

        OrderSubmitResultDTO result3 = result.getResults().get(2);
        assertEquals(303, result3.getOrderId());
        assertEquals("SUCCESS", result3.getStatus());
        assertEquals(2003, result3.getTradeOrderId());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_MissingResults() {
        // Arrange - simulate scenario where some results are missing from trade service response
        List<Integer> originalOrderIds = Arrays.asList(401, 402, 403);
        
        List<TradeOrderResultDTO> tradeResults = Arrays.asList(
            TradeOrderResultDTO.builder()
                .requestIndex(0)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(TradeOrderResponseDTO.builder().id(3001).build())
                .build(),
            // Missing result for index 1
            TradeOrderResultDTO.builder()
                .requestIndex(2)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(TradeOrderResponseDTO.builder().id(3003).build())
                .build()
        );
        
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("PARTIAL")
                .message("Some orders processed")
                .totalRequested(3)
                .successful(2)
                .failed(1)
                .results(tradeResults)
                .build();

        // Act
        BatchSubmitResponseDTO result = orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, originalOrderIds);

        // Assert
        assertNotNull(result);
        assertEquals("PARTIAL", result.getStatus());
        assertEquals(3, result.getResults().size());

        // Verify that missing result is handled as failure
        OrderSubmitResultDTO result1 = result.getResults().get(0);
        assertEquals(401, result1.getOrderId());
        assertEquals("SUCCESS", result1.getStatus());
        assertEquals(3001, result1.getTradeOrderId());

        OrderSubmitResultDTO result2 = result.getResults().get(1);
        assertEquals(402, result2.getOrderId());
        assertEquals("FAILURE", result2.getStatus());
        assertEquals("No result returned from trade service for this order", result2.getMessage());
        assertNull(result2.getTradeOrderId());

        OrderSubmitResultDTO result3 = result.getResults().get(2);
        assertEquals(403, result3.getOrderId());
        assertEquals("SUCCESS", result3.getStatus());
        assertEquals(3003, result3.getTradeOrderId());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_EmptyResults() {
        // Arrange
        List<Integer> originalOrderIds = Arrays.asList(501, 502);
        
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("FAILURE")
                .message("No results returned")
                .totalRequested(2)
                .successful(0)
                .failed(2)
                .results(new ArrayList<>()) // Empty results
                .build();

        // Act
        BatchSubmitResponseDTO result = orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, originalOrderIds);

        // Assert
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals("No results returned", result.getMessage());
        assertEquals(2, result.getTotalRequested());
        assertEquals(0, result.getSuccessful());
        assertEquals(2, result.getFailed());
        assertEquals(2, result.getResults().size());

        // All results should be failures due to missing trade service results
        for (int i = 0; i < result.getResults().size(); i++) {
            OrderSubmitResultDTO orderResult = result.getResults().get(i);
            assertEquals(originalOrderIds.get(i), orderResult.getOrderId());
            assertEquals("FAILURE", orderResult.getStatus());
            assertEquals("No result returned from trade service for this order", orderResult.getMessage());
            assertNull(orderResult.getTradeOrderId());
            assertEquals(i, orderResult.getRequestIndex());
        }
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_NullBulkResponse() {
        // Arrange
        List<Integer> originalOrderIds = Arrays.asList(601, 602);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.transformBulkResponseToOrderServiceFormat(null, originalOrderIds);
        });
        
        assertEquals("Bulk response cannot be null", exception.getMessage());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_NullOriginalOrderIds() {
        // Arrange
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .message("Success")
                .totalRequested(1)
                .successful(1)
                .failed(0)
                .results(new ArrayList<>())
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, null);
        });
        
        assertEquals("Original order IDs cannot be null or empty", exception.getMessage());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_EmptyOriginalOrderIds() {
        // Arrange
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .message("Success")
                .totalRequested(0)
                .successful(0)
                .failed(0)
                .results(new ArrayList<>())
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, new ArrayList<>());
        });
        
        assertEquals("Original order IDs cannot be null or empty", exception.getMessage());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_NullResultsInBulkResponse() {
        // Arrange
        List<Integer> originalOrderIds = Arrays.asList(701, 702);
        
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("FAILURE")
                .message("Service error")
                .totalRequested(2)
                .successful(0)
                .failed(2)
                .results(null) // Null results
                .build();

        // Act
        BatchSubmitResponseDTO result = orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, originalOrderIds);

        // Assert
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals("Service error", result.getMessage());
        assertEquals(2, result.getResults().size());

        // All results should be failures due to null trade service results
        for (int i = 0; i < result.getResults().size(); i++) {
            OrderSubmitResultDTO orderResult = result.getResults().get(i);
            assertEquals(originalOrderIds.get(i), orderResult.getOrderId());
            assertEquals("FAILURE", orderResult.getStatus());
            assertEquals("No result returned from trade service for this order", orderResult.getMessage());
            assertNull(orderResult.getTradeOrderId());
            assertEquals(i, orderResult.getRequestIndex());
        }
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_PreserveRequestIndexMapping() {
        // Arrange - test that request index mapping is preserved correctly
        List<Integer> originalOrderIds = Arrays.asList(801, 802, 803, 804);
        
        List<TradeOrderResultDTO> tradeResults = Arrays.asList(
            TradeOrderResultDTO.builder()
                .requestIndex(3) // Out of order
                .status("SUCCESS")
                .message("Success")
                .tradeOrder(TradeOrderResponseDTO.builder().id(4004).build())
                .build(),
            TradeOrderResultDTO.builder()
                .requestIndex(1) // Out of order
                .status("FAILURE")
                .message("Failed")
                .tradeOrder(null)
                .build(),
            TradeOrderResultDTO.builder()
                .requestIndex(0) // Out of order
                .status("SUCCESS")
                .message("Success")
                .tradeOrder(TradeOrderResponseDTO.builder().id(4001).build())
                .build()
            // Missing index 2
        );
        
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("PARTIAL")
                .message("Mixed results")
                .totalRequested(4)
                .successful(2)
                .failed(2)
                .results(tradeResults)
                .build();

        // Act
        BatchSubmitResponseDTO result = orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, originalOrderIds);

        // Assert
        assertNotNull(result);
        assertEquals(4, result.getResults().size());

        // Verify correct mapping by request index
        OrderSubmitResultDTO result0 = result.getResults().get(0); // Index 0
        assertEquals(801, result0.getOrderId());
        assertEquals("SUCCESS", result0.getStatus());
        assertEquals(4001, result0.getTradeOrderId());
        assertEquals(0, result0.getRequestIndex());

        OrderSubmitResultDTO result1 = result.getResults().get(1); // Index 1
        assertEquals(802, result1.getOrderId());
        assertEquals("FAILURE", result1.getStatus());
        assertEquals("Failed", result1.getMessage());
        assertNull(result1.getTradeOrderId());
        assertEquals(1, result1.getRequestIndex());

        OrderSubmitResultDTO result2 = result.getResults().get(2); // Index 2 - missing
        assertEquals(803, result2.getOrderId());
        assertEquals("FAILURE", result2.getStatus());
        assertEquals("No result returned from trade service for this order", result2.getMessage());
        assertNull(result2.getTradeOrderId());
        assertEquals(2, result2.getRequestIndex());

        OrderSubmitResultDTO result3 = result.getResults().get(3); // Index 3
        assertEquals(804, result3.getOrderId());
        assertEquals("SUCCESS", result3.getStatus());
        assertEquals(4004, result3.getTradeOrderId());
        assertEquals(3, result3.getRequestIndex());
    }
}