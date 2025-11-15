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
import org.kasbench.globeco_order_service.dto.BulkTradeOrderResponseDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderResultDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderResponseDTO;
import org.kasbench.globeco_order_service.entity.*;
import org.kasbench.globeco_order_service.repository.*;
import org.kasbench.globeco_order_service.service.SecurityCacheService;
import org.kasbench.globeco_order_service.service.PortfolioCacheService;
import org.kasbench.globeco_order_service.service.PortfolioServiceClient;
import org.kasbench.globeco_order_service.service.SecurityServiceClient;
import org.kasbench.globeco_order_service.service.ValidationCacheService;
import org.springframework.transaction.PlatformTransactionManager;
import org.mockito.Mock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
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
    @Mock private SecurityCacheService securityCacheService;
    @Mock private PortfolioCacheService portfolioCacheService;
    @Mock private PortfolioServiceClient portfolioServiceClient;
    @Mock private SecurityServiceClient securityServiceClient;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private ValidationCacheService validationCacheService;
    
    private OrderService orderService;

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
        
        // Manually create OrderService with mocked dependencies
        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        BulkSubmissionPerformanceMonitor performanceMonitor = mock(BulkSubmissionPerformanceMonitor.class);
        BatchUpdateService batchUpdateService = mock(BatchUpdateService.class);
        
        // Mock the meter registry to return mock timers and counters
        when(meterRegistry.timer(any(String.class))).thenReturn(mock(Timer.class));
        when(meterRegistry.counter(any(String.class))).thenReturn(mock(Counter.class));
        
        orderService = new OrderService(
                orderRepository,
                statusRepository,
                blotterRepository,
                orderTypeRepository,
                restTemplate,
                securityCacheService,
                portfolioCacheService,
                portfolioServiceClient,
                securityServiceClient,
                transactionManager,
                meterRegistry,
                performanceMonitor,
                batchUpdateService,
                "http://test-trade-service:8082"
        );
        
        // Set the validation cache service using reflection since it's @Autowired
        try {
            java.lang.reflect.Field field = OrderService.class.getDeclaredField("validationCacheService");
            field.setAccessible(true);
            field.set(orderService, validationCacheService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject validationCacheService", e);
        }
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
        List<Order> orders = Arrays.asList(order1, order2, order3);
        
        // Mock order repository batch loading
        when(orderRepository.findAllById(orderIds)).thenReturn(orders);
        
        // Mock bulk trade service response - all successful
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .message("All orders processed successfully")
                .totalRequested(3)
                .successful(3)
                .failed(0)
                .results(Arrays.asList(
                        TradeOrderResultDTO.success(0, TradeOrderResponseDTO.builder().id(99999).build()),
                        TradeOrderResultDTO.success(1, TradeOrderResponseDTO.builder().id(99998).build()),
                        TradeOrderResultDTO.success(2, TradeOrderResponseDTO.builder().id(99997).build())
                ))
                .build();
        
        when(restTemplate.postForEntity(anyString(), any(), eq(BulkTradeOrderResponseDTO.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(bulkResponse));
        
        // Mock status repository for SENT status
        Status sentStatus = Status.builder().id(2).abbreviation("SENT").description("Sent").version(1).build();
        when(statusRepository.findAll()).thenReturn(Arrays.asList(status, sentStatus));
        
        // Mock order save operations
        when(orderRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
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
            assertEquals(99999 - i, orderResult.getTradeOrderId()); // Different trade order IDs
            assertEquals(i, orderResult.getRequestIndex());
            assertEquals("Order submitted successfully", orderResult.getMessage());
        }
        
        // Verify repository interactions - now uses bulk operations
        verify(orderRepository, times(1)).findAllById(orderIds);
        verify(orderRepository, times(1)).saveAll(any());
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(BulkTradeOrderResponseDTO.class));
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

    // ========== Bulk Response Transformation Tests ==========

    @Test
    void testTransformBulkResponseToOrderServiceFormat_CompleteSuccess() {
        // Arrange
        List<Integer> originalOrderIds = Arrays.asList(101, 102, 103);
        
        List<TradeOrderResultDTO> tradeResults = Arrays.asList(
            TradeOrderResultDTO.builder()
                .requestIndex(0)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(TradeOrderResponseDTO.builder().id(1001).build())
                .build(),
            TradeOrderResultDTO.builder()
                .requestIndex(1)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(TradeOrderResponseDTO.builder().id(1002).build())
                .build(),
            TradeOrderResultDTO.builder()
                .requestIndex(2)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(TradeOrderResponseDTO.builder().id(1003).build())
                .build()
        );
        
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .message("All orders processed successfully")
                .totalRequested(3)
                .successful(3)
                .failed(0)
                .results(tradeResults)
                .build();

        // Act
        BatchSubmitResponseDTO result = orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, originalOrderIds);

        // Assert
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("All 3 orders submitted successfully", result.getMessage());
        assertEquals(3, result.getTotalRequested());
        assertEquals(3, result.getSuccessful());
        assertEquals(0, result.getFailed());
        assertEquals(3, result.getResults().size());

        // Verify individual results
        OrderSubmitResultDTO result1 = result.getResults().get(0);
        assertEquals(101, result1.getOrderId());
        assertEquals("SUCCESS", result1.getStatus());
        assertEquals("Order submitted successfully", result1.getMessage());
        assertEquals(1001, result1.getTradeOrderId());
        assertEquals(0, result1.getRequestIndex());

        OrderSubmitResultDTO result2 = result.getResults().get(1);
        assertEquals(102, result2.getOrderId());
        assertEquals("SUCCESS", result2.getStatus());
        assertEquals(1002, result2.getTradeOrderId());
        assertEquals(1, result2.getRequestIndex());

        OrderSubmitResultDTO result3 = result.getResults().get(2);
        assertEquals(103, result3.getOrderId());
        assertEquals("SUCCESS", result3.getStatus());
        assertEquals(1003, result3.getTradeOrderId());
        assertEquals(2, result3.getRequestIndex());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_CompleteFailure() {
        // Arrange
        List<Integer> originalOrderIds = Arrays.asList(201, 202);
        
        List<TradeOrderResultDTO> tradeResults = Arrays.asList(
            TradeOrderResultDTO.builder()
                .requestIndex(0)
                .status("FAILURE")
                .message("Invalid portfolio ID")
                .tradeOrder(null)
                .build(),
            TradeOrderResultDTO.builder()
                .requestIndex(1)
                .status("FAILURE")
                .message("Insufficient funds")
                .tradeOrder(null)
                .build()
        );
        
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("FAILURE")
                .message("All orders failed validation")
                .totalRequested(2)
                .successful(0)
                .failed(2)
                .results(tradeResults)
                .build();

        // Act
        BatchSubmitResponseDTO result = orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, originalOrderIds);

        // Assert
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals("All orders failed validation", result.getMessage());
        assertEquals(2, result.getTotalRequested());
        assertEquals(0, result.getSuccessful());
        assertEquals(2, result.getFailed());
        assertEquals(2, result.getResults().size());

        // Verify individual results
        OrderSubmitResultDTO result1 = result.getResults().get(0);
        assertEquals(201, result1.getOrderId());
        assertEquals("FAILURE", result1.getStatus());
        assertEquals("Invalid portfolio ID", result1.getMessage());
        assertNull(result1.getTradeOrderId());
        assertEquals(0, result1.getRequestIndex());

        OrderSubmitResultDTO result2 = result.getResults().get(1);
        assertEquals(202, result2.getOrderId());
        assertEquals("FAILURE", result2.getStatus());
        assertEquals("Insufficient funds", result2.getMessage());
        assertNull(result2.getTradeOrderId());
        assertEquals(1, result2.getRequestIndex());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_PartialSuccess() {
        // Arrange
        List<Integer> originalOrderIds = Arrays.asList(301, 302, 303);
        
        List<TradeOrderResultDTO> tradeResults = Arrays.asList(
            TradeOrderResultDTO.builder()
                .requestIndex(0)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(TradeOrderResponseDTO.builder().id(2001).build())
                .build(),
            TradeOrderResultDTO.builder()
                .requestIndex(1)
                .status("FAILURE")
                .message("Security not found")
                .tradeOrder(null)
                .build(),
            TradeOrderResultDTO.builder()
                .requestIndex(2)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(TradeOrderResponseDTO.builder().id(2003).build())
                .build()
        );
        
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("PARTIAL")
                .message("Some orders processed successfully")
                .totalRequested(3)
                .successful(2)
                .failed(1)
                .results(tradeResults)
                .build();

        // Act
        BatchSubmitResponseDTO result = orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, originalOrderIds);

        // Assert
        assertNotNull(result);
        assertEquals("PARTIAL", result.getStatus());
        assertEquals("2 of 3 orders submitted successfully, 1 failed", result.getMessage());
        assertEquals(3, result.getTotalRequested());
        assertEquals(2, result.getSuccessful());
        assertEquals(1, result.getFailed());
        assertEquals(3, result.getResults().size());

        // Verify mixed results
        OrderSubmitResultDTO result1 = result.getResults().get(0);
        assertEquals(301, result1.getOrderId());
        assertEquals("SUCCESS", result1.getStatus());
        assertEquals(2001, result1.getTradeOrderId());

        OrderSubmitResultDTO result2 = result.getResults().get(1);
        assertEquals(302, result2.getOrderId());
        assertEquals("FAILURE", result2.getStatus());
        assertEquals("Security not found", result2.getMessage());
        assertNull(result2.getTradeOrderId());

        OrderSubmitResultDTO result3 = result.getResults().get(2);
        assertEquals(303, result3.getOrderId());
        assertEquals("SUCCESS", result3.getStatus());
        assertEquals(2003, result3.getTradeOrderId());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_MissingResults() {
        // Arrange - simulate scenario where some results are missing from trade service response
        List<Integer> originalOrderIds = Arrays.asList(401, 402, 403);
        
        List<TradeOrderResultDTO> tradeResults = Arrays.asList(
            TradeOrderResultDTO.builder()
                .requestIndex(0)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(TradeOrderResponseDTO.builder().id(3001).build())
                .build(),
            // Missing result for index 1
            TradeOrderResultDTO.builder()
                .requestIndex(2)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(TradeOrderResponseDTO.builder().id(3003).build())
                .build()
        );
        
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("PARTIAL")
                .message("Some orders processed")
                .totalRequested(3)
                .successful(2)
                .failed(1)
                .results(tradeResults)
                .build();

        // Act
        BatchSubmitResponseDTO result = orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, originalOrderIds);

        // Assert
        assertNotNull(result);
        assertEquals("PARTIAL", result.getStatus());
        assertEquals(3, result.getResults().size());

        // Verify that missing result is handled as failure
        OrderSubmitResultDTO result1 = result.getResults().get(0);
        assertEquals(401, result1.getOrderId());
        assertEquals("SUCCESS", result1.getStatus());
        assertEquals(3001, result1.getTradeOrderId());

        OrderSubmitResultDTO result2 = result.getResults().get(1);
        assertEquals(402, result2.getOrderId());
        assertEquals("FAILURE", result2.getStatus());
        assertEquals("No result returned from trade service for this order", result2.getMessage());
        assertNull(result2.getTradeOrderId());

        OrderSubmitResultDTO result3 = result.getResults().get(2);
        assertEquals(403, result3.getOrderId());
        assertEquals("SUCCESS", result3.getStatus());
        assertEquals(3003, result3.getTradeOrderId());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_EmptyResults() {
        // Arrange
        List<Integer> originalOrderIds = Arrays.asList(501, 502);
        
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("FAILURE")
                .message("No results returned")
                .totalRequested(2)
                .successful(0)
                .failed(2)
                .results(new ArrayList<>()) // Empty results
                .build();

        // Act
        BatchSubmitResponseDTO result = orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, originalOrderIds);

        // Assert
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals("No results returned", result.getMessage());
        assertEquals(2, result.getTotalRequested());
        assertEquals(0, result.getSuccessful());
        assertEquals(2, result.getFailed());
        assertEquals(2, result.getResults().size());

        // All results should be failures due to missing trade service results
        for (int i = 0; i < result.getResults().size(); i++) {
            OrderSubmitResultDTO orderResult = result.getResults().get(i);
            assertEquals(originalOrderIds.get(i), orderResult.getOrderId());
            assertEquals("FAILURE", orderResult.getStatus());
            assertEquals("No result returned from trade service for this order", orderResult.getMessage());
            assertNull(orderResult.getTradeOrderId());
            assertEquals(i, orderResult.getRequestIndex());
        }
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_NullBulkResponse() {
        // Arrange
        List<Integer> originalOrderIds = Arrays.asList(601, 602);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.transformBulkResponseToOrderServiceFormat(null, originalOrderIds);
        });
        
        assertEquals("Bulk response cannot be null", exception.getMessage());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_NullOriginalOrderIds() {
        // Arrange
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .message("Success")
                .totalRequested(1)
                .successful(1)
                .failed(0)
                .results(new ArrayList<>())
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, null);
        });
        
        assertEquals("Original order IDs cannot be null or empty", exception.getMessage());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_EmptyOriginalOrderIds() {
        // Arrange
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("SUCCESS")
                .message("Success")
                .totalRequested(0)
                .successful(0)
                .failed(0)
                .results(new ArrayList<>())
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, new ArrayList<>());
        });
        
        assertEquals("Original order IDs cannot be null or empty", exception.getMessage());
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_NullResultsInBulkResponse() {
        // Arrange
        List<Integer> originalOrderIds = Arrays.asList(701, 702);
        
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("FAILURE")
                .message("Service error")
                .totalRequested(2)
                .successful(0)
                .failed(2)
                .results(null) // Null results
                .build();

        // Act
        BatchSubmitResponseDTO result = orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, originalOrderIds);

        // Assert
        assertNotNull(result);
        assertEquals("FAILURE", result.getStatus());
        assertEquals("Service error", result.getMessage());
        assertEquals(2, result.getResults().size());

        // All results should be failures due to null trade service results
        for (int i = 0; i < result.getResults().size(); i++) {
            OrderSubmitResultDTO orderResult = result.getResults().get(i);
            assertEquals(originalOrderIds.get(i), orderResult.getOrderId());
            assertEquals("FAILURE", orderResult.getStatus());
            assertEquals("No result returned from trade service for this order", orderResult.getMessage());
            assertNull(orderResult.getTradeOrderId());
            assertEquals(i, orderResult.getRequestIndex());
        }
    }

    @Test
    void testTransformBulkResponseToOrderServiceFormat_PreserveRequestIndexMapping() {
        // Arrange - test that request index mapping is preserved correctly
        List<Integer> originalOrderIds = Arrays.asList(801, 802, 803, 804);
        
        List<TradeOrderResultDTO> tradeResults = Arrays.asList(
            TradeOrderResultDTO.builder()
                .requestIndex(3) // Out of order
                .status("SUCCESS")
                .message("Success")
                .tradeOrder(TradeOrderResponseDTO.builder().id(4004).build())
                .build(),
            TradeOrderResultDTO.builder()
                .requestIndex(1) // Out of order
                .status("FAILURE")
                .message("Failed")
                .tradeOrder(null)
                .build(),
            TradeOrderResultDTO.builder()
                .requestIndex(0) // Out of order
                .status("SUCCESS")
                .message("Success")
                .tradeOrder(TradeOrderResponseDTO.builder().id(4001).build())
                .build()
            // Missing index 2
        );
        
        BulkTradeOrderResponseDTO bulkResponse = BulkTradeOrderResponseDTO.builder()
                .status("PARTIAL")
                .message("Mixed results")
                .totalRequested(4)
                .successful(2)
                .failed(2)
                .results(tradeResults)
                .build();

        // Act
        BatchSubmitResponseDTO result = orderService.transformBulkResponseToOrderServiceFormat(bulkResponse, originalOrderIds);

        // Assert
        assertNotNull(result);
        assertEquals(4, result.getResults().size());

        // Verify correct mapping by request index
        OrderSubmitResultDTO result0 = result.getResults().get(0); // Index 0
        assertEquals(801, result0.getOrderId());
        assertEquals("SUCCESS", result0.getStatus());
        assertEquals(4001, result0.getTradeOrderId());
        assertEquals(0, result0.getRequestIndex());

        OrderSubmitResultDTO result1 = result.getResults().get(1); // Index 1
        assertEquals(802, result1.getOrderId());
        assertEquals("FAILURE", result1.getStatus());
        assertEquals("Failed", result1.getMessage());
        assertNull(result1.getTradeOrderId());
        assertEquals(1, result1.getRequestIndex());

        OrderSubmitResultDTO result2 = result.getResults().get(2); // Index 2 - missing
        assertEquals(803, result2.getOrderId());
        assertEquals("FAILURE", result2.getStatus());
        assertEquals("No result returned from trade service for this order", result2.getMessage());
        assertNull(result2.getTradeOrderId());
        assertEquals(2, result2.getRequestIndex());

        OrderSubmitResultDTO result3 = result.getResults().get(3); // Index 3
        assertEquals(804, result3.getOrderId());
        assertEquals("SUCCESS", result3.getStatus());
        assertEquals(4004, result3.getTradeOrderId());
        assertEquals(3, result3.getRequestIndex());
    }
} 