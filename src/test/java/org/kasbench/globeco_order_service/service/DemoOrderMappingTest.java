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

public class DemoOrderMappingTest {

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
    void demonstrateFixedOrderToDtoMapping() {
        MockitoAnnotations.openMocks(this);
        
        OrderService orderService = new OrderService(
            orderRepository, statusRepository, blotterRepository, orderTypeRepository, restTemplate
        );

        // Create test entities similar to what would be in the database
        Blotter blotter = Blotter.builder()
                .id(1)
                .name("Equity Blotter")
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
                .id(1)
                .blotter(blotter)
                .status(status)
                .portfolioId("PORT12345678901234567890")
                .orderType(orderType)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.12345678"))
                .limitPrice(new BigDecimal("50.25000000"))
                .tradeOrderId(null)
                .orderTimestamp(OffsetDateTime.parse("2024-06-01T12:00:00Z"))
                .version(1)
                .build();

        // Mock repository to return our test order
        when(orderRepository.findAll()).thenReturn(Arrays.asList(order));

        // Test the mapping - this will now return properly populated DTOs
        List<OrderWithDetailsDTO> result = orderService.getAll();

        assertNotNull(result);
        assertEquals(1, result.size());

        OrderWithDetailsDTO dto = result.get(0);

        System.out.println("\n=== FIXED ORDER DTO OUTPUT ===");
        System.out.println("ID: " + dto.getId());
        System.out.println("Portfolio ID: " + dto.getPortfolioId());
        System.out.println("Security ID: " + dto.getSecurityId());
        System.out.println("Quantity: " + dto.getQuantity());
        System.out.println("Limit Price: " + dto.getLimitPrice());
        System.out.println("Trade Order ID: " + dto.getTradeOrderId());
        System.out.println("Order Timestamp: " + dto.getOrderTimestamp());
        System.out.println("Version: " + dto.getVersion());
        
        System.out.println("\nBlotter DTO:");
        System.out.println("  ID: " + dto.getBlotter().getId());
        System.out.println("  Name: " + dto.getBlotter().getName());
        System.out.println("  Version: " + dto.getBlotter().getVersion());
        
        System.out.println("\nStatus DTO:");
        System.out.println("  ID: " + dto.getStatus().getId());
        System.out.println("  Abbreviation: " + dto.getStatus().getAbbreviation());
        System.out.println("  Description: " + dto.getStatus().getDescription());
        System.out.println("  Version: " + dto.getStatus().getVersion());
        
        System.out.println("\nOrder Type DTO:");
        System.out.println("  ID: " + dto.getOrderType().getId());
        System.out.println("  Abbreviation: " + dto.getOrderType().getAbbreviation());
        System.out.println("  Description: " + dto.getOrderType().getDescription());
        System.out.println("  Version: " + dto.getOrderType().getVersion());
        
        System.out.println("\n=== COMPARISON TO ORIGINAL BROKEN OUTPUT ===");
        System.out.println("Before the fix, the API returned:");
        System.out.println("{");
        System.out.println("  \"id\": 1,");
        System.out.println("  \"blotter\": null,");
        System.out.println("  \"status\": null,");
        System.out.println("  \"portfolioId\": null,");
        System.out.println("  \"orderType\": null,");
        System.out.println("  \"securityId\": null,");
        System.out.println("  \"quantity\": null,");
        System.out.println("  \"limitPrice\": null,");
        System.out.println("  \"tradeOrderId\": null,");
        System.out.println("  \"orderTimestamp\": null,");
        System.out.println("  \"version\": 1");
        System.out.println("}");
        
        System.out.println("\nAfter the fix, it now returns properly populated OrderWithDetailsDTO:");
        System.out.println("{");
        System.out.println("  \"id\": " + dto.getId() + ",");
        System.out.println("  \"blotter\": {");
        System.out.println("    \"id\": " + dto.getBlotter().getId() + ",");
        System.out.println("    \"name\": \"" + dto.getBlotter().getName() + "\",");
        System.out.println("    \"version\": " + dto.getBlotter().getVersion());
        System.out.println("  },");
        System.out.println("  \"status\": {");
        System.out.println("    \"id\": " + dto.getStatus().getId() + ",");
        System.out.println("    \"abbreviation\": \"" + dto.getStatus().getAbbreviation() + "\",");
        System.out.println("    \"description\": \"" + dto.getStatus().getDescription() + "\",");
        System.out.println("    \"version\": " + dto.getStatus().getVersion());
        System.out.println("  },");
        System.out.println("  \"portfolioId\": \"" + dto.getPortfolioId() + "\",");
        System.out.println("  \"orderType\": {");
        System.out.println("    \"id\": " + dto.getOrderType().getId() + ",");
        System.out.println("    \"abbreviation\": \"" + dto.getOrderType().getAbbreviation() + "\",");
        System.out.println("    \"description\": \"" + dto.getOrderType().getDescription() + "\",");
        System.out.println("    \"version\": " + dto.getOrderType().getVersion());
        System.out.println("  },");
        System.out.println("  \"securityId\": \"" + dto.getSecurityId() + "\",");
        System.out.println("  \"quantity\": " + dto.getQuantity() + ",");
        System.out.println("  \"limitPrice\": " + dto.getLimitPrice() + ",");
        System.out.println("  \"tradeOrderId\": " + dto.getTradeOrderId() + ",");
        System.out.println("  \"orderTimestamp\": \"" + dto.getOrderTimestamp() + "\",");
        System.out.println("  \"version\": " + dto.getVersion());
        System.out.println("}");

        // Verify the fix works correctly - all these assertions should pass
        assertEquals(1, dto.getId());
        assertEquals("PORT12345678901234567890", dto.getPortfolioId());
        assertEquals("SEC12345678901234567890", dto.getSecurityId());
        assertEquals(new BigDecimal("100.12345678"), dto.getQuantity());
        assertEquals(new BigDecimal("50.25000000"), dto.getLimitPrice());
        assertNull(dto.getTradeOrderId());
        assertEquals(OffsetDateTime.parse("2024-06-01T12:00:00Z"), dto.getOrderTimestamp());
        assertEquals(1, dto.getVersion());

        assertNotNull(dto.getBlotter());
        assertEquals(1, dto.getBlotter().getId());
        assertEquals("Equity Blotter", dto.getBlotter().getName());
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
} 