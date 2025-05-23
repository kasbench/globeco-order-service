package org.kasbench_globeco_order_service.controller;

import org.kasbench.globeco_order_service.service.OrderService;
import org.kasbench.globeco_order_service.GlobecoOrderServiceApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kasbench.globeco_order_service.dto.OrderWithDetailsDTO;
import org.kasbench.globeco_order_service.dto.OrderDTO;
import org.kasbench.globeco_order_service.dto.OrderPostDTO;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.BeforeEach;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@SpringBootTest(classes = GlobecoOrderServiceApplication.class)
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    private ObjectMapper objectMapper;
    private OrderWithDetailsDTO orderWithDetailsDTO;
    private OrderDTO orderDTO;
    private OrderPostDTO orderPostDTO;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        var blotterDTO = org.kasbench.globeco_order_service.dto.BlotterDTO.builder().id(1).name("Test Blotter").version(1).build();
        var statusDTO = org.kasbench.globeco_order_service.dto.StatusDTO.builder().id(1).abbreviation("NEW").description("New").version(1).build();
        var orderTypeDTO = org.kasbench.globeco_order_service.dto.OrderTypeDTO.builder().id(1).abbreviation("BUY").description("Buy").version(1).build();
        orderWithDetailsDTO = OrderWithDetailsDTO.builder()
                .id(10)
                .blotter(blotterDTO)
                .status(statusDTO)
                .portfolioId("PORT12345678901234567890")
                .orderType(orderTypeDTO)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00000000"))
                .limitPrice(new BigDecimal("50.25000000"))
                .tradeOrderId(12345)
                .orderTimestamp(java.time.OffsetDateTime.now())
                .version(1)
                .build();
        orderDTO = OrderDTO.builder()
                .id(10)
                .blotterId(1)
                .statusId(1)
                .portfolioId("PORT12345678901234567890")
                .orderTypeId(1)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00000000"))
                .limitPrice(new BigDecimal("50.25000000"))
                .tradeOrderId(12345)
                .orderTimestamp(java.time.OffsetDateTime.now())
                .version(1)
                .build();
        orderPostDTO = OrderPostDTO.builder()
                .blotterId(1)
                .statusId(1)
                .portfolioId("PORT12345678901234567890")
                .orderTypeId(1)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00000000"))
                .limitPrice(new BigDecimal("50.25000000"))
                .tradeOrderId(12345)
                .orderTimestamp(java.time.OffsetDateTime.now())
                .version(1)
                .build();
    }

    @Test
    void testSubmitOrder_success() throws Exception {
        Mockito.when(orderService.submitOrder(10)).thenReturn(true);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/orders/10/submit"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("submitted"));
    }

    @Test
    void testSubmitOrder_failure() throws Exception {
        Mockito.when(orderService.submitOrder(10)).thenReturn(false);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/orders/10/submit"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("not submitted"));
    }

    @Test
    void testGetAllOrders() throws Exception {
        Mockito.when(orderService.getAll()).thenReturn(Arrays.asList(orderWithDetailsDTO));
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].tradeOrderId").value(12345));
    }

    @Test
    void testGetOrderById_found() throws Exception {
        Mockito.when(orderService.getById(10)).thenReturn(Optional.of(orderWithDetailsDTO));
        mockMvc.perform(get("/api/v1/order/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.tradeOrderId").value(12345));
    }

    @Test
    void testCreateOrder() throws Exception {
        Mockito.when(orderService.create(any(OrderPostDTO.class))).thenReturn(orderWithDetailsDTO);
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderPostDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeOrderId").value(12345))
                .andReturn().getResponse().getContentAsString();
        OrderWithDetailsDTO result = objectMapper.readValue(response, OrderWithDetailsDTO.class);
        org.junit.jupiter.api.Assertions.assertEquals(10, result.getId());
        org.junit.jupiter.api.Assertions.assertEquals(12345, result.getTradeOrderId());
    }

    @Test
    void testUpdateOrder_found() throws Exception {
        Mockito.when(orderService.update(eq(10), any(OrderDTO.class))).thenReturn(Optional.of(orderWithDetailsDTO));
        String response = mockMvc.perform(put("/api/v1/order/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeOrderId").value(12345))
                .andReturn().getResponse().getContentAsString();
        OrderWithDetailsDTO result = objectMapper.readValue(response, OrderWithDetailsDTO.class);
        org.junit.jupiter.api.Assertions.assertEquals(10, result.getId());
        org.junit.jupiter.api.Assertions.assertEquals(12345, result.getTradeOrderId());
    }
} 