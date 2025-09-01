package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.dto.BatchSubmitResponseDTO;
import org.kasbench.globeco_order_service.dto.BulkTradeOrderRequestDTO;
import org.kasbench.globeco_order_service.dto.BulkTradeOrderResponseDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderPostDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderResultDTO;
import org.kasbench.globeco_order_service.entity.*;
import org.kasbench.globeco_order_service.repository.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for error handling and logging in bulk order submission.
 * Tests various error scenarios including validation errors, network failures,
 * trade service errors, and database issues.
 */
@ExtendWith(MockitoExtension.class)
public class OrderServiceBulkErrorHandlingTest {

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

    private OrderService orderService;
    private TransactionTemplate mockTransactionTemplate;

    private static final String TRADE_SERVICE_URL = "http://test-trade-service:8082";
    private static final int TRADE_SERVICE_TIMEOUT = 5000;

    @BeforeEach
    void setUp() {
        mockTransactionTemplate = mock(TransactionTemplate.class);
        
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
                TRADE_SERVICE_URL,
                TRADE_SERVICE_TIMEOUT
        );
    }

    // =============== VALIDATION ERROR TESTS ===============

    @Test
    void testSubmitOrdersBatch_NullOrderIds() {
        // Act
        BatchSubmitResponseDTO result = orderService.submitOrdersBatch(null);

        // Assert
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals("Order IDs cannot be null", result.getMessage());
        assertEquals(0, result.getTotalRequested());
        assertEquals(0, result.getSuccessful());
        assertEquals(0, result.getFailed());
    }

    @Test
    void testSubmitOrdersBatch_EmptyOrderIds() {
        // Act
        BatchSubmitResponseDTO result = orderService.submitOrdersBatch(new ArrayList<>());

        // Assert
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals("No orders provided for submission", result.getMessage());
        assertEquals(0, result.getTotalRequested());
        assertEquals(0, result.getSuccessful());
        assertEquals(0, result.getFailed());
    }

    @Test
    void testSubmitOrdersBatch_ExceedsMaxBatchSize() {
        // Arrange: Create a list with 101 order IDs (exceeds limit of 100)
        List<Integer> orderIds = new ArrayList<>();
        for (int i = 1; i <= 101; i++) {
            orderIds.add(i);
        }

        // Act
        BatchSubmitResponseDTO result = orderService.submitOrdersBatch(orderIds);

        // Assert
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertTrue(result.getMessage().contains("exceeds maximum allowed size"));
        assertEquals(0, result.getTotalRequested()); // validationFailure sets totalRequested to 0
        assertEquals(0, result.getSuccessful());
        assertEquals(0, result.getFailed()); // validationFailure sets failed to 0
    }

    @Test
    void testBuildBulkTradeOrderRequest_NullOrdersList() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(null)
        );
        assertEquals("Orders list cannot be null or empty", exception.getMessage());
    }

    @Test
    void testBuildBulkTradeOrderRequest_EmptyOrdersList() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(new ArrayList<>())
        );
        assertEquals("Orders list cannot be null or empty", exception.getMessage());
    }

    @Test
    void testBuildBulkTradeOrderRequest_OrderWithMissingFields() {
        // Arrange: Create order with missing portfolio ID
        Order invalidOrder = Order.builder()
                .id(1)
                .portfolioId(null) // Missing required field
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.buildBulkTradeOrderRequest(Collections.singletonList(invalidOrder))
        );
        assertEquals("Portfolio ID is required for order 1", exception.getMessage());
    }

    // =============== TRADE SERVICE ERROR TESTS ===============

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
        assertEquals(clientException, exception.getCause());
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
        assertEquals(serverException, exception.getCause());
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
        assertEquals(networkException, exception.getCause());
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
        assertEquals(restException, exception.getCause());
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
        assertEquals(unexpectedException, exception.getCause());
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

    // =============== RESPONSE TRANSFORMATION ERROR TESTS ===============

    @Test
    void testTransformBulkResponseToOrderServiceFormat_NullBulkResponse() {
        // Arrange
        List<Integer> orderIds = Arrays.asList(1, 2, 3);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.transformBulkResponseToOrderServiceFormat(null, orderIds)
        );
        assertEquals("Bulk response cannot be null", exception.getMessage());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_NullOrderIds() {
        // Arrange
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .totalRequested(2)
                .successful(2)
                .failed(0)
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, null)
        );
        assertEquals("Original order IDs cannot be null or empty", exception.getMessage());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_EmptyOrderIds() {
        // Arrange
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .totalRequested(0)
                .successful(0)
                .failed(0)
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, new ArrayList<>())
        );
        assertEquals("Original order IDs cannot be null or empty", exception.getMessage());
    }

    // =============== DATABASE ERROR TESTS ===============

    @Test
    void testUpdateOrderStatusesFromBulkResponse_NullOrders() {
        // Arrange
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .totalRequested(2)
                .successful(2)
                .failed(0)
                .results(new ArrayList<>())
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.updateOrderStatusesFromBulkResponse(null, bulkResponse)
        );
        assertEquals("Orders list cannot be null or empty", exception.getMessage());
    }

    @Test
    void testUpdateOrderStatusesFromBulkResponse_EmptyOrders() {
        // Arrange
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .totalRequested(0)
                .successful(0)
                .failed(0)
                .results(new ArrayList<>())
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.updateOrderStatusesFromBulkResponse(new ArrayList<>(), bulkResponse)
        );
        assertEquals("Orders list cannot be null or empty", exception.getMessage());
    }

    @Test
    void testUpdateOrderStatusesFromBulkResponse_NullBulkResponse() {
        // Arrange
        List<Order> orders = createValidOrders(2);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.updateOrderStatusesFromBulkResponse(orders, null)
        );
        assertEquals("Bulk response cannot be null", exception.getMessage());
    }

    // =============== INTEGRATION ERROR TESTS ===============

    @Test
    void testSubmitOrdersBatch_TradeServiceFailure_ReturnsErrorResponse() {
        // Arrange
        List<Integer> orderIds = Arrays.asList(1, 2);
        List<Order> validOrders = createValidOrders(2);
        
        // Mock successful order loading
        when(orderRepository.findAllById(orderIds)).thenReturn(validOrders);
        
        // Mock trade service failure
        HttpServerErrorException serverException = new HttpServerErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Database error");
        
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(BulkTradeOrderResponseDTO.class)))
                .thenThrow(serverException);

        // Act
        BatchSubmitResponseDTO result = orderService.submitOrdersBatch(orderIds);

        // Assert
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertTrue(result.getMessage().contains("Bulk submission failed"));
        assertEquals(2, result.getTotalRequested());
        assertEquals(0, result.getSuccessful());
        assertEquals(2, result.getFailed());
        assertEquals(2, result.getResults().size());
        
        // Verify all results are failures
        result.getResults().forEach(resultDto -> {
            assertEquals("FAILURE", resultDto.getStatus());
            assertTrue(resultDto.getMessage().contains("Bulk submission failed"));
        });
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

    private List<Order> createValidOrders(int count) {
        List<Order> orders = new ArrayList<>();
        
        Blotter blotter = Blotter.builder().id(1).name("Test Blotter").version(1).build();
        Status newStatus = Status.builder().id(1).abbreviation("NEW").description("New Order").version(1).build();
        OrderType buyOrderType = OrderType.builder().id(1).abbreviation("BUY").description("Buy Order").version(1).build();
        
        for (int i = 1; i <= count; i++) {
            Order order = Order.builder()
                    .id(i)
                    .blotter(blotter)
                    .status(newStatus)
                    .portfolioId("PORTFOLIO_" + String.format("%03d", i))
                    .orderType(buyOrderType)
                    .securityId("SECURITY_" + String.format("%03d", i))
                    .quantity(new BigDecimal("100.00"))
                    .limitPrice(new BigDecimal("50.25"))
                    .orderTimestamp(OffsetDateTime.now())
                    .version(1)
                    .build();
            orders.add(order);
        }
        
        return orders;
    }
}