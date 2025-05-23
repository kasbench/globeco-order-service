package org.kasbench_globeco_order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.dto.OrderPostDTO;
import org.kasbench.globeco_order_service.dto.OrderWithDetailsDTO;
import org.kasbench.globeco_order_service.dto.OrderDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderPostDTO;
import org.kasbench.globeco_order_service.entity.*;
import org.kasbench.globeco_order_service.repository.*;
import org.kasbench.globeco_order_service.service.OrderService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
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
                .tradeOrderId(12345)
                .orderTimestamp(now)
                .version(1)
                .build();
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body("ok"));
        Status sentStatus = Status.builder().id(2).abbreviation("SENT").description("Sent").version(1).build();
        when(statusRepository.findAll()).thenReturn(Arrays.asList(status, sentStatus));
        boolean result = orderService.submitOrder(1);
        assertTrue(result);
        verify(orderRepository).save(argThat(o -> o.getStatus().getAbbreviation().equals("SENT")));
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
                .tradeOrderId(12345)
                .orderTimestamp(now)
                .version(1)
                .build();
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("fail"));
        when(statusRepository.findAll()).thenReturn(Arrays.asList(status));
        boolean result = orderService.submitOrder(1);
        assertFalse(result);
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
        assertEquals("Default", result.getBlotter().getName());
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
} 