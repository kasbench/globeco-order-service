package org.kasbench.globeco_order_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.dto.OrderTypeDTO;
import org.kasbench.globeco_order_service.dto.OrderTypePostDTO;
import org.kasbench.globeco_order_service.service.OrderTypeService;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderTypeController.class)
public class OrderTypeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderTypeService orderTypeService;

    @Autowired
    private ObjectMapper objectMapper;

    private OrderTypeDTO orderTypeDTO;
    private OrderTypePostDTO orderTypePostDTO;

    @BeforeEach
    void setUp() {
        orderTypeDTO = OrderTypeDTO.builder().id(1).abbreviation("BUY").description("Buy").version(1).build();
        orderTypePostDTO = OrderTypePostDTO.builder().abbreviation("BUY").description("Buy").version(1).build();
    }

    @Test
    void testGetAllOrderTypes() throws Exception {
        Mockito.when(orderTypeService.getAll()).thenReturn(Arrays.asList(orderTypeDTO));
        mockMvc.perform(get("/api/v1/orderTypes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].abbreviation").value("BUY"));
    }

    @Test
    void testGetOrderTypeById_found() throws Exception {
        Mockito.when(orderTypeService.getById(1)).thenReturn(Optional.of(orderTypeDTO));
        mockMvc.perform(get("/api/v1/orderTypes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abbreviation").value("BUY"));
    }

    @Test
    void testGetOrderTypeById_notFound() throws Exception {
        Mockito.when(orderTypeService.getById(1)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/orderTypes/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateOrderType() throws Exception {
        Mockito.when(orderTypeService.create(any(OrderTypePostDTO.class))).thenReturn(orderTypeDTO);
        mockMvc.perform(post("/api/v1/orderTypes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderTypePostDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abbreviation").value("BUY"));
    }

    @Test
    void testUpdateOrderType_found() throws Exception {
        Mockito.when(orderTypeService.update(eq(1), any(OrderTypeDTO.class))).thenReturn(Optional.of(orderTypeDTO));
        mockMvc.perform(put("/api/v1/orderType/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderTypeDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abbreviation").value("BUY"));
    }

    @Test
    void testUpdateOrderType_notFound() throws Exception {
        Mockito.when(orderTypeService.update(eq(1), any(OrderTypeDTO.class))).thenReturn(Optional.empty());
        mockMvc.perform(put("/api/v1/orderType/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderTypeDTO)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteOrderType_success() throws Exception {
        Mockito.when(orderTypeService.delete(1, 1)).thenReturn(true);
        mockMvc.perform(delete("/api/v1/orderType/1?version=1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteOrderType_conflict() throws Exception {
        Mockito.when(orderTypeService.delete(1, 1)).thenReturn(false);
        mockMvc.perform(delete("/api/v1/orderType/1?version=1"))
                .andExpect(status().isConflict());
    }
} 