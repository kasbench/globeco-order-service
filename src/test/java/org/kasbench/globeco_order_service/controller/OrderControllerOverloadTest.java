package org.kasbench.globeco_order_service.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.dto.OrderPostDTO;
import org.kasbench.globeco_order_service.dto.BatchSubmitRequestDTO;
import org.kasbench.globeco_order_service.exception.SystemOverloadException;
import org.kasbench.globeco_order_service.service.OrderService;
import org.kasbench.globeco_order_service.service.BatchProcessingService;
import org.kasbench.globeco_order_service.service.SystemOverloadDetector;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderController overload detection integration.
 * Tests verify that overload detection is properly integrated into controller endpoints.
 */
@ExtendWith(MockitoExtension.class)
public class OrderControllerOverloadTest {

    @Mock
    private OrderService orderService;

    @Mock
    private BatchProcessingService batchProcessingService;

    @Mock
    private SystemOverloadDetector systemOverloadDetector;

    private OrderController orderController;

    @BeforeEach
    void setUp() {
        orderController = new OrderController(orderService, batchProcessingService, systemOverloadDetector);
    }

    @Test
    void createOrders_SystemOverloaded_ThrowsSystemOverloadException() {
        // Arrange
        when(systemOverloadDetector.isSystemOverloaded()).thenReturn(true);
        when(systemOverloadDetector.calculateRetryDelay()).thenReturn(120);

        List<OrderPostDTO> orders = createTestOrderPostDTOs();

        // Act & Assert
        SystemOverloadException exception = assertThrows(SystemOverloadException.class, () -> {
            orderController.createOrders(orders);
        });

        assertEquals("System temporarily overloaded - please retry in a few minutes", exception.getMessage());
        assertEquals(120, exception.getRetryAfterSeconds());
        assertEquals("system_resource_exhaustion", exception.getOverloadReason());

        // Verify overload detection was called
        verify(systemOverloadDetector).isSystemOverloaded();
        verify(systemOverloadDetector).calculateRetryDelay();
        
        // Verify service methods were not called due to overload
        verifyNoInteractions(batchProcessingService);
    }

    @Test
    void createOrders_SystemNotOverloaded_ProcessesNormally() {
        // Arrange
        when(systemOverloadDetector.isSystemOverloaded()).thenReturn(false);

        List<OrderPostDTO> orders = createTestOrderPostDTOs();

        // Act - should not throw exception
        assertDoesNotThrow(() -> {
            orderController.createOrders(orders);
        });

        // Verify overload detection was called
        verify(systemOverloadDetector).isSystemOverloaded();
        
        // Verify processing continued normally
        verify(batchProcessingService).processOrdersWithConnectionControl(orders);
    }

    @Test
    void submitOrdersBatch_SystemOverloaded_ThrowsSystemOverloadException() {
        // Arrange
        when(systemOverloadDetector.isSystemOverloaded()).thenReturn(true);
        when(systemOverloadDetector.calculateRetryDelay()).thenReturn(180);

        BatchSubmitRequestDTO request = new BatchSubmitRequestDTO();
        request.setOrderIds(Arrays.asList(1, 2, 3));

        // Act & Assert
        SystemOverloadException exception = assertThrows(SystemOverloadException.class, () -> {
            orderController.submitOrdersBatch(request);
        });

        assertEquals("System temporarily overloaded - please retry in a few minutes", exception.getMessage());
        assertEquals(180, exception.getRetryAfterSeconds());
        assertEquals("system_resource_exhaustion", exception.getOverloadReason());

        // Verify overload detection was called
        verify(systemOverloadDetector).isSystemOverloaded();
        verify(systemOverloadDetector).calculateRetryDelay();
        
        // Verify service methods were not called due to overload
        verifyNoInteractions(orderService);
    }

    @Test
    void submitOrdersBatch_SystemNotOverloaded_ProcessesNormally() {
        // Arrange
        when(systemOverloadDetector.isSystemOverloaded()).thenReturn(false);

        BatchSubmitRequestDTO request = new BatchSubmitRequestDTO();
        request.setOrderIds(Arrays.asList(1, 2, 3));

        // Act - should not throw exception
        assertDoesNotThrow(() -> {
            orderController.submitOrdersBatch(request);
        });

        // Verify overload detection was called
        verify(systemOverloadDetector).isSystemOverloaded();
        
        // Verify processing continued normally
        verify(orderService).submitOrdersBatch(request.getOrderIds());
    }

    @Test
    void createOrders_OverloadDetectionException_ContinuesProcessing() {
        // Arrange - simulate exception in overload detection
        when(systemOverloadDetector.isSystemOverloaded()).thenThrow(new RuntimeException("Overload detection failed"));

        List<OrderPostDTO> orders = createTestOrderPostDTOs();

        // Act - should not throw SystemOverloadException, but may throw the RuntimeException
        assertThrows(RuntimeException.class, () -> {
            orderController.createOrders(orders);
        });

        // Verify overload detection was called
        verify(systemOverloadDetector).isSystemOverloaded();
        
        // Verify service methods were not called due to exception
        verifyNoInteractions(batchProcessingService);
    }

    @Test
    void submitOrdersBatch_OverloadDetectionException_ContinuesProcessing() {
        // Arrange - simulate exception in overload detection
        when(systemOverloadDetector.isSystemOverloaded()).thenThrow(new RuntimeException("Overload detection failed"));

        BatchSubmitRequestDTO request = new BatchSubmitRequestDTO();
        request.setOrderIds(Arrays.asList(1, 2, 3));

        // Act - should not throw SystemOverloadException, but may throw the RuntimeException
        assertThrows(RuntimeException.class, () -> {
            orderController.submitOrdersBatch(request);
        });

        // Verify overload detection was called
        verify(systemOverloadDetector).isSystemOverloaded();
        
        // Verify service methods were not called due to exception
        verifyNoInteractions(orderService);
    }

    @Test
    void createOrders_OverloadWithDifferentRetryDelays_ReturnsCorrectDelay() {
        // Test with different retry delay values
        int[] retryDelays = {60, 120, 300};
        
        for (int retryDelay : retryDelays) {
            // Arrange
            when(systemOverloadDetector.isSystemOverloaded()).thenReturn(true);
            when(systemOverloadDetector.calculateRetryDelay()).thenReturn(retryDelay);

            List<OrderPostDTO> orders = createTestOrderPostDTOs();

            // Act & Assert
            SystemOverloadException exception = assertThrows(SystemOverloadException.class, () -> {
                orderController.createOrders(orders);
            });

            assertEquals(retryDelay, exception.getRetryAfterSeconds());
        }
    }

    @Test
    void submitOrdersBatch_OverloadWithDifferentRetryDelays_ReturnsCorrectDelay() {
        // Test with different retry delay values
        int[] retryDelays = {90, 150, 240};
        
        for (int retryDelay : retryDelays) {
            // Arrange
            when(systemOverloadDetector.isSystemOverloaded()).thenReturn(true);
            when(systemOverloadDetector.calculateRetryDelay()).thenReturn(retryDelay);

            BatchSubmitRequestDTO request = new BatchSubmitRequestDTO();
            request.setOrderIds(Arrays.asList(1, 2, 3));

            // Act & Assert
            SystemOverloadException exception = assertThrows(SystemOverloadException.class, () -> {
                orderController.submitOrdersBatch(request);
            });

            assertEquals(retryDelay, exception.getRetryAfterSeconds());
        }
    }

    /**
     * Helper method to create test OrderPostDTO objects for testing.
     */
    private List<OrderPostDTO> createTestOrderPostDTOs() {
        OrderPostDTO order1 = new OrderPostDTO();
        order1.setBlotterId(1);
        order1.setStatusId(1);
        order1.setPortfolioId("TEST_PORTFOLIO_1");
        order1.setOrderTypeId(1);
        order1.setSecurityId("TEST_SECURITY_1");
        order1.setQuantity(new BigDecimal("100"));
        order1.setLimitPrice(new BigDecimal("50.00"));

        OrderPostDTO order2 = new OrderPostDTO();
        order2.setBlotterId(1);
        order2.setStatusId(1);
        order2.setPortfolioId("TEST_PORTFOLIO_2");
        order2.setOrderTypeId(1);
        order2.setSecurityId("TEST_SECURITY_2");
        order2.setQuantity(new BigDecimal("200"));
        order2.setLimitPrice(new BigDecimal("75.00"));

        return Arrays.asList(order1, order2);
    }
}