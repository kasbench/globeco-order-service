package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.dto.OrderPostDTO;
import org.kasbench.globeco_order_service.dto.OrderWithDetailsDTO;
import org.kasbench.globeco_order_service.dto.OrderDTO;
import org.kasbench.globeco_order_service.entity.*;
import org.kasbench.globeco_order_service.repository.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {
    @Mock private OrderRepository orderRepository;
    @Mock private BlotterRepository blotterRepository;
    @Mock private StatusRepository statusRepository;
    @Mock private OrderTypeRepository orderTypeRepository;
    @Mock private RestTemplate restTemplate;
    @InjectMocks private OrderService orderService;

    private Blotter blotter;
    private Status status;
    private OrderType orderType;
    private OffsetDateTime now;
    private Order order;

    @BeforeEach
    void setUp() {
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
                .tradeOrderId(12345)
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
    void testSubmitOrder_success() {
        Order order = Order.builder()
                .id(1)
                .blotter(blotter)
                .status(status)
                .portfolioId("PORT12345678901234567890")
                .orderType(orderType)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00000000"))
                .limitPrice(new BigDecimal("50.25000000"))
                .tradeOrderId(null)
                .orderTimestamp(now)
                .version(1)
                .build();
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        String tradeServiceResponse = "{\"id\":99999}";
        System.out.println("DEBUG: Mocking trade service response: " + tradeServiceResponse);
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(tradeServiceResponse));
        Status sentStatus = Status.builder().id(2).abbreviation("SENT").description("Sent").version(1).build();
        when(statusRepository.findAll()).thenReturn(Arrays.asList(status, sentStatus));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        boolean result = orderService.submitOrder(1);
        System.out.println("DEBUG: submitOrder result: " + result);
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        for (Order o : orderCaptor.getAllValues()) {
            System.out.println("DEBUG: captured order tradeOrderId: " + o.getTradeOrderId() + ", status: " + (o.getStatus() != null ? o.getStatus().getAbbreviation() : null));
        }
        Order savedOrder = orderCaptor.getValue();
        System.out.println("DEBUG: savedOrder.tradeOrderId: " + savedOrder.getTradeOrderId());
        assertEquals(99999, savedOrder.getTradeOrderId());
        assertEquals("SENT", savedOrder.getStatus().getAbbreviation());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void testSubmitOrder_notNewStatus() {
        Status notNew = Status.builder().id(3).abbreviation("SENT").description("Sent").version(1).build();
        Order order = Order.builder().id(1).status(notNew).build();
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(statusRepository.findAll()).thenReturn(Arrays.asList(notNew));
        boolean result = orderService.submitOrder(1);
        assertFalse(result);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void testSubmitOrder_tradeServiceFails() {
        Order order = Order.builder()
                .id(1)
                .blotter(blotter)
                .status(status)
                .portfolioId("PORT12345678901234567890")
                .orderType(orderType)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00000000"))
                .limitPrice(new BigDecimal("50.25000000"))
                .tradeOrderId(null)
                .orderTimestamp(now)
                .version(1)
                .build();
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("fail"));
        // Mock statusRepository.findAll() to return a SENT status to prevent NoSuchElementException
        Status sentStatus = Status.builder().id(2).abbreviation("SENT").description("Sent").version(1).build();
        when(statusRepository.findAll()).thenReturn(Arrays.asList(status, sentStatus));
        // Mock all required repository calls for submitOrder
        when(blotterRepository.findById(anyInt())).thenReturn(Optional.of(blotter));
        when(orderTypeRepository.findById(anyInt())).thenReturn(Optional.of(orderType));
        boolean result = orderService.submitOrder(1);
        assertFalse(result);
        assertNull(order.getTradeOrderId());
        verify(orderRepository, never()).save(any());
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
                .tradeOrderId(12345)
                .orderTimestamp(now)
                .version(1)
                .build();
        when(blotterRepository.findById(3)).thenReturn(Optional.of(blotter));
        when(statusRepository.findById(1)).thenReturn(Optional.of(status));
        when(orderTypeRepository.findById(2)).thenReturn(Optional.of(orderType));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        OrderWithDetailsDTO result = orderService.create(dto);
        assertEquals(order.getId(), result.getId());
        assertEquals(12345, result.getTradeOrderId());
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
                .tradeOrderId(12345)
                .orderTimestamp(now)
                .version(1)
                .build();
        when(orderRepository.findById(10)).thenReturn(Optional.of(order));
        when(blotterRepository.findById(3)).thenReturn(Optional.of(blotter));
        when(statusRepository.findById(1)).thenReturn(Optional.of(status));
        when(orderTypeRepository.findById(2)).thenReturn(Optional.of(orderType));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        Optional<OrderWithDetailsDTO> result = orderService.update(10, dto);
        assertTrue(result.isPresent());
        assertEquals(1, result.get().getVersion());
        assertEquals(12345, result.get().getTradeOrderId());
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
        when(orderRepository.existsById(10)).thenReturn(true);
        doNothing().when(orderRepository).deleteById(10);
        boolean deleted = orderService.delete(10, 1);
        assertTrue(deleted);
        verify(orderRepository).deleteById(10);
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void testDelete_versionMismatch() {
        Order order2 = Order.builder().id(10).version(2).build();
        when(orderRepository.findById(10)).thenReturn(Optional.of(order2));
        boolean result = orderService.delete(10, 1);
        assertFalse(result);
    }

    @Test
    void testDelete_notFound() {
        when(orderRepository.existsById(10)).thenReturn(false);
        boolean result = orderService.delete(10, 1);
        assertFalse(result);
    }
} 