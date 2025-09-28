package org.kasbench.globeco_order_service.controller;

import lombok.RequiredArgsConstructor;
import org.kasbench.globeco_order_service.dto.OrderDTO;
import org.kasbench.globeco_order_service.dto.OrderPostDTO;
import org.kasbench.globeco_order_service.dto.OrderWithDetailsDTO;
import org.kasbench.globeco_order_service.dto.OrderListResponseDTO;
import org.kasbench.globeco_order_service.dto.BatchSubmitRequestDTO;
import org.kasbench.globeco_order_service.dto.BatchSubmitResponseDTO;
import org.kasbench.globeco_order_service.service.OrderService;
import org.kasbench.globeco_order_service.service.BatchProcessingService;
import org.kasbench.globeco_order_service.service.SortingSpecification;
import org.kasbench.globeco_order_service.service.FilteringSpecification;
import org.kasbench.globeco_order_service.service.SystemOverloadDetector;
import org.kasbench.globeco_order_service.exception.SystemOverloadException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private static final int MAX_BATCH_SIZE = 1000;
    private static final int MAX_SUBMIT_BATCH_SIZE = 100;
    // private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 1000;
    
    private final OrderService orderService;
    private final BatchProcessingService batchProcessingService;
    private final SystemOverloadDetector systemOverloadDetector;

    /**
     * Get all orders with support for paging, sorting, and filtering.
     * 
     * @param limit Maximum number of results to return (1-1000, default: 50)
     * @param offset Number of results to skip (default: 0)
     * @param sort Comma-separated list of sort fields with optional direction prefix (default: id)
     * @param securityTicker Filter by security ticker (supports comma-separated values)
     * @param portfolioName Filter by portfolio name (supports comma-separated values)
     * @param blotterName Filter by blotter name (supports comma-separated values)
     * @param statusAbbreviation Filter by status abbreviation (supports comma-separated values)
     * @param orderTypeAbbreviation Filter by order type abbreviation (supports comma-separated values)
     * @param orderTimestamp Filter by order timestamp (ISO format)
     * @param request HttpServletRequest for extracting additional filter parameters
     * @return Page of orders with pagination metadata
     */
    @GetMapping("/orders")
    public ResponseEntity<?> getAllOrders(
            @RequestParam(value = "limit", defaultValue = "50") Integer limit,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "security.ticker", required = false) String securityTicker,
            @RequestParam(value = "portfolio.name", required = false) String portfolioName,
            @RequestParam(value = "blotter.name", required = false) String blotterName,
            @RequestParam(value = "status.abbreviation", required = false) String statusAbbreviation,
            @RequestParam(value = "orderType.abbreviation", required = false) String orderTypeAbbreviation,
            @RequestParam(value = "orderTimestamp", required = false) String orderTimestamp,
            HttpServletRequest request) {
        
        try {
            // Validate limit parameter
            if (limit < 1 || limit > MAX_LIMIT) {
                logger.warn("Invalid limit parameter: {}. Must be between 1 and {}", limit, MAX_LIMIT);
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid limit parameter",
                    "message", String.format("Limit must be between 1 and %d, got: %d", MAX_LIMIT, limit)
                ));
            }
            
            // Validate offset parameter
            if (offset < 0) {
                logger.warn("Invalid offset parameter: {}. Must be >= 0", offset);
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid offset parameter", 
                    "message", String.format("Offset must be >= 0, got: %d", offset)
                ));
            }
            
            // Validate sort fields
            try {
                SortingSpecification.validateSortFields(sort);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid sort parameter: {} - {}", sort, e.getMessage());
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid sort parameter",
                    "message", e.getMessage(),
                    "validSortFields", SortingSpecification.getValidSortFields()
                ));
            }
            
            // Build filter parameters map from all request parameters
            Map<String, String> filterParams = new HashMap<>();
            
            // Get all request parameters and filter out non-filter parameters
            Set<String> nonFilterParams = Set.of("limit", "offset", "sort");
            for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
                String paramName = entry.getKey();
                String[] paramValues = entry.getValue();
                
                // Skip non-filter parameters
                if (nonFilterParams.contains(paramName)) {
                    continue;
                }
                
                // For filter parameters, join multiple values with comma
                if (paramValues != null && paramValues.length > 0) {
                    String paramValue = String.join(",", paramValues);
                    if (!paramValue.trim().isEmpty()) {
                        filterParams.put(paramName, paramValue);
                    }
                }
            }
            
            // Validate filter fields
            try {
                FilteringSpecification.validateFilterFields(filterParams);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid filter parameters: {} - {}", filterParams, e.getMessage());
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid filter parameter",
                    "message", e.getMessage(),
                    "validFilterFields", FilteringSpecification.getValidFilterFields()
                ));
            }
            
            // Log the request for debugging
            logger.info("Orders request: limit={}, offset={}, sort={}, filters={}", 
                    limit, offset, sort, filterParams);
            
            // Call service layer with all parameters
            Page<OrderWithDetailsDTO> result = orderService.getAll(limit, offset, sort, filterParams);
            
            // Create response with pagination metadata
            Map<String, Object> response = new HashMap<>();
            response.put("content", result.getContent());
            response.put("pagination", Map.of(
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "currentPage", result.getNumber(),
                "pageSize", result.getSize(),
                "hasNext", result.hasNext(),
                "hasPrevious", result.hasPrevious()
            ));
            
            logger.info("Orders response: totalElements={}, currentPage={}, pageSize={}", 
                    result.getTotalElements(), result.getNumber(), result.getSize());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            // Handle validation errors from service layer
            logger.warn("Validation error in getAllOrders: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation error",
                "message", e.getMessage()
            ));
            
        } catch (Exception e) {
            // Handle unexpected errors
            logger.error("Unexpected error in getAllOrders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error",
                "message", "An unexpected error occurred while processing the request"
            ));
        }
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
            // Check for system overload before processing
            if (systemOverloadDetector.isSystemOverloaded()) {
                int retryDelay = systemOverloadDetector.calculateRetryDelay();
                logger.warn("System overload detected, rejecting batch order request. Retry after {} seconds", retryDelay);
                throw new SystemOverloadException(
                    "System temporarily overloaded - please retry in a few minutes", 
                    retryDelay, 
                    "system_resource_exhaustion"
                );
            }
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
            
            // Process the batch through controlled batch processing service
            OrderListResponseDTO response = batchProcessingService.processOrdersWithConnectionControl(orders);
            
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

    /**
     * Submit multiple orders in batch to the trade service.
     * Processes orders individually (non-atomic batch) and continues processing
     * even if some orders fail.
     * 
     * Returns appropriate HTTP status codes based on processing results:
     * - 200: All orders submitted successfully
     * - 207: Partial success (some orders succeeded, others failed)
     * - 400: Request validation failed
     * - 413: Batch size exceeds maximum allowed (100)
     * - 500: Unexpected server error
     * 
     * @param request The batch submission request containing order IDs
     * @return BatchSubmitResponseDTO containing results for each order
     */
    @PostMapping("/orders/batch/submit")
    public ResponseEntity<BatchSubmitResponseDTO> submitOrdersBatch(@Valid @RequestBody BatchSubmitRequestDTO request) {
        logger.info("Received batch order submission request with {} order IDs", 
                request != null && request.getOrderIds() != null ? request.getOrderIds().size() : 0);
        
        try {
            // Check for system overload before processing
            if (systemOverloadDetector.isSystemOverloaded()) {
                int retryDelay = systemOverloadDetector.calculateRetryDelay();
                logger.warn("System overload detected, rejecting batch submission request. Retry after {} seconds", retryDelay);
                throw new SystemOverloadException(
                    "System temporarily overloaded - please retry in a few minutes", 
                    retryDelay, 
                    "system_resource_exhaustion"
                );
            }
            // Validate request is not null
            if (request == null || request.getOrderIds() == null) {
                logger.warn("Batch submission request rejected: null request or order IDs");
                BatchSubmitResponseDTO errorResponse = BatchSubmitResponseDTO.validationFailure(
                    "Request body is required and must contain orderIds array");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Validate batch is not empty
            if (request.getOrderIds().isEmpty()) {
                logger.warn("Batch submission request rejected: empty order IDs list");
                BatchSubmitResponseDTO errorResponse = BatchSubmitResponseDTO.validationFailure(
                    "Order IDs list cannot be empty");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Validate batch size limit
            if (request.getOrderIds().size() > MAX_SUBMIT_BATCH_SIZE) {
                logger.warn("Batch submission request rejected: size {} exceeds maximum {}", 
                        request.getOrderIds().size(), MAX_SUBMIT_BATCH_SIZE);
                BatchSubmitResponseDTO errorResponse = BatchSubmitResponseDTO.validationFailure(
                    String.format("Batch size %d exceeds maximum allowed size of %d", 
                            request.getOrderIds().size(), MAX_SUBMIT_BATCH_SIZE));
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorResponse);
            }
            
            // Validate order IDs are not null
            if (request.getOrderIds().stream().anyMatch(java.util.Objects::isNull)) {
                logger.warn("Batch submission request rejected: contains null order IDs");
                BatchSubmitResponseDTO errorResponse = BatchSubmitResponseDTO.validationFailure(
                    "Order IDs cannot contain null values");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Process the batch through service layer
            BatchSubmitResponseDTO response = orderService.submitOrdersBatch(request.getOrderIds());
            
            // Determine appropriate HTTP status code based on results
            HttpStatus statusCode = determineBatchSubmitHttpStatus(response);
            
            logger.info("Batch submission processing completed: status={}, total={}, successful={}, failed={}", 
                    response.getStatus(), response.getTotalRequested(), 
                    response.getSuccessful(), response.getFailed());
            
            return ResponseEntity.status(statusCode).body(response);
            
        } catch (Exception e) {
            logger.error("Unexpected error processing batch submission request", e);
            BatchSubmitResponseDTO errorResponse = BatchSubmitResponseDTO.validationFailure(
                "Internal server error processing batch submission request: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Determine appropriate HTTP status code based on batch submission results.
     * 
     * @param response The batch submission results
     * @return Appropriate HttpStatus
     */
    private HttpStatus determineBatchSubmitHttpStatus(BatchSubmitResponseDTO response) {
        if (response == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        
        switch (response.getStatus()) {
            case "SUCCESS":
                return HttpStatus.OK; // 200 - All orders submitted successfully
                
            case "PARTIAL": 
                return HttpStatus.MULTI_STATUS; // 207 - Some succeeded, some failed
                
            case "FAILURE":
                // Distinguish between validation failures and processing failures
                if (response.getTotalRequested() == 0) {
                    // This was a validation failure before processing
                    return HttpStatus.BAD_REQUEST; // 400 - Request validation failed
                } else {
                    // All orders failed during processing
                    return HttpStatus.MULTI_STATUS; // 207 - Processing attempted but all failed
                }
                
            default:
                logger.warn("Unknown batch submission response status: {}", response.getStatus());
                return HttpStatus.INTERNAL_SERVER_ERROR; // 500 - Unknown status
        }
    }
} 