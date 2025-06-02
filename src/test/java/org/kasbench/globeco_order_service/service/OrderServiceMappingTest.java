package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.dto.OrderWithDetailsDTO;
import org.kasbench.globeco_order_service.entity.*;
import org.kasbench.globeco_order_service.repository.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OrderServiceMappingTest {

    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private StatusRepository statusRepository;
    
    @Mock
    private BlotterRepository blotterRepository;
    
    @Mock
    private OrderTypeRepository orderTypeRepository;
    
    @Mock
    private RestTemplate restTemplate;

    @Test
    void testOrderToDtoMapping() {
        MockitoAnnotations.openMocks(this);
        
        OrderService orderService = new OrderService(
            orderRepository, statusRepository, blotterRepository, orderTypeRepository, restTemplate
        );

        // Create test entities
        Blotter blotter = Blotter.builder()
                .id(1)
                .name("Test Blotter")
                .version(1)
                .build();

        Status status = Status.builder()
                .id(2)
                .abbreviation("NEW")
                .description("New Order")
                .version(1)
                .build();

        OrderType orderType = OrderType.builder()
                .id(3)
                .abbreviation("BUY")
                .description("Buy Order")
                .version(1)
                .build();

        Order order = Order.builder()
                .id(42)
                .blotter(blotter)
                .status(status)
                .portfolioId("PORT12345678901234567890")
                .orderType(orderType)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.12345678"))
                .limitPrice(new BigDecimal("50.25000000"))
                .tradeOrderId(99999)
                .orderTimestamp(OffsetDateTime.parse("2024-06-01T12:00:00Z"))
                .version(1)
                .build();

        // Mock repository to return our test order
        when(orderRepository.findAll()).thenReturn(Arrays.asList(order));

        // Test the mapping
        List<OrderWithDetailsDTO> result = orderService.getAll();

        assertNotNull(result);
        assertEquals(1, result.size());

        OrderWithDetailsDTO dto = result.get(0);
        
        // Verify basic fields
        assertEquals(42, dto.getId());
        assertEquals("PORT12345678901234567890", dto.getPortfolioId());
        assertEquals("SEC12345678901234567890", dto.getSecurityId());
        assertEquals(new BigDecimal("100.12345678"), dto.getQuantity());
        assertEquals(new BigDecimal("50.25000000"), dto.getLimitPrice());
        assertEquals(99999, dto.getTradeOrderId());
        assertEquals(OffsetDateTime.parse("2024-06-01T12:00:00Z"), dto.getOrderTimestamp());
        assertEquals(1, dto.getVersion());

        // Verify nested DTOs are properly populated
        assertNotNull(dto.getBlotter());
        assertEquals(1, dto.getBlotter().getId());
        assertEquals("Test Blotter", dto.getBlotter().getName());
        assertEquals(1, dto.getBlotter().getVersion());

        assertNotNull(dto.getStatus());
        assertEquals(2, dto.getStatus().getId());
        assertEquals("NEW", dto.getStatus().getAbbreviation());
        assertEquals("New Order", dto.getStatus().getDescription());
        assertEquals(1, dto.getStatus().getVersion());

        assertNotNull(dto.getOrderType());
        assertEquals(3, dto.getOrderType().getId());
        assertEquals("BUY", dto.getOrderType().getAbbreviation());
        assertEquals("Buy Order", dto.getOrderType().getDescription());
        assertEquals(1, dto.getOrderType().getVersion());
    }

    @Test
    void testOrderToDtoMappingWithNullRelations() {
        MockitoAnnotations.openMocks(this);
        
        OrderService orderService = new OrderService(
            orderRepository, statusRepository, blotterRepository, orderTypeRepository, restTemplate
        );

        // Create test order with null blotter (which is allowed)
        Status status = Status.builder()
                .id(2)
                .abbreviation("NEW")
                .description("New Order")
                .version(1)
                .build();

        OrderType orderType = OrderType.builder()
                .id(3)
                .abbreviation("BUY")
                .description("Buy Order")
                .version(1)
                .build();

        Order order = Order.builder()
                .id(42)
                .blotter(null)  // null blotter
                .status(status)
                .portfolioId("PORT12345678901234567890")
                .orderType(orderType)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.12345678"))
                .limitPrice(null)  // null limit price
                .tradeOrderId(null)  // null trade order id
                .orderTimestamp(OffsetDateTime.parse("2024-06-01T12:00:00Z"))
                .version(1)
                .build();

        // Mock repository to return our test order
        when(orderRepository.findAll()).thenReturn(Arrays.asList(order));

        // Test the mapping
        List<OrderWithDetailsDTO> result = orderService.getAll();

        assertNotNull(result);
        assertEquals(1, result.size());

        OrderWithDetailsDTO dto = result.get(0);
        
        // Verify basic fields
        assertEquals(42, dto.getId());
        assertEquals("PORT12345678901234567890", dto.getPortfolioId());
        assertEquals("SEC12345678901234567890", dto.getSecurityId());
        assertEquals(new BigDecimal("100.12345678"), dto.getQuantity());
        assertNull(dto.getLimitPrice());
        assertNull(dto.getTradeOrderId());
        assertEquals(OffsetDateTime.parse("2024-06-01T12:00:00Z"), dto.getOrderTimestamp());
        assertEquals(1, dto.getVersion());

        // Verify nullable blotter is handled correctly
        assertNull(dto.getBlotter());

        // Verify required relations are properly populated
        assertNotNull(dto.getStatus());
        assertEquals(2, dto.getStatus().getId());
        assertEquals("NEW", dto.getStatus().getAbbreviation());

        assertNotNull(dto.getOrderType());
        assertEquals(3, dto.getOrderType().getId());
        assertEquals("BUY", dto.getOrderType().getAbbreviation());
    }
} 