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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import static org.junit.jupiter.api.Assertions.*;
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
                .portfolio(PortfolioDTO.builder()
                        .portfolioId("PORT12345678901234567890")
                        .name("Test Portfolio")
                        .build())
                .orderType(orderTypeDTO)
                .security(SecurityDTO.builder()
                        .securityId("SEC12345678901234567890")
                        .ticker("AAPL")
                        .build())
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

    // =============== BATCH PROCESSING TESTS ===============
    
    @Test
    void testCreateOrders_AllSuccess() throws Exception {
        // Setup test data
        List<OrderPostDTO> orders = Arrays.asList(orderPostDTO, orderPostDTO);
        
        List<OrderPostResponseDTO> orderResponses = Arrays.asList(
            OrderPostResponseDTO.success(orderWithDetailsDTO, 10L, 0),
            OrderPostResponseDTO.success(orderWithDetailsDTO, 11L, 1)
        );
        
        OrderListResponseDTO successResponse = OrderListResponseDTO.success(orderResponses);
        
        // Mock service response
        Mockito.when(orderService.processBatchOrders(any(List.class))).thenReturn(successResponse);
        
        // Execute request
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orders)))
                .andExpect(status().isOk()) // HTTP 200 for complete success
                .andReturn().getResponse().getContentAsString();
        
        // Verify response
        OrderListResponseDTO result = objectMapper.readValue(response, OrderListResponseDTO.class);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(2, result.getTotalReceived());
        assertEquals(2, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertEquals(2, result.getOrders().size());
    }
    
    @Test
    void testCreateOrders_PartialSuccess() throws Exception {
        // Setup test data
        List<OrderPostDTO> orders = Arrays.asList(orderPostDTO, orderPostDTO);
        
        List<OrderPostResponseDTO> orderResponses = Arrays.asList(
            OrderPostResponseDTO.success(orderWithDetailsDTO, 10L, 0),
            OrderPostResponseDTO.failure("Validation error", 1)
        );
        
        OrderListResponseDTO partialResponse = OrderListResponseDTO.partial(orderResponses);
        
        // Mock service response
        Mockito.when(orderService.processBatchOrders(any(List.class))).thenReturn(partialResponse);
        
        // Execute request
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orders)))
                .andExpect(status().isMultiStatus()) // HTTP 207 for partial success
                .andReturn().getResponse().getContentAsString();
        
        // Verify response
        OrderListResponseDTO result = objectMapper.readValue(response, OrderListResponseDTO.class);
        assertEquals("PARTIAL", result.getStatus());
        assertEquals(2, result.getTotalReceived());
        assertEquals(1, result.getSuccessful());
        assertEquals(1, result.getFailed());
    }
    
    @Test
    void testCreateOrders_AllFailure_Processing() throws Exception {
        // Setup test data
        List<OrderPostDTO> orders = Arrays.asList(orderPostDTO, orderPostDTO);
        
        OrderListResponseDTO failureResponse = OrderListResponseDTO.failure("All orders failed", 2, new ArrayList<>());
        
        // Mock service response
        Mockito.when(orderService.processBatchOrders(any(List.class))).thenReturn(failureResponse);
        
        // Execute request
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orders)))
                .andExpect(status().isMultiStatus()) // HTTP 207 for processing failures
                .andReturn().getResponse().getContentAsString();
        
        // Verify response
        OrderListResponseDTO result = objectMapper.readValue(response, OrderListResponseDTO.class);
        assertEquals("FAILURE", result.getStatus());
        assertEquals(2, result.getTotalReceived());
        assertEquals(0, result.getSuccessful());
        assertEquals(2, result.getFailed());
    }
    
    @Test
    void testCreateOrders_ValidationFailure() throws Exception {
        // Setup test data
        List<OrderPostDTO> orders = Arrays.asList(orderPostDTO);
        
        OrderListResponseDTO validationFailure = OrderListResponseDTO.validationFailure("Invalid request data");
        
        // Mock service response
        Mockito.when(orderService.processBatchOrders(any(List.class))).thenReturn(validationFailure);
        
        // Execute request
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orders)))
                .andExpect(status().isBadRequest()) // HTTP 400 for validation failures
                .andReturn().getResponse().getContentAsString();
        
        // Verify response
        OrderListResponseDTO result = objectMapper.readValue(response, OrderListResponseDTO.class);
        assertEquals("FAILURE", result.getStatus());
        assertEquals(0, result.getTotalReceived());
    }
    
    @Test
    void testCreateOrders_ExceedsBatchSizeLimit() throws Exception {
        // Create a batch larger than allowed (1001 orders)
        List<OrderPostDTO> orders = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            orders.add(orderPostDTO);
        }
        
        // Execute request
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orders)))
                .andExpect(status().isPayloadTooLarge()) // HTTP 413 for oversized batch
                .andReturn().getResponse().getContentAsString();
        
        // Verify response
        OrderListResponseDTO result = objectMapper.readValue(response, OrderListResponseDTO.class);
        assertEquals("FAILURE", result.getStatus());
        assertTrue(result.getMessage().contains("Batch size 1001 exceeds maximum allowed size of 1000"));
    }
    
    @Test
    void testCreateOrders_NullRequestBody() throws Exception {
        // Execute request with null body
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest()) // HTTP 400 for null request
                .andReturn().getResponse().getContentAsString();
        
        // Spring returns empty body for HttpMessageNotReadableException
        // This is expected behavior when sending null to a List<> parameter
        assertTrue(response.isEmpty() || response.isBlank());
    }
    
    @Test
    void testCreateOrders_EmptyArray() throws Exception {
        // Setup empty array
        List<OrderPostDTO> orders = new ArrayList<>();
        
        OrderListResponseDTO emptyResponse = OrderListResponseDTO.validationFailure("No orders provided for processing");
        
        // Mock service response
        Mockito.when(orderService.processBatchOrders(any(List.class))).thenReturn(emptyResponse);
        
        // Execute request
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orders)))
                .andExpect(status().isBadRequest()) // HTTP 400 for empty array
                .andReturn().getResponse().getContentAsString();
        
        // Verify response
        OrderListResponseDTO result = objectMapper.readValue(response, OrderListResponseDTO.class);
        assertEquals("FAILURE", result.getStatus());
        assertTrue(result.getMessage().contains("No orders provided for processing"));
    }
    
    @Test
    void testCreateOrders_MalformedJson() throws Exception {
        // Execute request with malformed JSON
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest()); // HTTP 400 for malformed JSON
    }
    
    @Test
    void testCreateOrders_ServiceException() throws Exception {
        // Setup test data
        List<OrderPostDTO> orders = Arrays.asList(orderPostDTO);
        
        // Mock service to throw exception
        Mockito.when(orderService.processBatchOrders(any(List.class)))
               .thenThrow(new RuntimeException("Database connection failed"));
        
        // Execute request
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orders)))
                .andExpect(status().isInternalServerError()) // HTTP 500 for service exceptions
                .andReturn().getResponse().getContentAsString();
        
        // Verify response
        OrderListResponseDTO result = objectMapper.readValue(response, OrderListResponseDTO.class);
        assertEquals("FAILURE", result.getStatus());
        assertTrue(result.getMessage().contains("Internal server error"));
    }
    
    @Test
    void testCreateOrders_MaxBatchSize() throws Exception {
        // Create exactly 1000 orders (maximum allowed)
        List<OrderPostDTO> orders = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            orders.add(orderPostDTO);
        }
        
        List<OrderPostResponseDTO> orderResponses = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            orderResponses.add(OrderPostResponseDTO.success(orderWithDetailsDTO, (long) i, i));
        }
        
        OrderListResponseDTO successResponse = OrderListResponseDTO.success(orderResponses);
        
        // Mock service response
        Mockito.when(orderService.processBatchOrders(any(List.class))).thenReturn(successResponse);
        
        // Execute request
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orders)))
                .andExpect(status().isOk()) // HTTP 200 for complete success
                .andReturn().getResponse().getContentAsString();
        
        // Verify response
        OrderListResponseDTO result = objectMapper.readValue(response, OrderListResponseDTO.class);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(1000, result.getTotalReceived());
        assertEquals(1000, result.getSuccessful());
        assertEquals(0, result.getFailed());
    }

    // =============== EXISTING TESTS (unchanged) ===============

    @Test
    void testGetAllOrders() throws Exception {
        // Mock the paginated getAll method instead of the simple getAll method
        Page<OrderWithDetailsDTO> mockPage = new PageImpl<>(Arrays.asList(orderWithDetailsDTO));
        Mockito.when(orderService.getAll(eq(50), eq(0), eq(null), any(Map.class)))
               .thenReturn(mockPage);
        
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
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