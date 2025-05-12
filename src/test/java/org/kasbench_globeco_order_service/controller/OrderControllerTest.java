package org.kasbench_globeco_order_service.controller;

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
import static org.hamcrest.Matchers.is;

@WebMvcTest(org.kasbench.globeco_order_service.controller.OrderController.class)
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
    void testCreateOrder() throws Exception {
        Mockito.when(orderService.create(any(OrderPostDTO.class))).thenReturn(orderWithDetailsDTO);
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderPostDTO)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Robustness: Deserialize and assert
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
        // Robustness: Deserialize and assert
        OrderWithDetailsDTO result = objectMapper.readValue(response, OrderWithDetailsDTO.class);
        org.junit.jupiter.api.Assertions.assertEquals(10, result.getId());
    }
} 