package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.dto.*;
import org.kasbench.globeco_order_service.entity.*;
import org.kasbench.globeco_order_service.repository.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class OrderServiceTest {
    @Mock private OrderRepository orderRepository;
    @Mock private BlotterRepository blotterRepository;
    @Mock private StatusRepository statusRepository;
    @Mock private OrderTypeRepository orderTypeRepository;

    @InjectMocks private OrderService orderService;

    private Status status;
    private OrderType orderType;
    private Blotter blotter;
    private Order order;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        status = Status.builder().id(1).abbreviation("NEW").description("New").version(1).build();
        orderType = OrderType.builder().id(2).abbreviation("BUY").description("Buy").version(1).build();
        blotter = Blotter.builder().id(3).name("Default").version(1).build();
        now = OffsetDateTime.now();
        order = Order.builder()
                .id(10)
                .blotter(blotter)
                .status(status)
                .portfolioId("PORT12345678901234567890")
                .orderType(orderType)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00000000"))
                .limitPrice(new BigDecimal("50.25000000"))
                .orderTimestamp(now)
                .version(1)
                .build();
    }

    @Test
    void testGetAll() {
        when(orderRepository.findAll()).thenReturn(Arrays.asList(order));
        List<OrderWithDetailsDTO> result = orderService.getAll();
        assertEquals(1, result.size());
        assertEquals(order.getId(), result.get(0).getId());
    }

    @Test
    void testGetById_found() {
        when(orderRepository.findById(10)).thenReturn(Optional.of(order));
        Optional<OrderWithDetailsDTO> result = orderService.getById(10);
        assertTrue(result.isPresent());
        assertEquals(order.getId(), result.get().getId());
    }

    @Test
    void testGetById_notFound() {
        when(orderRepository.findById(10)).thenReturn(Optional.empty());
        Optional<OrderWithDetailsDTO> result = orderService.getById(10);
        assertFalse(result.isPresent());
    }

    @Test
    void testCreate() {
        OrderPostDTO dto = OrderPostDTO.builder()
                .blotterId(3)
                .statusId(1)
                .portfolioId("PORT12345678901234567890")
                .orderTypeId(2)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00000000"))
                .limitPrice(new BigDecimal("50.25000000"))
                .orderTimestamp(now)
                .version(1)
                .build();
        when(blotterRepository.findById(3)).thenReturn(Optional.of(blotter));
        when(statusRepository.findById(1)).thenReturn(Optional.of(status));
        when(orderTypeRepository.findById(2)).thenReturn(Optional.of(orderType));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        OrderWithDetailsDTO result = orderService.create(dto);
        assertEquals(order.getId(), result.getId());
        assertEquals("Default", result.getBlotter().getName());
    }

    @Test
    void testUpdate_found() {
        OrderDTO dto = OrderDTO.builder()
                .id(10)
                .blotterId(3)
                .statusId(1)
                .portfolioId("PORT12345678901234567890")
                .orderTypeId(2)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00000000"))
                .limitPrice(new BigDecimal("50.25000000"))
                .orderTimestamp(now)
                .version(2)
                .build();
        when(orderRepository.findById(10)).thenReturn(Optional.of(order));
        when(blotterRepository.findById(3)).thenReturn(Optional.of(blotter));
        when(statusRepository.findById(1)).thenReturn(Optional.of(status));
        when(orderTypeRepository.findById(2)).thenReturn(Optional.of(orderType));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        Optional<OrderWithDetailsDTO> result = orderService.update(10, dto);
        assertTrue(result.isPresent());
        assertEquals(2, result.get().getVersion());
    }

    @Test
    void testUpdate_notFound() {
        OrderDTO dto = OrderDTO.builder().id(10).build();
        when(orderRepository.findById(10)).thenReturn(Optional.empty());
        Optional<OrderWithDetailsDTO> result = orderService.update(10, dto);
        assertFalse(result.isPresent());
    }

    @Test
    void testDelete_success() {
        when(orderRepository.findById(10)).thenReturn(Optional.of(order));
        doNothing().when(orderRepository).deleteById(10);
        boolean result = orderService.delete(10, 1);
        assertTrue(result);
    }

    @Test
    void testDelete_versionMismatch() {
        Order order2 = Order.builder().id(10).version(2).build();
        when(orderRepository.findById(10)).thenReturn(Optional.of(order2));
        boolean result = orderService.delete(10, 1);
        assertFalse(result);
    }

    @Test
    void testDelete_notFound() {
        when(orderRepository.findById(10)).thenReturn(Optional.empty());
        boolean result = orderService.delete(10, 1);
        assertFalse(result);
    }
} 