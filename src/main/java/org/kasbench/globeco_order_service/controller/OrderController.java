package org.kasbench.globeco_order_service.controller;

import lombok.RequiredArgsConstructor;
import org.kasbench.globeco_order_service.dto.OrderDTO;
import org.kasbench.globeco_order_service.dto.OrderPostDTO;
import org.kasbench.globeco_order_service.dto.OrderWithDetailsDTO;
import org.kasbench.globeco_order_service.dto.OrderListResponseDTO;
import org.kasbench.globeco_order_service.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private static final int MAX_BATCH_SIZE = 1000;
    
    private final OrderService orderService;

    @GetMapping("/orders")
    public List<OrderWithDetailsDTO> getAllOrders() {
        return orderService.getAll();
    }

    @GetMapping("/order/{id}")
    public ResponseEntity<OrderWithDetailsDTO> getOrderById(@PathVariable Integer id) {
        return orderService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create orders in batch. Supports processing up to 1000 orders at once.
     * Returns appropriate HTTP status codes based on processing results:
     * - 200: All orders processed successfully
     * - 207: Partial success (some orders succeeded, others failed)
     * - 400: Request validation failed
     * - 413: Batch size exceeds maximum allowed (1000)
     * - 500: Unexpected server error
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderListResponseDTO> createOrders(@Valid @RequestBody List<OrderPostDTO> orders) {
        logger.info("Received batch order request with {} orders", orders != null ? orders.size() : 0);
        
        try {
            // Validate request is not null
            if (orders == null) {
                logger.warn("Batch order request rejected: null order list");
                OrderListResponseDTO errorResponse = OrderListResponseDTO.validationFailure(
                    "Request body is required and must contain an array of orders");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Validate batch size limit
            if (orders.size() > MAX_BATCH_SIZE) {
                logger.warn("Batch order request rejected: size {} exceeds maximum {}", orders.size(), MAX_BATCH_SIZE);
                OrderListResponseDTO errorResponse = OrderListResponseDTO.validationFailure(
                    String.format("Batch size %d exceeds maximum allowed size of %d", orders.size(), MAX_BATCH_SIZE));
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorResponse);
            }
            
            // Process the batch through service layer
            OrderListResponseDTO response = orderService.processBatchOrders(orders);
            
            // Determine appropriate HTTP status code based on results
            HttpStatus statusCode = determineHttpStatus(response);
            
            logger.info("Batch order processing completed: status={}, total={}, successful={}, failed={}", 
                    response.getStatus(), response.getTotalReceived(), 
                    response.getSuccessful(), response.getFailed());
            
            return ResponseEntity.status(statusCode).body(response);
            
        } catch (Exception e) {
            logger.error("Unexpected error processing batch order request", e);
            OrderListResponseDTO errorResponse = OrderListResponseDTO.validationFailure(
                "Internal server error processing batch request: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Determine appropriate HTTP status code based on batch processing results.
     * 
     * @param response The processing results
     * @return Appropriate HttpStatus
     */
    private HttpStatus determineHttpStatus(OrderListResponseDTO response) {
        if (response == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        
        switch (response.getStatus()) {
            case "SUCCESS":
                return HttpStatus.OK; // 200 - All orders processed successfully
                
            case "PARTIAL": 
                return HttpStatus.MULTI_STATUS; // 207 - Some succeeded, some failed
                
            case "FAILURE":
                // Distinguish between validation failures and processing failures
                if (response.getTotalReceived() == 0) {
                    // This was a validation failure before processing
                    return HttpStatus.BAD_REQUEST; // 400 - Request validation failed
                } else {
                    // All orders failed during processing
                    return HttpStatus.MULTI_STATUS; // 207 - Processing attempted but all failed
                }
                
            default:
                logger.warn("Unknown response status: {}", response.getStatus());
                return HttpStatus.INTERNAL_SERVER_ERROR; // 500 - Unknown status
        }
    }

    @PutMapping("/order/{id}")
    public ResponseEntity<OrderWithDetailsDTO> updateOrder(@PathVariable Integer id, @RequestBody OrderDTO dto) {
        return orderService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/order/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Integer id, @RequestParam Integer version) {
        boolean deleted = orderService.delete(id, version);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.status(409).build(); // Conflict if version mismatch
        }
    }

    @PostMapping("/orders/{id}/submit")
    public ResponseEntity<?> submitOrder(@PathVariable Integer id) {
        OrderDTO orderDTO = orderService.submitOrder(id);
        if (orderDTO != null) {
            return ResponseEntity.ok(orderDTO);
        } else {
            return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("status", "not submitted"));
        }
    }
} 