package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.entity.*;
import org.kasbench.globeco_order_service.repository.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
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
@MockitoSettings(strictness = Strictness.LENIENT)
public class OrderServiceBulkSubmissionTest {
    
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
    private TransactionTemplate transactionTemplate;
    private TransactionTemplate readOnlyTransactionTemplate;

    @BeforeEach
    void setUp() {
        // Mock transaction template behavior
        TransactionStatus mockStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(mockStatus);
        
        // Create OrderService instance with all required dependencies
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

    // =============== BULK SUBMISSION VALIDATION TESTS ===============
    
    @Test
    void testLoadAndValidateOrdersForBulkSubmission_AllValid() {
        // Setup test data - orders in NEW status without tradeOrderId
        List<Integer> orderIds = Arrays.asList(1, 2, 3);
        
        Order validOrder1 = createValidOrderForBulkSubmission(1);
        Order validOrder2 = createValidOrderForBulkSubmission(2);
        Order validOrder3 = createValidOrderForBulkSubmission(3);
        
        List<Order> allOrders = Arrays.asList(validOrder1, validOrder2, validOrder3);
        
        // Mock repository response
        when(orderRepository.findAllById(orderIds)).thenReturn(allOrders);
        
        // Execute
        List<Order> result = orderService.loadAndValidateOrdersForBulkSubmission(orderIds);
        
        // Verify
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(validOrder1, result.get(0));
        assertEquals(validOrder2, result.get(1));
        assertEquals(validOrder3, result.get(2));
        
        verify(orderRepository).findAllById(orderIds);
    }
    
    @Test
    void testLoadAndValidateOrdersForBulkSubmission_FilterInvalidStatus() {
        // Setup test data - mix of NEW and SENT status orders
        List<Integer> orderIds = Arrays.asList(1, 2, 3);
        
        Order validOrder = createValidOrderForBulkSubmission(1);
        Order sentOrder = createValidOrderForBulkSubmission(2);
        sentOrder.setStatus(Status.builder().id(2).abbreviation("SENT").description("Sent").version(1).build());
        Order cancelledOrder = createValidOrderForBulkSubmission(3);
        cancelledOrder.setStatus(Status.builder().id(3).abbreviation("CANCELLED").description("Cancelled").version(1).build());
        
        List<Order> allOrders = Arrays.asList(validOrder, sentOrder, cancelledOrder);
        
        // Mock repository response
        when(orderRepository.findAllById(orderIds)).thenReturn(allOrders);
        
        // Execute
        List<Order> result = orderService.loadAndValidateOrdersForBulkSubmission(orderIds);
        
        // Verify - only the NEW status order should be returned
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(validOrder, result.get(0));
        assertEquals("NEW", result.get(0).getStatus().getAbbreviation());
    }
    
    @Test
    void testLoadAndValidateOrdersForBulkSubmission_FilterAlreadyProcessed() {
        // Setup test data - orders with existing tradeOrderId
        List<Integer> orderIds = Arrays.asList(1, 2);
        
        Order validOrder = createValidOrderForBulkSubmission(1);
        Order processedOrder = createValidOrderForBulkSubmission(2);
        processedOrder.setTradeOrderId(12345); // Already processed
        
        List<Order> allOrders = Arrays.asList(validOrder, processedOrder);
        
        // Mock repository response
        when(orderRepository.findAllById(orderIds)).thenReturn(allOrders);
        
        // Execute
        List<Order> result = orderService.loadAndValidateOrdersForBulkSubmission(orderIds);
        
        // Verify - only the unprocessed order should be returned
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(validOrder, result.get(0));
        assertNull(result.get(0).getTradeOrderId());
    }
    
    @Test
    void testLoadAndValidateOrdersForBulkSubmission_FilterMissingRequiredFields() {
        // Setup test data - orders with missing required fields
        List<Integer> orderIds = Arrays.asList(1, 2, 3, 4);
        
        Order validOrder = createValidOrderForBulkSubmission(1);
        
        Order missingPortfolioId = createValidOrderForBulkSubmission(2);
        missingPortfolioId.setPortfolioId(null);
        
        Order missingSecurityId = createValidOrderForBulkSubmission(3);
        missingSecurityId.setSecurityId("");
        
        Order invalidQuantity = createValidOrderForBulkSubmission(4);
        invalidQuantity.setQuantity(BigDecimal.ZERO);
        
        List<Order> allOrders = Arrays.asList(validOrder, missingPortfolioId, missingSecurityId, invalidQuantity);
        
        // Mock repository response
        when(orderRepository.findAllById(orderIds)).thenReturn(allOrders);
        
        // Execute
        List<Order> result = orderService.loadAndValidateOrdersForBulkSubmission(orderIds);
        
        // Verify - only the valid order should be returned
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(validOrder, result.get(0));
    }
    
    @Test
    void testLoadAndValidateOrdersForBulkSubmission_EmptyInput() {
        // Test with empty order IDs list
        List<Order> result = orderService.loadAndValidateOrdersForBulkSubmission(new ArrayList<>());
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        // Test with null input
        result = orderService.loadAndValidateOrdersForBulkSubmission(null);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        // Verify no repository calls were made
        verify(orderRepository, never()).findAllById(any());
    }
    
    @Test
    void testLoadAndValidateOrdersForBulkSubmission_NoOrdersFound() {
        // Setup test data - order IDs that don't exist
        List<Integer> orderIds = Arrays.asList(999, 1000);
        
        // Mock repository response - no orders found
        when(orderRepository.findAllById(orderIds)).thenReturn(new ArrayList<>());
        
        // Execute
        List<Order> result = orderService.loadAndValidateOrdersForBulkSubmission(orderIds);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        verify(orderRepository).findAllById(orderIds);
    }
    
    @Test
    void testHasRequiredFieldsForTradeService_ValidOrder() {
        // Create order with all required fields
        Order validOrder = createValidOrderForBulkSubmission(1);
        
        // Use reflection to access private method for testing
        boolean result = invokeHasRequiredFieldsForTradeService(validOrder);
        
        assertTrue(result);
    }
    
    @Test
    void testHasRequiredFieldsForTradeService_MissingFields() {
        // Test null order
        assertFalse(invokeHasRequiredFieldsForTradeService(null));
        
        // Test missing portfolioId
        Order order = createValidOrderForBulkSubmission(1);
        order.setPortfolioId(null);
        assertFalse(invokeHasRequiredFieldsForTradeService(order));
        
        // Test empty portfolioId
        order = createValidOrderForBulkSubmission(1);
        order.setPortfolioId("   ");
        assertFalse(invokeHasRequiredFieldsForTradeService(order));
        
        // Test missing orderType
        order = createValidOrderForBulkSubmission(1);
        order.setOrderType(null);
        assertFalse(invokeHasRequiredFieldsForTradeService(order));
        
        // Test orderType with null abbreviation
        order = createValidOrderForBulkSubmission(1);
        order.getOrderType().setAbbreviation(null);
        assertFalse(invokeHasRequiredFieldsForTradeService(order));
        
        // Test missing securityId
        order = createValidOrderForBulkSubmission(1);
        order.setSecurityId(null);
        assertFalse(invokeHasRequiredFieldsForTradeService(order));
        
        // Test zero quantity
        order = createValidOrderForBulkSubmission(1);
        order.setQuantity(BigDecimal.ZERO);
        assertFalse(invokeHasRequiredFieldsForTradeService(order));
        
        // Test negative quantity
        order = createValidOrderForBulkSubmission(1);
        order.setQuantity(new BigDecimal("-10.00"));
        assertFalse(invokeHasRequiredFieldsForTradeService(order));
        
        // Test zero limitPrice
        order = createValidOrderForBulkSubmission(1);
        order.setLimitPrice(BigDecimal.ZERO);
        assertFalse(invokeHasRequiredFieldsForTradeService(order));
        
        // Test missing orderTimestamp
        order = createValidOrderForBulkSubmission(1);
        order.setOrderTimestamp(null);
        assertFalse(invokeHasRequiredFieldsForTradeService(order));
        
        // Test missing blotter
        order = createValidOrderForBulkSubmission(1);
        order.setBlotter(null);
        assertFalse(invokeHasRequiredFieldsForTradeService(order));
        
        // Test blotter with null ID
        order = createValidOrderForBulkSubmission(1);
        order.getBlotter().setId(null);
        assertFalse(invokeHasRequiredFieldsForTradeService(order));
    }
    
    // Helper method to create a valid order for bulk submission testing
    private Order createValidOrderForBulkSubmission(Integer id) {
        Status newStatus = Status.builder()
                .id(1)
                .abbreviation("NEW")
                .description("New")
                .version(1)
                .build();
                
        OrderType buyOrderType = OrderType.builder()
                .id(2)
                .abbreviation("BUY")
                .description("Buy")
                .version(1)
                .build();
                
        Blotter testBlotter = Blotter.builder()
                .id(3)
                .name("Test Blotter")
                .version(1)
                .build();
        
        return Order.builder()
                .id(id)
                .blotter(testBlotter)
                .status(newStatus)
                .portfolioId("PORTFOLIO_" + String.format("%03d", id))
                .orderType(buyOrderType)
                .securityId("SECURITY_" + String.format("%03d", id))
                .quantity(new BigDecimal("100.00"))
                .limitPrice(new BigDecimal("50.25"))
                .tradeOrderId(null) // Not processed yet
                .orderTimestamp(OffsetDateTime.now())
                .version(1)
                .build();
    }
    
    // Helper method to invoke private hasRequiredFieldsForTradeService method using reflection
    private boolean invokeHasRequiredFieldsForTradeService(Order order) {
        try {
            java.lang.reflect.Method method = OrderService.class.getDeclaredMethod("hasRequiredFieldsForTradeService", Order.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(orderService, order);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke hasRequiredFieldsForTradeService method", e);
        }
    }
}