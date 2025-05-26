package org.kasbench.globeco_order_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.dto.*;
import org.kasbench.globeco_order_service.service.OrderService;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
public class OrderControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    private OrderWithDetailsDTO orderWithDetailsDTO;
    private OrderPostDTO orderPostDTO;
    private OrderDTO orderDTO;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        now = OffsetDateTime.now();
        BlotterDTO blotterDTO = BlotterDTO.builder().id(3).name("Default").version(1).build();
        StatusDTO statusDTO = StatusDTO.builder().id(1).abbreviation("NEW").description("New").version(1).build();
        OrderTypeDTO orderTypeDTO = OrderTypeDTO.builder().id(2).abbreviation("BUY").description("Buy").version(1).build();
        orderWithDetailsDTO = OrderWithDetailsDTO.builder()
                .id(10)
                .blotter(blotterDTO)
                .status(statusDTO)
                .portfolioId("PORT12345678901234567890")
                .orderType(orderTypeDTO)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00000000"))
                .limitPrice(new BigDecimal("50.25000000"))
                .orderTimestamp(now)
                .version(1)
                .build();
        orderPostDTO = OrderPostDTO.builder()
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
        orderDTO = OrderDTO.builder()
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
    }

    @Test
    void testGetAllOrders() throws Exception {
        Mockito.when(orderService.getAll()).thenReturn(Arrays.asList(orderWithDetailsDTO));
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void testGetOrderById_found() throws Exception {
        Mockito.when(orderService.getById(10)).thenReturn(Optional.of(orderWithDetailsDTO));
        mockMvc.perform(get("/api/v1/order/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void testGetOrderById_notFound() throws Exception {
        Mockito.when(orderService.getById(10)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/order/10"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateOrder() throws Exception {
        Mockito.when(orderService.create(any(OrderPostDTO.class))).thenReturn(orderWithDetailsDTO);
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderPostDTO)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        OrderWithDetailsDTO result = objectMapper.readValue(response, OrderWithDetailsDTO.class);
        org.junit.jupiter.api.Assertions.assertEquals(10, result.getId());
    }

    @Test
    void testUpdateOrder_found() throws Exception {
        Mockito.when(orderService.update(eq(10), any(OrderDTO.class))).thenReturn(Optional.of(orderWithDetailsDTO));
        String response = mockMvc.perform(put("/api/v1/order/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderDTO)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        OrderWithDetailsDTO result = objectMapper.readValue(response, OrderWithDetailsDTO.class);
        org.junit.jupiter.api.Assertions.assertEquals(10, result.getId());
    }

    @Test
    void testUpdateOrder_notFound() throws Exception {
        Mockito.when(orderService.update(eq(10), any(OrderDTO.class))).thenReturn(Optional.empty());
        mockMvc.perform(put("/api/v1/order/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderDTO)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteOrder_success() throws Exception {
        Mockito.when(orderService.delete(10, 1)).thenReturn(true);
        mockMvc.perform(delete("/api/v1/order/10?version=1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteOrder_conflict() throws Exception {
        Mockito.when(orderService.delete(10, 1)).thenReturn(false);
        mockMvc.perform(delete("/api/v1/order/10?version=1"))
                .andExpect(status().isConflict());
    }

    @Test
    void testSubmitOrder_success() throws Exception {
        OrderDTO submittedOrder = OrderDTO.builder()
            .id(orderDTO.getId())
            .blotterId(orderDTO.getBlotterId())
            .statusId(2)
            .portfolioId(orderDTO.getPortfolioId())
            .orderTypeId(orderDTO.getOrderTypeId())
            .securityId(orderDTO.getSecurityId())
            .quantity(orderDTO.getQuantity())
            .limitPrice(orderDTO.getLimitPrice())
            .tradeOrderId(99999)
            .orderTimestamp(orderDTO.getOrderTimestamp())
            .version(orderDTO.getVersion())
            .build();
        Mockito.when(orderService.submitOrder(10)).thenReturn(submittedOrder);
        mockMvc.perform(post("/api/v1/orders/10/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.tradeOrderId").value(99999))
                .andExpect(jsonPath("$.statusId").value(2));
    }

    @Test
    void testSubmitOrder_failure() throws Exception {
        Mockito.when(orderService.submitOrder(10)).thenReturn(null);
        mockMvc.perform(post("/api/v1/orders/10/submit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("not submitted"));
    }
} 