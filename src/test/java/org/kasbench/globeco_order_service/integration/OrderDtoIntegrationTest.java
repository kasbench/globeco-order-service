package org.kasbench.globeco_order_service.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.kasbench.globeco_order_service.dto.OrderWithDetailsDTO;
import org.kasbench.globeco_order_service.dto.OrderPostDTO;
import org.kasbench.globeco_order_service.entity.*;
import org.kasbench.globeco_order_service.repository.*;
import org.kasbench.globeco_order_service.service.OrderService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

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