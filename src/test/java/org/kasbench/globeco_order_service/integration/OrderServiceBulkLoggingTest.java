package org.kasbench.globeco_order_service.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.dto.BatchSubmitResponseDTO;
import org.kasbench.globeco_order_service.dto.BulkTradeOrderRequestDTO;
import org.kasbench.globeco_order_service.dto.BulkTradeOrderResponseDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderPostDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderResultDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderResponseDTO;
import org.kasbench.globeco_order_service.entity.*;
import org.kasbench.globeco_order_service.repository.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for comprehensive logging behavior in bulk order submission.
 * Verifies that appropriate log messages are generated for various scenarios
 * including success, failure, performance metrics, and error conditions.
 */
@ExtendWith(MockitoExtension.class)
public class OrderServiceBulkLoggingTest {

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
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    private static final String TRADE_SERVICE_URL = "http://test-trade-service:8082";

    @BeforeEach
    void setUp() {
        // Setup logging capture
        logger = (Logger) LoggerFactory.getLogger(OrderService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        logger.setLevel(Level.DEBUG);

        // Mock transaction template to execute immediately
        TransactionTemplate mockTransactionTemplate = mock(TransactionTemplate.class);
        when(mockTransactionTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class).doInTransaction(null);
        });

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

    // =============== SUCCESSFUL SUBMISSION LOGGING TESTS ===============

    @Test
    void testSubmitOrdersBatch_SuccessfulSubmission_LogsPerformanceMetrics() {
        // Arrange
        List<Integer> orderIds = Arrays.asList(1, 2, 3);
        List<Order> validOrders = createValidOrders(3);
        BulkTradeOrderResponseDTO successResponse = createSuccessfulBulkResponse(3);
        Status sentStatus = createSentStatus();
        
        // Mock dependencies
        when(orderRepository.findAllById(orderIds)).thenReturn(validOrders);
        when(statusRepository.findAll()).thenReturn(Arrays.asList(sentStatus));
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(BulkTradeOrderResponseDTO.class)))
                .thenReturn(new ResponseEntity<>(successResponse, HttpStatus.CREATED));
        when(orderRepository.saveAll(anyList())).thenReturn(validOrders);

        // Act
        BatchSubmitResponseDTO result = orderService.submitOrdersBatch(orderIds);

        // Assert
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());

        // Verify performance logging
        List<ILoggingEvent> logEvents = logAppender.list;
        
        // Check for bulk submission start log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getMessage().contains("BULK_SUBMISSION: Starting bulk order submission for 3 orders")));
        
        // Check for performance breakdown log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getMessage().contains("BULK_SUBMISSION_PERFORMANCE_BREAKDOWN")));
        
        // Check for completion log with success metrics
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getMessage().contains("BULK_SUBMISSION: Completed successfully") &&
                event.getMessage().contains("3 successful, 0 failed")));
        
        // Check for average processing time log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getMessage().contains("Average processing time per successful order")));
    }

    @Test
    void testCallTradeServiceBulk_SuccessfulCall_LogsTradeServiceMetrics() {
        // Arrange
        BulkTradeOrderRequestDTO bulkRequest = createValidBulkRequest(2);
        BulkTradeOrderResponseDTO successResponse = createSuccessfulBulkResponse(2);
        
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(BulkTradeOrderResponseDTO.class)))
                .thenReturn(new ResponseEntity<>(successResponse, HttpStatus.CREATED));

        // Act
        BulkTradeOrderResponseDTO result = orderService.callTradeServiceBulk(bulkRequest);

        // Assert
        assertNotNull(result);
        
        // Verify trade service logging
        List<ILoggingEvent> logEvents = logAppender.list;
        
        // Check for trade service call start log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getMessage().contains("BULK_SUBMISSION: Sending HTTP POST to trade service bulk endpoint for 2 orders")));
        
        // Check for trade service response log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getMessage().contains("BULK_SUBMISSION: Trade service HTTP response received for 2 orders")));
        
        // Check for trade service success log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getMessage().contains("BULK_SUBMISSION: Trade service SUCCESS for 2 orders")));
        
        // Check for performance metrics log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getMessage().contains("BULK_SUBMISSION_PERFORMANCE: Processed 2 orders")));
    }

    // =============== ERROR LOGGING TESTS ===============

    @Test
    void testCallTradeServiceBulk_HttpServerError_LogsDetailedError() {
        // Arrange
        BulkTradeOrderRequestDTO bulkRequest = createValidBulkRequest(2);
        
        HttpServerErrorException serverException = new HttpServerErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Database connection failed", 
                "{\"error\":\"Connection timeout\"}".getBytes(), null);
        
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(BulkTradeOrderResponseDTO.class)))
                .thenThrow(serverException);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> orderService.callTradeServiceBulk(bulkRequest));

        // Verify error logging
        List<ILoggingEvent> logEvents = logAppender.list;
        
        // Check for detailed error log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getLevel() == Level.ERROR &&
                event.getMessage().contains("BULK_SUBMISSION: Trade service server error for 2 orders") &&
                event.getMessage().contains("500 INTERNAL_SERVER_ERROR")));
    }

    @Test
    void testSubmitOrdersBatch_ValidationFailure_LogsValidationError() {
        // Arrange: Create oversized batch
        List<Integer> orderIds = new ArrayList<>();
        for (int i = 1; i <= 101; i++) {
            orderIds.add(i);
        }

        // Act
        BatchSubmitResponseDTO result = orderService.submitOrdersBatch(orderIds);

        // Assert
        assertEquals("VALIDATION_FAILURE", result.getStatus());
        
        // Verify validation error logging
        List<ILoggingEvent> logEvents = logAppender.list;
        
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getLevel() == Level.WARN &&
                event.getMessage().contains("BULK_SUBMISSION: Batch size validation failed")));
    }

    @Test
    void testLoadAndValidateOrdersForBulkSubmission_LogsValidationMetrics() {
        // Arrange
        List<Integer> orderIds = Arrays.asList(1, 2, 3, 4);
        List<Order> allOrders = createMixedValidityOrders(); // 2 valid, 2 invalid
        
        when(orderRepository.findAllById(orderIds)).thenReturn(allOrders);

        // Act
        List<Order> result = orderService.loadAndValidateOrdersForBulkSubmission(orderIds);

        // Assert
        assertEquals(2, result.size()); // Only valid orders returned
        
        // Verify validation logging
        List<ILoggingEvent> logEvents = logAppender.list;
        
        // Check for loading log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getMessage().contains("BULK_VALIDATION: Loading and validating 4 orders")));
        
        // Check for database load performance log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getMessage().contains("BULK_VALIDATION: Loaded 4 orders from database")));
        
        // Check for validation performance log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getMessage().contains("BULK_VALIDATION_PERFORMANCE: Validated 4 orders")));
        
        // Check for validation errors log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getLevel() == Level.WARN &&
                event.getMessage().contains("validation errors encountered")));
    }

    // =============== STATUS UPDATE LOGGING TESTS ===============

    @Test
    void testUpdateOrderStatusesFromBulkResponse_LogsStatusUpdateMetrics() {
        // Arrange
        List<Order> orders = createValidOrders(3);
        BulkTradeOrderResponseDTO bulkResponse = createPartialSuccessBulkResponse(3, 2, 1);
        Status sentStatus = createSentStatus();
        
        when(statusRepository.findAll()).thenReturn(Arrays.asList(sentStatus));
        when(orderRepository.saveAll(anyList())).thenReturn(orders.subList(0, 2));

        // Act
        orderService.updateOrderStatusesFromBulkResponse(orders, bulkResponse);

        // Verify status update logging
        List<ILoggingEvent> logEvents = logAppender.list;
        
        // Check for status update start log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getMessage().contains("BULK_STATUS_UPDATE: Starting status update for 3 orders")));
        
        // Check for batch database update log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getMessage().contains("BULK_STATUS_UPDATE: Performing batch database update")));
        
        // Check for completion metrics log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getMessage().contains("BULK_STATUS_UPDATE: Completed status update") &&
                event.getMessage().contains("2 successful, 1 failed")));
        
        // Check for performance metrics log
        assertTrue(logEvents.stream().anyMatch(event -> 
                event.getMessage().contains("BULK_STATUS_UPDATE_METRICS: Success rate")));
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

    private List<Order> createMixedValidityOrders() {
        List<Order> orders = new ArrayList<>();
        
        Blotter blotter = Blotter.builder().id(1).name("Test Blotter").version(1).build();
        Status newStatus = Status.builder().id(1).abbreviation("NEW").description("New Order").version(1).build();
        Status sentStatus = Status.builder().id(2).abbreviation("SENT").description("Sent Order").version(1).build();
        OrderType buyOrderType = OrderType.builder().id(1).abbreviation("BUY").description("Buy Order").version(1).build();
        
        // Valid order 1
        orders.add(Order.builder()
                .id(1)
                .blotter(blotter)
                .status(newStatus)
                .portfolioId("PORTFOLIO_001")
                .orderType(buyOrderType)
                .securityId("AAPL")
                .quantity(new BigDecimal("100.00"))
                .limitPrice(new BigDecimal("150.25"))
                .orderTimestamp(OffsetDateTime.now())
                .version(1)
                .build());
        
        // Valid order 2
        orders.add(Order.builder()
                .id(2)
                .blotter(blotter)
                .status(newStatus)
                .portfolioId("PORTFOLIO_002")
                .orderType(buyOrderType)
                .securityId("GOOGL")
                .quantity(new BigDecimal("50.00"))
                .limitPrice(new BigDecimal("2500.75"))
                .orderTimestamp(OffsetDateTime.now())
                .version(1)
                .build());
        
        // Invalid order 3 - wrong status
        orders.add(Order.builder()
                .id(3)
                .blotter(blotter)
                .status(sentStatus) // Invalid status
                .portfolioId("PORTFOLIO_003")
                .orderType(buyOrderType)
                .securityId("MSFT")
                .quantity(new BigDecimal("75.00"))
                .limitPrice(new BigDecimal("300.50"))
                .orderTimestamp(OffsetDateTime.now())
                .version(1)
                .build());
        
        // Invalid order 4 - already has tradeOrderId
        orders.add(Order.builder()
                .id(4)
                .blotter(blotter)
                .status(newStatus)
                .portfolioId("PORTFOLIO_004")
                .orderType(buyOrderType)
                .securityId("TSLA")
                .quantity(new BigDecimal("25.00"))
                .limitPrice(new BigDecimal("800.00"))
                .tradeOrderId(9999) // Already processed
                .orderTimestamp(OffsetDateTime.now())
                .version(1)
                .build());
        
        return orders;
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

    private Status createSentStatus() {
        return Status.builder()
                .id(2)
                .abbreviation("SENT")
                .description("Sent Order")
                .version(1)
                .build();
    }
}