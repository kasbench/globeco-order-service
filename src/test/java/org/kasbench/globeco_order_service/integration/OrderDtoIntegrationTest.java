package org.kasbench.globeco_order_service.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.kasbench.globeco_order_service.dto.OrderWithDetailsDTO;
import org.kasbench.globeco_order_service.dto.OrderPostDTO;
import org.kasbench.globeco_order_service.dto.OrderListResponseDTO;
import org.kasbench.globeco_order_service.dto.OrderPostResponseDTO;
import org.kasbench.globeco_order_service.entity.*;
import org.kasbench.globeco_order_service.repository.*;
import org.kasbench.globeco_order_service.service.OrderService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class OrderDtoIntegrationTest {

    @Autowired
    private OrderService orderService;
    
    @Autowired
    private BlotterRepository blotterRepository;
    
    @Autowired
    private StatusRepository statusRepository;
    
    @Autowired
    private OrderTypeRepository orderTypeRepository;
    
    @Autowired
    private OrderRepository orderRepository;

    // =============== BATCH PROCESSING INTEGRATION TESTS ===============

    @Test
    public void testBatchOrderProcessing_AllSuccess() {
        setupTestData();
        
        // Create a batch of valid orders
        List<OrderPostDTO> orders = createValidOrderBatch(3);
        
        // Process batch
        OrderListResponseDTO result = orderService.processBatchOrders(orders);
        
        // Verify batch results
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(3, result.getTotalReceived());
        assertEquals(3, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertEquals(3, result.getOrders().size());
        assertTrue(result.getMessage().contains("All 3 orders processed successfully"));
        
        // Verify each individual order result
        for (int i = 0; i < 3; i++) {
            OrderPostResponseDTO orderResponse = result.getOrders().get(i);
            assertEquals("SUCCESS", orderResponse.getStatus());
            assertEquals(i, orderResponse.getRequestIndex());
            assertNotNull(orderResponse.getOrder());
            assertNotNull(orderResponse.getOrderId());
            verifyOrderDtoIsProperlyPopulated(orderResponse.getOrder());
        }
        
        // Verify orders were actually saved to database
        List<OrderWithDetailsDTO> allOrders = orderService.getAll();
        assertTrue(allOrders.size() >= 3, "Should have at least 3 orders in database");
        
        System.out.println("✅ Batch processing (all success) integration test passed!");
    }
    
    @Test
    public void testBatchOrderProcessing_PartialSuccess() {
        setupTestData();
        
        // Create batch with mix of valid and invalid orders
        List<OrderPostDTO> orders = new ArrayList<>();
        orders.add(createValidOrderPostDTO()); // Valid
        orders.add(createValidOrderPostDTO()); // Valid
        orders.add(createInvalidOrderPostDTO()); // Invalid - missing required field
        
        // Process batch
        OrderListResponseDTO result = orderService.processBatchOrders(orders);
        
        // Verify batch results
        assertNotNull(result);
        assertEquals("PARTIAL", result.getStatus());
        assertEquals(3, result.getTotalReceived());
        assertEquals(2, result.getSuccessful());
        assertEquals(1, result.getFailed());
        assertEquals(3, result.getOrders().size());
        assertTrue(result.getMessage().contains("2 of 3 orders processed successfully"));
        
        // Verify individual results
        assertEquals("SUCCESS", result.getOrders().get(0).getStatus());
        assertEquals("SUCCESS", result.getOrders().get(1).getStatus());
        assertEquals("FAILURE", result.getOrders().get(2).getStatus());
        assertTrue(result.getOrders().get(2).getMessage().contains("Portfolio ID is required"));
        
        // Verify only successful orders were saved
        long orderCountAfter = orderRepository.count();
        assertTrue(orderCountAfter >= 2, "Should have saved at least 2 successful orders");
        
        System.out.println("✅ Batch processing (partial success) integration test passed!");
    }
    
    @Test
    public void testBatchOrderProcessing_AllFailure() {
        setupTestData();
        
        // Create batch with all invalid orders
        List<OrderPostDTO> orders = new ArrayList<>();
        orders.add(createOrderWithInvalidBlotter()); // Invalid blotter ID
        orders.add(createOrderWithInvalidStatus()); // Invalid status ID
        orders.add(createOrderWithNegativeQuantity()); // Invalid quantity
        
        // Process batch
        OrderListResponseDTO result = orderService.processBatchOrders(orders);
        
        // Verify batch results
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals(3, result.getTotalReceived());
        assertEquals(0, result.getSuccessful());
        assertEquals(3, result.getFailed());
        assertEquals(3, result.getOrders().size());
        
        // Verify all orders failed with appropriate error messages
        assertEquals("FAILURE", result.getOrders().get(0).getStatus());
        assertEquals("FAILURE", result.getOrders().get(1).getStatus());
        assertEquals("FAILURE", result.getOrders().get(2).getStatus());
        
        System.out.println("✅ Batch processing (all failure) integration test passed!");
    }
    
    @Test
    public void testBatchOrderProcessing_EmptyBatch() {
        List<OrderPostDTO> orders = new ArrayList<>();
        
        // Process empty batch
        OrderListResponseDTO result = orderService.processBatchOrders(orders);
        
        // Verify results
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals(0, result.getTotalReceived());
        assertEquals(0, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertTrue(result.getMessage().contains("No orders provided for processing"));
        
        System.out.println("✅ Empty batch processing integration test passed!");
    }
    
    @Test
    public void testBatchOrderProcessing_LargeBatch() {
        setupTestData();
        
        // Create a large batch (but within limits)
        List<OrderPostDTO> orders = createValidOrderBatch(50);
        
        // Process batch
        OrderListResponseDTO result = orderService.processBatchOrders(orders);
        
        // Verify batch results
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(50, result.getTotalReceived());
        assertEquals(50, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertEquals(50, result.getOrders().size());
        
        // Verify all orders have proper DTOs and were saved
        for (OrderPostResponseDTO orderResponse : result.getOrders()) {
            assertEquals("SUCCESS", orderResponse.getStatus());
            assertNotNull(orderResponse.getOrder());
            verifyOrderDtoIsProperlyPopulated(orderResponse.getOrder());
        }
        
        System.out.println("✅ Large batch processing integration test passed!");
    }
    
    @Test
    public void testBatchOrderProcessing_DatabaseTransaction() {
        setupTestData();
        long initialOrderCount = orderRepository.count();
        
        // Create batch with valid and invalid orders to test transaction behavior
        List<OrderPostDTO> orders = new ArrayList<>();
        orders.add(createValidOrderPostDTO());
        orders.add(createValidOrderPostDTO());
        orders.add(createInvalidOrderPostDTO()); // This should fail but not rollback successful ones
        
        // Process batch
        OrderListResponseDTO result = orderService.processBatchOrders(orders);
        
        // Verify partial success (non-atomic behavior)
        assertEquals("PARTIAL", result.getStatus());
        assertEquals(2, result.getSuccessful());
        assertEquals(1, result.getFailed());
        
        // Verify successful orders were committed to database
        long finalOrderCount = orderRepository.count();
        assertEquals(initialOrderCount + 2, finalOrderCount, 
                "Successful orders should be persisted even when some orders fail");
        
        System.out.println("✅ Batch transaction behavior integration test passed!");
    }

    // =============== EXISTING TESTS (updated for compatibility) ===============

    @Test
    public void testOrderServiceReturnsProperDtoFormat() {
        // Set up test data
        setupTestData();
        
        // Test getting all orders - should return OrderWithDetailsDTO with proper nesting
        List<OrderWithDetailsDTO> orders = orderService.getAll();
        assertNotNull(orders);
        
        if (!orders.isEmpty()) {
            OrderWithDetailsDTO order = orders.get(0);
            verifyOrderDtoIsProperlyPopulated(order);
        } else {
            // If no orders exist, create one and verify
            testCreateOrderReturnsProperDtoFormat();
        }
    }
    
    @Test
    public void testCreateOrderReturnsProperDtoFormat() {
        // Set up test data
        setupTestData();
        
        // Create a test order via service
        Blotter blotter = blotterRepository.findAll().get(0);
        Status status = statusRepository.findAll().get(0);
        OrderType orderType = orderTypeRepository.findAll().get(0);
        
        OrderPostDTO postDTO = OrderPostDTO.builder()
            .blotterId(blotter.getId())
            .statusId(status.getId())
            .portfolioId("TEST_PORTFOLIO_123")
            .orderTypeId(orderType.getId())
            .securityId("TEST_SECURITY_456")
            .quantity(new BigDecimal("100.00"))
            .limitPrice(new BigDecimal("50.25"))
            .orderTimestamp(OffsetDateTime.now())
            .version(1)
            .build();
            
        OrderWithDetailsDTO createdOrder = orderService.create(postDTO);
        assertNotNull(createdOrder);
        
        // Verify all fields are properly populated (this would fail before our fix)
        verifyOrderDtoIsProperlyPopulated(createdOrder);
        
        // Verify specific values from our POST
        assertEquals("TEST_PORTFOLIO_123", createdOrder.getPortfolioId());
        assertEquals("TEST_SECURITY_456", createdOrder.getSecurityId());
        assertEquals(new BigDecimal("100.00"), createdOrder.getQuantity());
        assertEquals(new BigDecimal("50.25"), createdOrder.getLimitPrice());
        
        // Verify nested DTOs match our input data
        assertEquals(blotter.getId(), createdOrder.getBlotter().getId());
        assertEquals(blotter.getName(), createdOrder.getBlotter().getName());
        assertEquals(status.getId(), createdOrder.getStatus().getId());
        assertEquals(status.getAbbreviation(), createdOrder.getStatus().getAbbreviation());
        assertEquals(orderType.getId(), createdOrder.getOrderType().getId());
        assertEquals(orderType.getAbbreviation(), createdOrder.getOrderType().getAbbreviation());
        
        System.out.println("✅ Service layer creates properly populated OrderWithDetailsDTO - fix verified!");
    }

    // =============== HELPER METHODS ===============
    
    private List<OrderPostDTO> createValidOrderBatch(int size) {
        List<OrderPostDTO> orders = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            orders.add(createValidOrderPostDTO());
        }
        return orders;
    }
    
    private OrderPostDTO createValidOrderPostDTO() {
        Blotter blotter = blotterRepository.findAll().get(0);
        Status status = statusRepository.findAll().get(0);
        OrderType orderType = orderTypeRepository.findAll().get(0);
        
        return OrderPostDTO.builder()
            .blotterId(blotter.getId())
            .statusId(status.getId())
            .portfolioId("BATCH_PORTFOLIO_" + System.currentTimeMillis())
            .orderTypeId(orderType.getId())
            .securityId("BATCH_SECURITY_" + System.currentTimeMillis())
            .quantity(new BigDecimal("100.00"))
            .limitPrice(new BigDecimal("50.25"))
            .orderTimestamp(OffsetDateTime.now())
            .version(1)
            .build();
    }
    
    private OrderPostDTO createInvalidOrderPostDTO() {
        Blotter blotter = blotterRepository.findAll().get(0);
        Status status = statusRepository.findAll().get(0);
        OrderType orderType = orderTypeRepository.findAll().get(0);
        
        return OrderPostDTO.builder()
            .blotterId(blotter.getId())
            .statusId(status.getId())
            // Missing portfolioId - validation should fail
            .orderTypeId(orderType.getId())
            .securityId("INVALID_SECURITY")
            .quantity(new BigDecimal("100.00"))
            .limitPrice(new BigDecimal("50.25"))
            .build();
    }
    
    private OrderPostDTO createOrderWithInvalidBlotter() {
        Status status = statusRepository.findAll().get(0);
        OrderType orderType = orderTypeRepository.findAll().get(0);
        
        return OrderPostDTO.builder()
            .blotterId(99999) // Non-existent blotter ID
            .statusId(status.getId())
            .portfolioId("VALID_PORTFOLIO")
            .orderTypeId(orderType.getId())
            .securityId("VALID_SECURITY")
            .quantity(new BigDecimal("100.00"))
            .limitPrice(new BigDecimal("50.25"))
            .build();
    }
    
    private OrderPostDTO createOrderWithInvalidStatus() {
        Blotter blotter = blotterRepository.findAll().get(0);
        OrderType orderType = orderTypeRepository.findAll().get(0);
        
        return OrderPostDTO.builder()
            .blotterId(blotter.getId())
            .statusId(88888) // Non-existent status ID
            .portfolioId("VALID_PORTFOLIO")
            .orderTypeId(orderType.getId())
            .securityId("VALID_SECURITY")
            .quantity(new BigDecimal("100.00"))
            .limitPrice(new BigDecimal("50.25"))
            .build();
    }
    
    private OrderPostDTO createOrderWithNegativeQuantity() {
        Blotter blotter = blotterRepository.findAll().get(0);
        Status status = statusRepository.findAll().get(0);
        OrderType orderType = orderTypeRepository.findAll().get(0);
        
        return OrderPostDTO.builder()
            .blotterId(blotter.getId())
            .statusId(status.getId())
            .portfolioId("VALID_PORTFOLIO")
            .orderTypeId(orderType.getId())
            .securityId("VALID_SECURITY")
            .quantity(new BigDecimal("-100.00")) // Invalid negative quantity
            .limitPrice(new BigDecimal("50.25"))
            .build();
    }
    
    private void verifyOrderDtoIsProperlyPopulated(OrderWithDetailsDTO order) {
        // Verify that basic fields are populated (not null like before the fix)
        assertNotNull(order.getId(), "Order ID should not be null");
        assertNotNull(order.getVersion(), "Order version should not be null");
        
        // Verify core fields that should now be populated
        assertNotNull(order.getPortfolioId(), "Portfolio ID should not be null");
        assertFalse(order.getPortfolioId().isEmpty(), "Portfolio ID should not be empty");
        
        // Verify nested DTOs are properly populated (this was the main issue)
        assertNotNull(order.getStatus(), "Status DTO should not be null");
        assertNotNull(order.getStatus().getId(), "Status ID should not be null");
        assertNotNull(order.getStatus().getAbbreviation(), "Status abbreviation should not be null");
        assertNotNull(order.getStatus().getDescription(), "Status description should not be null");
        assertNotNull(order.getStatus().getVersion(), "Status version should not be null");
        
        assertNotNull(order.getOrderType(), "OrderType DTO should not be null");
        assertNotNull(order.getOrderType().getId(), "OrderType ID should not be null");
        assertNotNull(order.getOrderType().getAbbreviation(), "OrderType abbreviation should not be null");
        assertNotNull(order.getOrderType().getDescription(), "OrderType description should not be null");
        assertNotNull(order.getOrderType().getVersion(), "OrderType version should not be null");
        
        // Blotter is optional, but if present, should be properly populated
        if (order.getBlotter() != null) {
            assertNotNull(order.getBlotter().getId(), "Blotter ID should not be null");
            assertNotNull(order.getBlotter().getName(), "Blotter name should not be null");
            assertNotNull(order.getBlotter().getVersion(), "Blotter version should not be null");
        }
        
        System.out.println("✅ Order DTO properly populated with nested DTOs - fix verified!");
    }
    
    private void setupTestData() {
        // Create test blotter
        if (blotterRepository.count() == 0) {
            Blotter blotter = Blotter.builder()
                .name("Test Blotter")
                .version(1)
                .build();
            blotterRepository.save(blotter);
        }
        
        // Create test status
        if (statusRepository.count() == 0) {
            Status status = Status.builder()
                .abbreviation("NEW")
                .description("New Order")
                .version(1)
                .build();
            statusRepository.save(status);
        }
        
        // Create test order type
        if (orderTypeRepository.count() == 0) {
            OrderType orderType = OrderType.builder()
                .abbreviation("BUY")
                .description("Buy Order")
                .version(1)
                .build();
            orderTypeRepository.save(orderType);
        }
    }
} 