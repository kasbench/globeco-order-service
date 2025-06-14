package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.dto.OrderPostDTO;
import org.kasbench.globeco_order_service.dto.OrderWithDetailsDTO;
import org.kasbench.globeco_order_service.dto.OrderDTO;
import org.kasbench.globeco_order_service.dto.OrderListResponseDTO;
import org.kasbench.globeco_order_service.dto.OrderPostResponseDTO;
import org.kasbench.globeco_order_service.dto.BatchSubmitResponseDTO;
import org.kasbench.globeco_order_service.dto.OrderSubmitResultDTO;
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
import java.util.ArrayList;
import java.util.Collections;

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

    // =============== BATCH PROCESSING TESTS ===============
    
    @Test
    void testProcessBatchOrders_AllSuccess() {
        // Setup test data
        List<OrderPostDTO> orders = createValidOrderBatch(3);
        
        // Mock repository responses for validation
        when(blotterRepository.existsById(3)).thenReturn(true);
        when(statusRepository.existsById(1)).thenReturn(true);
        when(orderTypeRepository.existsById(2)).thenReturn(true);
        
        // Mock repository responses for creation
        when(blotterRepository.findById(3)).thenReturn(Optional.of(blotter));
        when(statusRepository.findById(1)).thenReturn(Optional.of(status));
        when(orderTypeRepository.findById(2)).thenReturn(Optional.of(orderType));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order savedOrder = invocation.getArgument(0);
            savedOrder.setId((int) (Math.random() * 1000) + 1); // Set random ID
            return savedOrder;
        });
        
        // Execute batch processing
        OrderListResponseDTO result = orderService.processBatchOrders(orders);
        
        // Verify results
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(3, result.getTotalReceived());
        assertEquals(3, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertEquals(3, result.getOrders().size());
        assertTrue(result.getMessage().contains("All 3 orders processed successfully"));
        
        // Verify all individual orders succeeded
        for (int i = 0; i < 3; i++) {
            OrderPostResponseDTO orderResponse = result.getOrders().get(i);
            assertEquals("SUCCESS", orderResponse.getStatus());
            assertEquals(i, orderResponse.getRequestIndex());
            assertNotNull(orderResponse.getOrder());
            assertNotNull(orderResponse.getOrderId());
        }
        
        // Verify repository interactions
        verify(orderRepository, times(3)).save(any(Order.class));
    }
    
    @Test 
    void testProcessBatchOrders_PartialSuccess() {
        // Setup test data - 2 valid, 1 with missing required field
        List<OrderPostDTO> orders = new ArrayList<>();
        orders.add(createValidOrderPostDTO());
        orders.add(createValidOrderPostDTO());
        orders.add(OrderPostDTO.builder()
                .blotterId(3)
                .statusId(1)
                // Missing portfolioId - should fail validation
                .orderTypeId(2)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00"))
                .limitPrice(new BigDecimal("50.25"))
                .build());
        
        // Mock repository responses for successful orders
        when(blotterRepository.existsById(3)).thenReturn(true);
        when(statusRepository.existsById(1)).thenReturn(true); 
        when(orderTypeRepository.existsById(2)).thenReturn(true);
        when(blotterRepository.findById(3)).thenReturn(Optional.of(blotter));
        when(statusRepository.findById(1)).thenReturn(Optional.of(status));
        when(orderTypeRepository.findById(2)).thenReturn(Optional.of(orderType));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order savedOrder = invocation.getArgument(0);
            savedOrder.setId((int) (Math.random() * 1000) + 1);
            return savedOrder;
        });
        
        // Execute batch processing
        OrderListResponseDTO result = orderService.processBatchOrders(orders);
        
        // Verify results
        assertNotNull(result);
        assertEquals("PARTIAL", result.getStatus());
        assertEquals(3, result.getTotalReceived());
        assertEquals(2, result.getSuccessful());
        assertEquals(1, result.getFailed());
        assertEquals(3, result.getOrders().size());
        assertTrue(result.getMessage().contains("2 of 3 orders processed successfully"));
        
        // Verify individual order results
        assertEquals("SUCCESS", result.getOrders().get(0).getStatus());
        assertEquals("SUCCESS", result.getOrders().get(1).getStatus());
        assertEquals("FAILURE", result.getOrders().get(2).getStatus());
        assertTrue(result.getOrders().get(2).getMessage().contains("Portfolio ID is required"));
        
        // Verify only 2 orders were saved (the successful ones)
        verify(orderRepository, times(2)).save(any(Order.class));
    }
    
    @Test
    void testProcessBatchOrders_AllFailure() {
        // Setup test data with invalid foreign key references
        List<OrderPostDTO> orders = new ArrayList<>();
        orders.add(OrderPostDTO.builder()
                .blotterId(999) // Non-existent blotter
                .statusId(1)
                .portfolioId("VALID_PORTFOLIO")
                .orderTypeId(2)
                .securityId("VALID_SECURITY")
                .quantity(new BigDecimal("100.00"))
                .limitPrice(new BigDecimal("50.25"))
                .build());
        orders.add(OrderPostDTO.builder()
                .blotterId(3)
                .statusId(888) // Non-existent status
                .portfolioId("VALID_PORTFOLIO")
                .orderTypeId(2)
                .securityId("VALID_SECURITY")
                .quantity(new BigDecimal("100.00"))
                .limitPrice(new BigDecimal("50.25"))
                .build());
        
        // Mock repository responses to return false for non-existent entities
        when(blotterRepository.existsById(999)).thenReturn(false);
        when(blotterRepository.existsById(3)).thenReturn(true); // Allow second order to pass blotter check
        when(statusRepository.existsById(888)).thenReturn(false);
        
        // Execute batch processing
        OrderListResponseDTO result = orderService.processBatchOrders(orders);
        
        // Verify results
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals(2, result.getTotalReceived());
        assertEquals(0, result.getSuccessful());
        assertEquals(2, result.getFailed());
        assertEquals(2, result.getOrders().size());
        
        // Verify individual order failures
        assertEquals("FAILURE", result.getOrders().get(0).getStatus());
        assertTrue(result.getOrders().get(0).getMessage().contains("Blotter with ID 999 not found"));
        assertEquals("FAILURE", result.getOrders().get(1).getStatus());
        assertTrue(result.getOrders().get(1).getMessage().contains("Status with ID 888 not found"));
        
        // Verify no orders were saved
        verify(orderRepository, never()).save(any(Order.class));
    }
    
    @Test
    void testProcessBatchOrders_ExceedsBatchSizeLimit() {
        // Create a batch larger than the limit (1000)
        List<OrderPostDTO> orders = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            orders.add(createValidOrderPostDTO());
        }
        
        // Execute batch processing
        OrderListResponseDTO result = orderService.processBatchOrders(orders);
        
        // Verify results
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals(0, result.getTotalReceived());
        assertEquals(0, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertTrue(result.getMessage().contains("Batch size 1001 exceeds maximum allowed size of 1000"));
        
        // Verify no repository interactions
        verify(orderRepository, never()).save(any(Order.class));
    }
    
    @Test
    void testProcessBatchOrders_EmptyBatch() {
        List<OrderPostDTO> orders = new ArrayList<>();
        
        // Execute batch processing
        OrderListResponseDTO result = orderService.processBatchOrders(orders);
        
        // Verify results
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals(0, result.getTotalReceived());
        assertEquals(0, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertTrue(result.getMessage().contains("No orders provided for processing"));
        
        // Verify no repository interactions
        verify(orderRepository, never()).save(any(Order.class));
    }
    
    @Test 
    void testProcessBatchOrders_ValidationErrors() {
        List<OrderPostDTO> orders = new ArrayList<>();
        
        // Order with null required fields
        orders.add(OrderPostDTO.builder()
                .blotterId(null) // Required field is null
                .statusId(1)
                .portfolioId("VALID_PORTFOLIO")
                .orderTypeId(2)
                .securityId("VALID_SECURITY")
                .quantity(new BigDecimal("100.00"))
                .limitPrice(new BigDecimal("50.25"))
                .build());
        
        // Order with negative quantity
        orders.add(OrderPostDTO.builder()
                .blotterId(3)
                .statusId(1)
                .portfolioId("VALID_PORTFOLIO")
                .orderTypeId(2)
                .securityId("VALID_SECURITY")
                .quantity(new BigDecimal("-100.00")) // Invalid negative quantity
                .limitPrice(new BigDecimal("50.25"))
                .build());
                
        // Order with empty string fields
        orders.add(OrderPostDTO.builder()
                .blotterId(3)
                .statusId(1)
                .portfolioId("") // Empty string
                .orderTypeId(2)
                .securityId("VALID_SECURITY")
                .quantity(new BigDecimal("100.00"))
                .limitPrice(new BigDecimal("50.25"))
                .build());
        
        // Execute batch processing
        OrderListResponseDTO result = orderService.processBatchOrders(orders);
        
        // Verify results
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals(3, result.getTotalReceived());
        assertEquals(0, result.getSuccessful());
        assertEquals(3, result.getFailed());
        
        // Verify specific validation error messages
        assertTrue(result.getOrders().get(0).getMessage().contains("Blotter ID is required"));
        assertTrue(result.getOrders().get(1).getMessage().contains("Quantity must be positive"));
        assertTrue(result.getOrders().get(2).getMessage().contains("Portfolio ID is required"));
        
        // Verify no orders were saved
        verify(orderRepository, never()).save(any(Order.class));
    }
    
    @Test
    void testProcessBatchOrders_MaxBatchSize() {
        // Create exactly 1000 orders (the maximum allowed)
        List<OrderPostDTO> orders = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            orders.add(createValidOrderPostDTO());
        }
        
        // Mock repository responses
        when(blotterRepository.existsById(3)).thenReturn(true);
        when(statusRepository.existsById(1)).thenReturn(true);
        when(orderTypeRepository.existsById(2)).thenReturn(true);
        when(blotterRepository.findById(3)).thenReturn(Optional.of(blotter));
        when(statusRepository.findById(1)).thenReturn(Optional.of(status));
        when(orderTypeRepository.findById(2)).thenReturn(Optional.of(orderType));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order savedOrder = invocation.getArgument(0);
            savedOrder.setId((int) (Math.random() * 1000) + 1);
            return savedOrder;
        });
        
        // Execute batch processing
        OrderListResponseDTO result = orderService.processBatchOrders(orders);
        
        // Verify results
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(1000, result.getTotalReceived());
        assertEquals(1000, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertEquals(1000, result.getOrders().size());
        
        // Verify all orders were saved
        verify(orderRepository, times(1000)).save(any(Order.class));
    }
    
    // Helper methods for creating test data
    private List<OrderPostDTO> createValidOrderBatch(int size) {
        List<OrderPostDTO> orders = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            orders.add(createValidOrderPostDTO());
        }
        return orders;
    }
    
    private OrderPostDTO createValidOrderPostDTO() {
        return OrderPostDTO.builder()
                .blotterId(3)
                .statusId(1)
                .portfolioId("PORT12345678901234567890")
                .orderTypeId(2)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00"))
                .limitPrice(new BigDecimal("50.25"))
                .orderTimestamp(now)
                .version(1)
                .build();
    }

    // =============== EXISTING TESTS (unchanged) ===============

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
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(tradeServiceResponse));
        Status sentStatus = Status.builder().id(2).abbreviation("SENT").description("Sent").version(1).build();
        when(statusRepository.findAll()).thenReturn(Arrays.asList(status, sentStatus));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrderDTO result = orderService.submitOrder(1);
        assertNotNull(result);
        assertEquals(99999, result.getTradeOrderId());
        assertEquals(2, result.getStatusId());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void testSubmitOrder_notNewStatus() {
        Status notNew = Status.builder().id(3).abbreviation("SENT").description("Sent").version(1).build();
        Order order = Order.builder().id(1).status(notNew).build();
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(statusRepository.findAll()).thenReturn(Arrays.asList(notNew));
        OrderDTO result = orderService.submitOrder(1);
        assertNull(result);
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
        Status sentStatus = Status.builder().id(2).abbreviation("SENT").description("Sent").version(1).build();
        when(statusRepository.findAll()).thenReturn(Arrays.asList(status, sentStatus));
        when(blotterRepository.findById(anyInt())).thenReturn(Optional.of(blotter));
        when(orderTypeRepository.findById(anyInt())).thenReturn(Optional.of(orderType));
        OrderDTO result = orderService.submitOrder(1);
        assertNull(result);
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

    @Test
    void testProcessBatchOrders_NullLimitPrice() {
        // Test that null limit prices are allowed (market orders)
        OrderPostDTO marketOrder = OrderPostDTO.builder()
                .blotterId(3)
                .statusId(1)
                .portfolioId("PORT12345678901234567890")
                .orderTypeId(2)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00"))
                .limitPrice(null) // This should be allowed
                .orderTimestamp(now)
                .version(1)
                .build();

        List<OrderPostDTO> orders = Arrays.asList(marketOrder);

        // Mock repository responses to return valid entities
        when(blotterRepository.existsById(3)).thenReturn(true);
        when(statusRepository.existsById(1)).thenReturn(true);
        when(orderTypeRepository.existsById(2)).thenReturn(true);
        when(blotterRepository.findById(3)).thenReturn(Optional.of(blotter));
        when(statusRepository.findById(1)).thenReturn(Optional.of(status));
        when(orderTypeRepository.findById(2)).thenReturn(Optional.of(orderType));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        // Execute the batch processing
        OrderListResponseDTO result = orderService.processBatchOrders(orders);

        // Verify successful processing
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(1, result.getTotalReceived());
        assertEquals(1, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertEquals(1, result.getOrders().size());
        assertEquals("SUCCESS", result.getOrders().get(0).getStatus());
        assertEquals("Order created successfully", result.getOrders().get(0).getMessage());
    }

    @Test
    void testProcessBatchOrders_NegativeLimitPrice() {
        // Test that negative limit prices are rejected
        OrderPostDTO invalidOrder = OrderPostDTO.builder()
                .blotterId(3)
                .statusId(1)
                .portfolioId("PORT12345678901234567890")
                .orderTypeId(2)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00"))
                .limitPrice(new BigDecimal("-50.25")) // This should be rejected
                .orderTimestamp(now)
                .version(1)
                .build();

        List<OrderPostDTO> orders = Arrays.asList(invalidOrder);

        // Execute the batch processing
        OrderListResponseDTO result = orderService.processBatchOrders(orders);

        // Verify validation failure
        assertEquals("FAILURE", result.getStatus());
        assertEquals(1, result.getTotalReceived());
        assertEquals(0, result.getSuccessful());
        assertEquals(1, result.getFailed());
        assertEquals(1, result.getOrders().size());
        assertEquals("FAILURE", result.getOrders().get(0).getStatus());
        assertTrue(result.getOrders().get(0).getMessage().contains("Limit price must be positive when provided"));
    }

    @Test
    void testProcessBatchOrders_ZeroLimitPrice() {
        // Test that zero limit prices are rejected
        OrderPostDTO invalidOrder = OrderPostDTO.builder()
                .blotterId(3)
                .statusId(1)
                .portfolioId("PORT12345678901234567890")
                .orderTypeId(2)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00"))
                .limitPrice(new BigDecimal("0.00")) // This should be rejected
                .orderTimestamp(now)
                .version(1)
                .build();

        List<OrderPostDTO> orders = Arrays.asList(invalidOrder);

        // Execute the batch processing
        OrderListResponseDTO result = orderService.processBatchOrders(orders);

        // Verify validation failure
        assertEquals("FAILURE", result.getStatus());
        assertEquals(1, result.getTotalReceived());
        assertEquals(0, result.getSuccessful());
        assertEquals(1, result.getFailed());
        assertEquals(1, result.getOrders().size());
        assertEquals("FAILURE", result.getOrders().get(0).getStatus());
        assertTrue(result.getOrders().get(0).getMessage().contains("Limit price must be positive when provided"));
    }

    // =============== BATCH SUBMISSION TESTS ===============

    @Test
    void testSubmitOrdersBatch_AllSuccess() {
        // Setup test data - 3 orders in NEW status
        List<Integer> orderIds = Arrays.asList(1, 2, 3);
        
        // Create orders in NEW status
        Order order1 = createOrderInStatus(1, "NEW");
        Order order2 = createOrderInStatus(2, "NEW");
        Order order3 = createOrderInStatus(3, "NEW");
        
        // Mock order repository responses
        when(orderRepository.findById(1)).thenReturn(Optional.of(order1));
        when(orderRepository.findById(2)).thenReturn(Optional.of(order2));
        when(orderRepository.findById(3)).thenReturn(Optional.of(order3));
        
        // Mock trade service responses - all successful
        String tradeServiceResponse = "{\"id\":99999}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(tradeServiceResponse));
        
        // Mock status repository for SENT status
        Status sentStatus = Status.builder().id(2).abbreviation("SENT").description("Sent").version(1).build();
        when(statusRepository.findAll()).thenReturn(Arrays.asList(status, sentStatus));
        
        // Mock order save operations
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Execute batch submission
        BatchSubmitResponseDTO result = orderService.submitOrdersBatch(orderIds);
        
        // Verify results
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(3, result.getTotalRequested());
        assertEquals(3, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertEquals(3, result.getResults().size());
        assertTrue(result.getMessage().contains("All 3 orders submitted successfully"));
        
        // Verify individual order results
        for (int i = 0; i < 3; i++) {
            OrderSubmitResultDTO orderResult = result.getResults().get(i);
            assertEquals("SUCCESS", orderResult.getStatus());
            assertEquals(orderIds.get(i), orderResult.getOrderId());
            assertEquals(99999, orderResult.getTradeOrderId());
            assertEquals(i, orderResult.getRequestIndex());
            assertEquals("Order submitted successfully", orderResult.getMessage());
        }
        
        // Verify repository interactions
        verify(orderRepository, times(3)).findById(anyInt());
        verify(orderRepository, times(3)).save(any(Order.class));
        verify(restTemplate, times(3)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    void testSubmitOrdersBatch_PartialSuccess() {
        // Setup test data - 2 orders in NEW status, 1 in SENT status
        List<Integer> orderIds = Arrays.asList(1, 2, 3);
        
        Order order1 = createOrderInStatus(1, "NEW");    // Should succeed
        Order order2 = createOrderInStatus(2, "NEW");    // Should succeed  
        Order order3 = createOrderInStatus(3, "SENT");   // Should fail - not in NEW status
        
        // Mock order repository responses
        when(orderRepository.findById(1)).thenReturn(Optional.of(order1));
        when(orderRepository.findById(2)).thenReturn(Optional.of(order2));
        when(orderRepository.findById(3)).thenReturn(Optional.of(order3));
        
        // Mock trade service responses for valid orders
        String tradeServiceResponse = "{\"id\":88888}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(tradeServiceResponse));
        
        // Mock status repository
        Status sentStatus = Status.builder().id(2).abbreviation("SENT").description("Sent").version(1).build();
        when(statusRepository.findAll()).thenReturn(Arrays.asList(status, sentStatus));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Execute batch submission
        BatchSubmitResponseDTO result = orderService.submitOrdersBatch(orderIds);
        
        // Verify results
        assertNotNull(result);
        assertEquals("PARTIAL", result.getStatus());
        assertEquals(3, result.getTotalRequested());
        assertEquals(2, result.getSuccessful());
        assertEquals(1, result.getFailed());
        assertEquals(3, result.getResults().size());
        assertTrue(result.getMessage().contains("2 of 3 orders submitted successfully"));
        
        // Verify individual results
        assertEquals("SUCCESS", result.getResults().get(0).getStatus());
        assertEquals("SUCCESS", result.getResults().get(1).getStatus());
        assertEquals("FAILURE", result.getResults().get(2).getStatus());
        assertTrue(result.getResults().get(2).getMessage().contains("not in NEW status"));
        
        // Verify only 2 orders were saved (the successful ones)
        verify(orderRepository, times(2)).save(any(Order.class));
        verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    void testSubmitOrdersBatch_AllFailure() {
        // Setup test data - all orders not found
        List<Integer> orderIds = Arrays.asList(999, 998, 997);
        
        // Mock order repository to return empty for all orders
        when(orderRepository.findById(999)).thenReturn(Optional.empty());
        when(orderRepository.findById(998)).thenReturn(Optional.empty());
        when(orderRepository.findById(997)).thenReturn(Optional.empty());
        
        // Execute batch submission
        BatchSubmitResponseDTO result = orderService.submitOrdersBatch(orderIds);
        
        // Verify results
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals(3, result.getTotalRequested());
        assertEquals(0, result.getSuccessful());
        assertEquals(3, result.getFailed());
        assertEquals(3, result.getResults().size());
        
        // Verify all individual results are failures
        for (int i = 0; i < 3; i++) {
            OrderSubmitResultDTO orderResult = result.getResults().get(i);
            assertEquals("FAILURE", orderResult.getStatus());
            assertEquals(orderIds.get(i), orderResult.getOrderId());
            assertNull(orderResult.getTradeOrderId());
            assertEquals(i, orderResult.getRequestIndex());
            assertEquals("Order not found", orderResult.getMessage());
        }
        
        // Verify no orders were saved or submitted to trade service
        verify(orderRepository, never()).save(any(Order.class));
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void testSubmitOrdersBatch_TradeServiceFailures() {
        // Setup test data - orders in NEW status but trade service fails
        List<Integer> orderIds = Arrays.asList(1, 2);
        
        Order order1 = createOrderInStatus(1, "NEW");
        Order order2 = createOrderInStatus(2, "NEW");
        
        when(orderRepository.findById(1)).thenReturn(Optional.of(order1));
        when(orderRepository.findById(2)).thenReturn(Optional.of(order2));
        
        // Mock trade service to fail (non-CREATED response)
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Trade service error"));
        
        Status sentStatus = Status.builder().id(2).abbreviation("SENT").description("Sent").version(1).build();
        when(statusRepository.findAll()).thenReturn(Arrays.asList(status, sentStatus));
        
        // Execute batch submission
        BatchSubmitResponseDTO result = orderService.submitOrdersBatch(orderIds);
        
        // Verify results
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals(2, result.getTotalRequested());
        assertEquals(0, result.getSuccessful());
        assertEquals(2, result.getFailed());
        
        // Verify both orders failed due to trade service
        for (OrderSubmitResultDTO orderResult : result.getResults()) {
            assertEquals("FAILURE", orderResult.getStatus());
            assertEquals("Trade service submission failed", orderResult.getMessage());
        }
        
        // Verify no orders were saved (since trade service failed)
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void testSubmitOrdersBatch_ExceedsBatchSizeLimit() {
        // Create a list with more than 100 orders
        List<Integer> orderIds = new ArrayList<>();
        for (int i = 1; i <= 101; i++) {
            orderIds.add(i);
        }
        
        // Execute batch submission
        BatchSubmitResponseDTO result = orderService.submitOrdersBatch(orderIds);
        
        // Verify validation failure
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals(0, result.getTotalRequested());
        assertEquals(0, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertTrue(result.getMessage().contains("Batch size 101 exceeds maximum allowed size of 100"));
        assertEquals(0, result.getResults().size());
        
        // Verify no repository interactions
        verify(orderRepository, never()).findById(anyInt());
        verify(orderRepository, never()).save(any(Order.class));
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    void testSubmitOrdersBatch_EmptyBatch() {
        // Test with empty order list
        List<Integer> orderIds = Collections.emptyList();
        
        // Execute batch submission
        BatchSubmitResponseDTO result = orderService.submitOrdersBatch(orderIds);
        
        // Verify validation failure
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals(0, result.getTotalRequested());
        assertEquals(0, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertEquals("No orders to process", result.getMessage());
        assertEquals(0, result.getResults().size());
    }

    @Test
    void testSubmitOrdersBatch_MixedScenarios() {
        // Test with mixed scenarios: success, not found, wrong status, trade service failure
        List<Integer> orderIds = Arrays.asList(1, 999, 3, 4);
        
        Order order1 = createOrderInStatus(1, "NEW");     // Should succeed
        // Order 999 not found                           // Should fail - not found
        Order order3 = createOrderInStatus(3, "SENT");   // Should fail - wrong status
        Order order4 = createOrderInStatus(4, "NEW");    // Should fail - trade service failure
        
        when(orderRepository.findById(1)).thenReturn(Optional.of(order1));
        when(orderRepository.findById(999)).thenReturn(Optional.empty());
        when(orderRepository.findById(3)).thenReturn(Optional.of(order3));
        when(orderRepository.findById(4)).thenReturn(Optional.of(order4));
        
        // Mock trade service - succeed for order 1, fail for order 4
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body("{\"id\":77777}"))
            .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Service error"));
        
        Status sentStatus = Status.builder().id(2).abbreviation("SENT").description("Sent").version(1).build();
        when(statusRepository.findAll()).thenReturn(Arrays.asList(status, sentStatus));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Execute batch submission
        BatchSubmitResponseDTO result = orderService.submitOrdersBatch(orderIds);
        
        // Verify results
        assertNotNull(result);
        assertEquals("PARTIAL", result.getStatus());
        assertEquals(4, result.getTotalRequested());
        assertEquals(1, result.getSuccessful());
        assertEquals(3, result.getFailed());
        assertEquals(4, result.getResults().size());
        
        // Verify individual results
        assertEquals("SUCCESS", result.getResults().get(0).getStatus());
        assertEquals(77777, result.getResults().get(0).getTradeOrderId());
        
        assertEquals("FAILURE", result.getResults().get(1).getStatus());
        assertEquals("Order not found", result.getResults().get(1).getMessage());
        
        assertEquals("FAILURE", result.getResults().get(2).getStatus());
        assertTrue(result.getResults().get(2).getMessage().contains("not in NEW status"));
        
        assertEquals("FAILURE", result.getResults().get(3).getStatus());
        assertEquals("Trade service submission failed", result.getResults().get(3).getMessage());
        
        // Verify only 1 order was saved
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test 
    void testSubmitOrdersBatch_SingleOrder() {
        // Test batch submission with single order (edge case)
        List<Integer> orderIds = Arrays.asList(1);
        
        Order order1 = createOrderInStatus(1, "NEW");
        when(orderRepository.findById(1)).thenReturn(Optional.of(order1));
        
        String tradeServiceResponse = "{\"id\":55555}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(tradeServiceResponse));
        
        Status sentStatus = Status.builder().id(2).abbreviation("SENT").description("Sent").version(1).build();
        when(statusRepository.findAll()).thenReturn(Arrays.asList(status, sentStatus));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Execute batch submission
        BatchSubmitResponseDTO result = orderService.submitOrdersBatch(orderIds);
        
        // Verify results
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(1, result.getTotalRequested());
        assertEquals(1, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertEquals(1, result.getResults().size());
        
        OrderSubmitResultDTO orderResult = result.getResults().get(0);
        assertEquals("SUCCESS", orderResult.getStatus());
        assertEquals(1, orderResult.getOrderId());
        assertEquals(55555, orderResult.getTradeOrderId());
        assertEquals(0, orderResult.getRequestIndex());
    }

    @Test
    void testSubmitOrdersBatch_MaxBatchSize() {
        // Test with exactly 100 orders (maximum allowed)
        List<Integer> orderIds = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            orderIds.add(i);
        }
        
        // Mock all orders as found and in NEW status
        for (int i = 1; i <= 100; i++) {
            Order order = createOrderInStatus(i, "NEW");
            when(orderRepository.findById(i)).thenReturn(Optional.of(order));
        }
        
        // Mock trade service to always succeed
        String tradeServiceResponse = "{\"id\":12345}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(tradeServiceResponse));
        
        Status sentStatus = Status.builder().id(2).abbreviation("SENT").description("Sent").version(1).build();
        when(statusRepository.findAll()).thenReturn(Arrays.asList(status, sentStatus));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Execute batch submission
        BatchSubmitResponseDTO result = orderService.submitOrdersBatch(orderIds);
        
        // Verify results
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(100, result.getTotalRequested());
        assertEquals(100, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertEquals(100, result.getResults().size());
        assertTrue(result.getMessage().contains("All 100 orders submitted successfully"));
        
        // Verify all repository interactions
        verify(orderRepository, times(100)).findById(anyInt());
        verify(orderRepository, times(100)).save(any(Order.class));
        verify(restTemplate, times(100)).postForEntity(anyString(), any(), eq(String.class));
    }

    // Helper method to create orders in specific status
    private Order createOrderInStatus(Integer id, String statusAbbreviation) {
        Status orderStatus = Status.builder()
                .id(statusAbbreviation.equals("NEW") ? 1 : 2)
                .abbreviation(statusAbbreviation)
                .description(statusAbbreviation.equals("NEW") ? "New" : "Sent")
                .version(1)
                .build();
                
        return Order.builder()
                .id(id)
                .blotter(blotter)
                .status(orderStatus)
                .portfolioId("PORT12345678901234567890")
                .orderType(orderType)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00000000"))
                .limitPrice(new BigDecimal("50.25000000"))
                .tradeOrderId(null)
                .orderTimestamp(now)
                .version(1)
                .build();
    }
} 