package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.dto.BulkTradeOrderRequestDTO;
import org.kasbench.globeco_order_service.dto.BulkTradeOrderResponseDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderPostDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderResultDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderResponseDTO;
import org.kasbench.globeco_order_service.entity.*;
import org.kasbench.globeco_order_service.repository.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceBulkApiClientTest {

    @Mock private OrderRepository orderRepository;
    @Mock private StatusRepository statusRepository;
    @Mock private BlotterRepository blotterRepository;
    @Mock private OrderTypeRepository orderTypeRepository;
    @Mock private RestTemplate restTemplate;
    @Mock private SecurityCacheService securityCacheService;
    @Mock private PortfolioCacheService portfolioCacheService;
    @Mock private PortfolioServiceClient portfolioServiceClient;
    @Mock private SecurityServiceClient securityServiceClient;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private io.micrometer.core.instrument.MeterRegistry meterRegistry;
    @Mock private BulkSubmissionPerformanceMonitor performanceMonitor;
    @Mock private BatchUpdateService batchUpdateService;

    private OrderService orderService;

    private static final String TRADE_SERVICE_URL = "http://test-trade-service:8082";

    @BeforeEach
    void setUp() {
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
                meterRegistry,
                performanceMonitor,
                batchUpdateService,
                TRADE_SERVICE_URL
        );
    }

    // =============== SUCCESSFUL BULK SUBMISSION TESTS ===============

    @Test
    void testCallTradeServiceBulk_Success() {
        // Arrange
        BulkTradeOrderRequestDTO bulkRequest = createValidBulkRequest(3);
        BulkTradeOrderResponseDTO expectedResponse = createSuccessfulBulkResponse(3);
        
        ResponseEntity<BulkTradeOrderResponseDTO> responseEntity = 
                new ResponseEntity<>(expectedResponse, HttpStatus.CREATED);
        
        when(restTemplate.postForEntity(
                eq(TRADE_SERVICE_URL + "/api/v1/tradeOrders/bulk"),
                any(HttpEntity.class),
                eq(BulkTradeOrderResponseDTO.class)
        )).thenReturn(responseEntity);

        // Act
        BulkTradeOrderResponseDTO result = orderService.callTradeServiceBulk(bulkRequest);

        // Assert
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(3, result.getTotalRequested());
        assertEquals(3, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertTrue(result.isSuccess());
        assertTrue(result.isCompleteSuccess());
        assertFalse(result.isFailure());

        // Verify RestTemplate was called with correct parameters
        verify(restTemplate).postForEntity(
                eq(TRADE_SERVICE_URL + "/api/v1/tradeOrders/bulk"),
                any(HttpEntity.class),
                eq(BulkTradeOrderResponseDTO.class)
        );
    }

    @Test
    void testCallTradeServiceBulk_PartialSuccess() {
        // Arrange
        BulkTradeOrderRequestDTO bulkRequest = createValidBulkRequest(5);
        BulkTradeOrderResponseDTO expectedResponse = createPartialSuccessBulkResponse(5, 3, 2);
        
        ResponseEntity<BulkTradeOrderResponseDTO> responseEntity = 
                new ResponseEntity<>(expectedResponse, HttpStatus.CREATED);
        
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(BulkTradeOrderResponseDTO.class)))
                .thenReturn(responseEntity);

        // Act
        BulkTradeOrderResponseDTO result = orderService.callTradeServiceBulk(bulkRequest);

        // Assert
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(5, result.getTotalRequested());
        assertEquals(3, result.getSuccessful());
        assertEquals(2, result.getFailed());
        assertTrue(result.isSuccess());
        assertFalse(result.isCompleteSuccess());
        assertFalse(result.isCompleteFailure());
        assertEquals(0.6, result.getSuccessRate(), 0.01);
    }

    // =============== ERROR HANDLING TESTS ===============

    @Test
    void testCallTradeServiceBulk_BadRequest() {
        // Arrange
        BulkTradeOrderRequestDTO bulkRequest = createValidBulkRequest(2);
        BulkTradeOrderResponseDTO errorResponse = BulkTradeOrderResponseDTO.builder()
                .status("FAILURE")
                .message("Invalid request data")
                .totalRequested(2)
                .successful(0)
                .failed(2)
                .build();
        
        ResponseEntity<BulkTradeOrderResponseDTO> responseEntity = 
                new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(BulkTradeOrderResponseDTO.class)))
                .thenReturn(responseEntity);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.callTradeServiceBulk(bulkRequest);
        });
        
        assertTrue(exception.getMessage().contains("Trade service rejected bulk request with HTTP 400"));
    }

    @Test
    void testCallTradeServiceBulk_ServerError() {
        // Arrange
        BulkTradeOrderRequestDTO bulkRequest = createValidBulkRequest(2);
        
        ResponseEntity<BulkTradeOrderResponseDTO> responseEntity = 
                new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(BulkTradeOrderResponseDTO.class)))
                .thenReturn(responseEntity);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.callTradeServiceBulk(bulkRequest);
        });
        
        assertTrue(exception.getMessage().contains("Trade service internal error during bulk submission"));
    }

    @Test
    void testCallTradeServiceBulk_HttpClientError() {
        // Arrange
        BulkTradeOrderRequestDTO bulkRequest = createValidBulkRequest(2);
        
        HttpClientErrorException clientException = new HttpClientErrorException(
                HttpStatus.BAD_REQUEST, "Bad Request", "{\"error\":\"Invalid data\"}".getBytes(), null);
        
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(BulkTradeOrderResponseDTO.class)))
                .thenThrow(clientException);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.callTradeServiceBulk(bulkRequest);
        });
        
        assertTrue(exception.getMessage().contains("Trade service HTTP client error"));
        assertTrue(exception.getMessage().contains("400 BAD_REQUEST"));
    }

    @Test
    void testCallTradeServiceBulk_HttpServerError() {
        // Arrange
        BulkTradeOrderRequestDTO bulkRequest = createValidBulkRequest(2);
        
        HttpServerErrorException serverException = new HttpServerErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", 
                "{\"error\":\"Database connection failed\"}".getBytes(), null);
        
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(BulkTradeOrderResponseDTO.class)))
                .thenThrow(serverException);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.callTradeServiceBulk(bulkRequest);
        });
        
        assertTrue(exception.getMessage().contains("Trade service server error"));
        assertTrue(exception.getMessage().contains("500 INTERNAL_SERVER_ERROR"));
    }

    @Test
    void testCallTradeServiceBulk_NetworkConnectivityError() {
        // Arrange
        BulkTradeOrderRequestDTO bulkRequest = createValidBulkRequest(2);
        
        ResourceAccessException networkException = new ResourceAccessException(
                "Connection refused: connect");
        
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(BulkTradeOrderResponseDTO.class)))
                .thenThrow(networkException);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.callTradeServiceBulk(bulkRequest);
        });
        
        assertTrue(exception.getMessage().contains("Trade service connectivity error"));
        assertTrue(exception.getMessage().contains("Connection refused"));
    }

    @Test
    void testCallTradeServiceBulk_RestClientError() {
        // Arrange
        BulkTradeOrderRequestDTO bulkRequest = createValidBulkRequest(2);
        
        RestClientException restException = new RestClientException("Serialization error");
        
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(BulkTradeOrderResponseDTO.class)))
                .thenThrow(restException);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.callTradeServiceBulk(bulkRequest);
        });
        
        assertTrue(exception.getMessage().contains("Trade service REST client error"));
        assertTrue(exception.getMessage().contains("Serialization error"));
    }

    @Test
    void testCallTradeServiceBulk_UnexpectedError() {
        // Arrange
        BulkTradeOrderRequestDTO bulkRequest = createValidBulkRequest(2);
        
        RuntimeException unexpectedException = new RuntimeException("Unexpected system error");
        
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(BulkTradeOrderResponseDTO.class)))
                .thenThrow(unexpectedException);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.callTradeServiceBulk(bulkRequest);
        });
        
        assertTrue(exception.getMessage().contains("Unexpected error during trade service bulk call"));
        assertTrue(exception.getMessage().contains("Unexpected system error"));
    }

    // =============== VALIDATION TESTS ===============

    @Test
    void testCallTradeServiceBulk_NullRequest() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.callTradeServiceBulk(null);
        });
        
        assertEquals("Bulk request cannot be null or empty", exception.getMessage());
    }

    @Test
    void testCallTradeServiceBulk_EmptyTradeOrders() {
        // Arrange
        BulkTradeOrderRequestDTO emptyRequest = BulkTradeOrderRequestDTO.builder()
                .tradeOrders(new ArrayList<>())
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.callTradeServiceBulk(emptyRequest);
        });
        
        assertEquals("Bulk request cannot be null or empty", exception.getMessage());
    }

    @Test
    void testCallTradeServiceBulk_NullTradeOrders() {
        // Arrange
        BulkTradeOrderRequestDTO nullRequest = BulkTradeOrderRequestDTO.builder()
                .tradeOrders(null)
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.callTradeServiceBulk(nullRequest);
        });
        
        assertEquals("Bulk request cannot be null or empty", exception.getMessage());
    }

    @Test
    void testCallTradeServiceBulk_NullResponseBody() {
        // Arrange
        BulkTradeOrderRequestDTO bulkRequest = createValidBulkRequest(2);
        
        ResponseEntity<BulkTradeOrderResponseDTO> responseEntity = 
                new ResponseEntity<>(null, HttpStatus.CREATED);
        
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(BulkTradeOrderResponseDTO.class)))
                .thenReturn(responseEntity);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.callTradeServiceBulk(bulkRequest);
        });
        
        assertTrue(exception.getMessage().contains("Trade service returned null response body"));
    }

    // =============== HELPER METHODS ===============

    private BulkTradeOrderRequestDTO createValidBulkRequest(int orderCount) {
        List<TradeOrderPostDTO> tradeOrders = new ArrayList<>();
        
        for (int i = 1; i <= orderCount; i++) {
            TradeOrderPostDTO tradeOrder = TradeOrderPostDTO.builder()
                    .orderId(i)
                    .portfolioId("PORTFOLIO_" + String.format("%03d", i))
                    .orderType("BUY")
                    .securityId("SECURITY_" + String.format("%03d", i))
                    .quantity(new BigDecimal("100.00"))
                    .limitPrice(new BigDecimal("50.25"))
                    .tradeTimestamp(OffsetDateTime.now())
                    .blotterId(1)
                    .build();
            tradeOrders.add(tradeOrder);
        }
        
        return BulkTradeOrderRequestDTO.of(tradeOrders);
    }

    private BulkTradeOrderResponseDTO createSuccessfulBulkResponse(int orderCount) {
        List<TradeOrderResultDTO> results = new ArrayList<>();
        
        for (int i = 0; i < orderCount; i++) {
            TradeOrderResponseDTO tradeOrder = TradeOrderResponseDTO.builder()
                    .id(1000 + i)
                    .orderId(i + 1)
                    .portfolioId("PORTFOLIO_" + String.format("%03d", i + 1))
                    .orderType("BUY")
                    .securityId("SECURITY_" + String.format("%03d", i + 1))
                    .quantity(new BigDecimal("100.00"))
                    .limitPrice(new BigDecimal("50.25"))
                    .build();
            
            TradeOrderResultDTO result = TradeOrderResultDTO.success(i, tradeOrder);
            results.add(result);
        }
        
        return BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .message("All orders processed successfully")
                .totalRequested(orderCount)
                .successful(orderCount)
                .failed(0)
                .results(results)
                .build();
    }

    private BulkTradeOrderResponseDTO createPartialSuccessBulkResponse(int totalCount, int successCount, int failCount) {
        List<TradeOrderResultDTO> results = new ArrayList<>();
        
        // Add successful results
        for (int i = 0; i < successCount; i++) {
            TradeOrderResponseDTO tradeOrder = TradeOrderResponseDTO.builder()
                    .id(1000 + i)
                    .orderId(i + 1)
                    .portfolioId("PORTFOLIO_" + String.format("%03d", i + 1))
                    .orderType("BUY")
                    .securityId("SECURITY_" + String.format("%03d", i + 1))
                    .quantity(new BigDecimal("100.00"))
                    .limitPrice(new BigDecimal("50.25"))
                    .build();
            
            TradeOrderResultDTO result = TradeOrderResultDTO.success(i, tradeOrder);
            results.add(result);
        }
        
        // Add failed results
        for (int i = successCount; i < totalCount; i++) {
            TradeOrderResultDTO result = TradeOrderResultDTO.failure(i, "Validation error: Invalid portfolio");
            results.add(result);
        }
        
        return BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .message("Partial success: " + successCount + " succeeded, " + failCount + " failed")
                .totalRequested(totalCount)
                .successful(successCount)
                .failed(failCount)
                .results(results)
                .build();
    }
}