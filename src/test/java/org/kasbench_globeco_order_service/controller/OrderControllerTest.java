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

@SpringBootTest(classes = GlobecoOrderServiceApplication.class)
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

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
} 