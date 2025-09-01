package org.kasbench.globeco_order_service.service;

import org.kasbench.globeco_order_service.entity.Order;
import org.kasbench.globeco_order_service.entity.Status;
import org.kasbench.globeco_order_service.entity.Blotter;
import org.kasbench.globeco_order_service.entity.OrderType;
import org.kasbench.globeco_order_service.dto.TradeOrderPostDTO;
import org.kasbench.globeco_order_service.dto.BlotterDTO;
import org.kasbench.globeco_order_service.dto.StatusDTO;
import org.kasbench.globeco_order_service.dto.OrderTypeDTO;
import org.kasbench.globeco_order_service.dto.OrderListResponseDTO;
import org.kasbench.globeco_order_service.dto.OrderPostResponseDTO;
import org.kasbench.globeco_order_service.dto.BatchSubmitRequestDTO;
import org.kasbench.globeco_order_service.dto.BatchSubmitResponseDTO;
import org.kasbench.globeco_order_service.dto.OrderSubmitResultDTO;
import org.kasbench.globeco_order_service.dto.BulkTradeOrderRequestDTO;
import org.kasbench.globeco_order_service.dto.BulkTradeOrderResponseDTO;
import org.kasbench.globeco_order_service.dto.TradeOrderResultDTO;
import org.kasbench.globeco_order_service.repository.OrderRepository;
import org.kasbench.globeco_order_service.repository.StatusRepository;
import org.kasbench.globeco_order_service.repository.BlotterRepository;
import org.kasbench.globeco_order_service.repository.OrderTypeRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.kasbench.globeco_order_service.dto.OrderWithDetailsDTO;
import org.kasbench.globeco_order_service.dto.OrderPostDTO;
import org.kasbench.globeco_order_service.dto.OrderDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;



import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.math.BigDecimal;
import org.kasbench.globeco_order_service.service.SecurityCacheService;
import org.kasbench.globeco_order_service.service.PortfolioCacheService;
import org.kasbench.globeco_order_service.service.PortfolioServiceClient;
import org.kasbench.globeco_order_service.service.SecurityServiceClient;
import org.kasbench.globeco_order_service.dto.SecurityDTO;
import org.kasbench.globeco_order_service.dto.PortfolioDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_BATCH_SIZE = 1000;
    private static final int MAX_SUBMIT_BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final StatusRepository statusRepository;
    private final BlotterRepository blotterRepository;
    private final OrderTypeRepository orderTypeRepository;
    private final RestTemplate restTemplate;
    private final SecurityCacheService securityCacheService;
    private final PortfolioCacheService portfolioCacheService;
    private final PortfolioServiceClient portfolioServiceClient;
    private final SecurityServiceClient securityServiceClient;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final String tradeServiceUrl;
    private final int tradeServiceTimeout;



    public OrderService(
            OrderRepository orderRepository,
            StatusRepository statusRepository,
            BlotterRepository blotterRepository,
            OrderTypeRepository orderTypeRepository,
            RestTemplate restTemplate,
            SecurityCacheService securityCacheService,
            PortfolioCacheService portfolioCacheService,
            PortfolioServiceClient portfolioServiceClient,
            SecurityServiceClient securityServiceClient,
            PlatformTransactionManager transactionManager,
            @Value("${trade.service.url:http://globeco-trade-service:8082}") String tradeServiceUrl,
            @Value("${trade.service.timeout:5000}") int tradeServiceTimeout) {
        this.orderRepository = orderRepository;
        this.statusRepository = statusRepository;
        this.blotterRepository = blotterRepository;
        this.orderTypeRepository = orderTypeRepository;
        this.restTemplate = restTemplate;
        this.securityCacheService = securityCacheService;
        this.portfolioCacheService = portfolioCacheService;
        this.portfolioServiceClient = portfolioServiceClient;
        this.securityServiceClient = securityServiceClient;
        this.tradeServiceUrl = tradeServiceUrl;
        this.tradeServiceTimeout = tradeServiceTimeout;

        // Create transaction templates for precise control
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setTimeout(5); // 5 second timeout

        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
        this.readOnlyTransactionTemplate.setTimeout(3); // 3 second timeout for reads
    }

    @Transactional
    public OrderDTO submitOrder(Integer id) {
        // Load order and validate status in one query
        Order order = orderRepository.findById(id).orElseThrow();
        if (!order.getStatus().getAbbreviation().equals("NEW")) {
            return null;
        }

        // Prepare trade order data
        TradeOrderPostDTO tradeOrder = TradeOrderPostDTO.builder()
                .orderId(id)
                .portfolioId(order.getPortfolioId())
                .orderType(order.getOrderType().getAbbreviation())
                .securityId(order.getSecurityId())
                .quantity(order.getQuantity())
                .limitPrice(order.getLimitPrice())
                .build();

        // Call external service (this should be quick)
        Integer tradeOrderId = callTradeService(order);
        if (tradeOrderId == null) {
            return null;
        }

        // Update order status efficiently
        updateOrderAfterSubmission(order, tradeOrderId);

        // Return the updated order
        Order updatedOrder = orderRepository.findById(id).orElseThrow();
        return toOrderDTO(updatedOrder);
    }

    /**
     * Submit multiple orders in batch to the trade service using bulk submission.
     * This method uses a single bulk API call to the trade service for improved performance,
     * replacing the previous individual order processing approach.
     * 
     * @param orderIds List of order IDs to submit
     * @return BatchSubmitResponseDTO containing results for each order
     */
    public BatchSubmitResponseDTO submitOrdersBatch(List<Integer> orderIds) {
        long overallStartTime = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        
        // Validate input parameters first
        if (orderIds == null) {
            logger.error("BULK_SUBMISSION: Order IDs list is null, thread={}", threadName);
            return BatchSubmitResponseDTO.validationFailure("Order IDs cannot be null");
        }
        
        logger.info("BULK_SUBMISSION: Starting bulk order submission for {} orders, thread={}, timestamp={}",
                orderIds.size(), threadName, overallStartTime);

        if (orderIds.isEmpty()) {
            logger.warn("BULK_SUBMISSION: Empty order list provided, thread={}", threadName);
            return BatchSubmitResponseDTO.validationFailure("No orders provided for submission");
        }

        // Validate batch size with detailed logging
        if (orderIds.size() > MAX_SUBMIT_BATCH_SIZE) {
            String errorMessage = String.format("Batch size %d exceeds maximum allowed size of %d",
                    orderIds.size(), MAX_SUBMIT_BATCH_SIZE);
            logger.warn("BULK_SUBMISSION: Batch size validation failed - {}, thread={}", errorMessage, threadName);
            return BatchSubmitResponseDTO.validationFailure(errorMessage);
        }

        // Log performance baseline
        logger.info("BULK_SUBMISSION_PERFORMANCE: Processing {} orders (limit: {}), thread={}",
                orderIds.size(), MAX_SUBMIT_BATCH_SIZE, threadName);

        try {
            // 1. Load and validate orders in batch
            long loadStartTime = System.currentTimeMillis();
            logger.debug("BULK_SUBMISSION: Step 1 - Loading and validating orders, thread={}", threadName);
            
            List<Order> validOrders = loadAndValidateOrdersForBulkSubmission(orderIds);
            
            long loadDuration = System.currentTimeMillis() - loadStartTime;
            logger.info("BULK_SUBMISSION_PERFORMANCE: Order loading completed in {}ms - {} valid out of {} requested, thread={}",
                    loadDuration, validOrders.size(), orderIds.size(), threadName);
            
            if (validOrders.isEmpty()) {
                logger.warn("BULK_SUBMISSION: No valid orders found from {} requested orders, thread={}",
                        orderIds.size(), threadName);
                return BatchSubmitResponseDTO.validationFailure("No valid orders found for submission");
            }

            // Log validation results
            int invalidCount = orderIds.size() - validOrders.size();
            if (invalidCount > 0) {
                logger.warn("BULK_SUBMISSION: {} orders were invalid and excluded from bulk submission, thread={}",
                        invalidCount, threadName);
            }

            // 2. Build bulk request
            long buildStartTime = System.currentTimeMillis();
            logger.debug("BULK_SUBMISSION: Step 2 - Building bulk request for {} valid orders, thread={}",
                    validOrders.size(), threadName);
            
            BulkTradeOrderRequestDTO bulkRequest = buildBulkTradeOrderRequest(validOrders);
            
            long buildDuration = System.currentTimeMillis() - buildStartTime;
            logger.info("BULK_SUBMISSION_PERFORMANCE: Bulk request building completed in {}ms, thread={}",
                    buildDuration, threadName);

            // 3. Call trade service bulk endpoint
            long tradeServiceStartTime = System.currentTimeMillis();
            logger.debug("BULK_SUBMISSION: Step 3 - Calling trade service bulk endpoint, thread={}", threadName);
            
            BulkTradeOrderResponseDTO tradeServiceResponse = callTradeServiceBulk(bulkRequest);
            
            long tradeServiceDuration = System.currentTimeMillis() - tradeServiceStartTime;
            logger.info("BULK_SUBMISSION_PERFORMANCE: Trade service call completed in {}ms - {} successful, {} failed, thread={}",
                    tradeServiceDuration, tradeServiceResponse.getSuccessful(), tradeServiceResponse.getFailed(), threadName);

            // 4. Update order statuses in batch
            long updateStartTime = System.currentTimeMillis();
            logger.debug("BULK_SUBMISSION: Step 4 - Updating order statuses from bulk response, thread={}", threadName);
            
            updateOrderStatusesFromBulkResponse(validOrders, tradeServiceResponse);
            
            long updateDuration = System.currentTimeMillis() - updateStartTime;
            logger.info("BULK_SUBMISSION_PERFORMANCE: Status update completed in {}ms, thread={}",
                    updateDuration, threadName);

            // 5. Transform response to match existing API contract
            long transformStartTime = System.currentTimeMillis();
            logger.debug("BULK_SUBMISSION: Step 5 - Transforming response to order service format, thread={}", threadName);
            
            BatchSubmitResponseDTO response = transformBulkResponseToOrderServiceFormat(tradeServiceResponse, orderIds);
            
            long transformDuration = System.currentTimeMillis() - transformStartTime;
            logger.info("BULK_SUBMISSION_PERFORMANCE: Response transformation completed in {}ms, thread={}",
                    transformDuration, threadName);

            // Log comprehensive completion metrics
            long overallDuration = System.currentTimeMillis() - overallStartTime;
            double successRate = response.getTotalRequested() > 0 ? 
                    (double) response.getSuccessful() / response.getTotalRequested() * 100 : 0;
            
            logger.info("BULK_SUBMISSION: Completed successfully in {}ms - {} successful, {} failed out of {} total (success_rate: {:.2f}%), thread={}",
                    overallDuration, response.getSuccessful(), response.getFailed(), response.getTotalRequested(),
                    successRate, threadName);

            // Log detailed performance breakdown
            logger.info("BULK_SUBMISSION_PERFORMANCE_BREAKDOWN: load={}ms, build={}ms, trade_service={}ms, update={}ms, transform={}ms, total={}ms, thread={}",
                    loadDuration, buildDuration, tradeServiceDuration, updateDuration, transformDuration, overallDuration, threadName);

            // Log performance per order metrics
            if (response.getSuccessful() > 0) {
                double avgTimePerOrder = (double) overallDuration / response.getSuccessful();
                logger.info("BULK_SUBMISSION_PERFORMANCE: Average processing time per successful order: {:.2f}ms, thread={}",
                        avgTimePerOrder, threadName);
            }

            return response;

        } catch (IllegalArgumentException e) {
            long overallDuration = System.currentTimeMillis() - overallStartTime;
            logger.error("BULK_SUBMISSION: Validation error after {}ms for {} orders, thread={}, error={}",
                    overallDuration, orderIds.size(), threadName, e.getMessage());
            
            return BatchSubmitResponseDTO.validationFailure(e.getMessage());

        } catch (RuntimeException e) {
            long overallDuration = System.currentTimeMillis() - overallStartTime;
            logger.error("BULK_SUBMISSION: Runtime error after {}ms for {} orders, thread={}, error={}",
                    overallDuration, orderIds.size(), threadName, e.getMessage(), e);
            
            return BatchSubmitResponseDTO.builder()
                    .status("FAILURE")
                    .message("Bulk submission failed: " + e.getMessage())
                    .totalRequested(orderIds.size())
                    .successful(0)
                    .failed(orderIds.size())
                    .results(orderIds.stream()
                            .map(orderId -> OrderSubmitResultDTO.failure(orderId, 
                                    "Bulk submission failed: " + e.getMessage(), 
                                    orderIds.indexOf(orderId)))
                            .toList())
                    .build();

        } catch (Exception e) {
            long overallDuration = System.currentTimeMillis() - overallStartTime;
            logger.error("BULK_SUBMISSION: Unexpected error after {}ms for {} orders, thread={}, error={}",
                    overallDuration, orderIds.size(), threadName, e.getMessage(), e);
            
            return BatchSubmitResponseDTO.builder()
                    .status("FAILURE")
                    .message("Unexpected error during bulk submission: " + e.getMessage())
                    .totalRequested(orderIds.size())
                    .successful(0)
                    .failed(orderIds.size())
                    .results(orderIds.stream()
                            .map(orderId -> OrderSubmitResultDTO.failure(orderId, 
                                    "Unexpected error: " + e.getMessage(), 
                                    orderIds.indexOf(orderId)))
                            .toList())
                    .build();
        }
    }













    /**
     * Call the trade service to submit an order.
     * Reuses the existing trade service integration logic.
     * 
     * @param order The order to submit to the trade service
     * @return The trade order ID if successful, null if failed
     */
    private Integer callTradeService(Order order) {
        String threadName = Thread.currentThread().getName();
        long callStart = System.currentTimeMillis();

        try {
            TradeOrderPostDTO tradeOrder = TradeOrderPostDTO.builder()
                    .orderId(order.getId())
                    .portfolioId(order.getPortfolioId())
                    .orderType(order.getOrderType().getAbbreviation())
                    .securityId(order.getSecurityId())
                    .quantity(order.getQuantity())
                    .limitPrice(order.getLimitPrice())
                    .build();

            String fullUrl = tradeServiceUrl + "/api/v1/tradeOrders";
            logger.info(
                    "DUPLICATE_TRACKING: Sending HTTP POST to trade service for orderId={}, url={}, thread={}, timestamp={}",
                    order.getId(), fullUrl, threadName, callStart);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    fullUrl,
                    new HttpEntity<>(tradeOrder),
                    String.class);

            long callEnd = System.currentTimeMillis();
            logger.info(
                    "DUPLICATE_TRACKING: Trade service HTTP response received for orderId={}, status={}, duration={}ms, thread={}",
                    order.getId(), response.getStatusCode(), (callEnd - callStart), threadName);

            if (response.getStatusCode() == HttpStatus.CREATED) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode node = mapper.readTree(response.getBody());
                    if (node.has("id") && node.get("id").isInt()) {
                        Integer tradeOrderId = node.get("id").asInt();
                        logger.info(
                                "DUPLICATE_TRACKING: Trade service SUCCESS for orderId={}, returned tradeOrderId={}, thread={}",
                                order.getId(), tradeOrderId, threadName);
                        return tradeOrderId;
                    }
                } catch (Exception e) {
                    logger.error("Failed to parse trade service response for order {}: {}",
                            order.getId(), e.getMessage());
                }
            } else {
                logger.warn("Trade service returned non-success status {} for order {}",
                        response.getStatusCode(), order.getId());
            }
        } catch (Exception e) {
            long callEnd = System.currentTimeMillis();
            logger.error(
                    "DUPLICATE_TRACKING: Trade service call FAILED for orderId={}, duration={}ms, thread={}, error: {}",
                    order.getId(), (callEnd - callStart), threadName, e.getMessage());
        }

        return null;
    }

    /**
     * Update order status to SENT and set tradeOrderId after successful trade
     * service submission.
     * Uses cached status lookup to minimize database queries.
     * 
     * @param order        The order to update
     * @param tradeOrderId The trade order ID returned from the trade service
     */
    private void updateOrderAfterSubmission(Order order, Integer tradeOrderId) {
        // Use cached validation service to get SENT status efficiently
        Status sentStatus = getSentStatus();

        Order updatedOrder = Order.builder()
                .id(order.getId())
                .blotter(order.getBlotter())
                .status(sentStatus)
                .portfolioId(order.getPortfolioId())
                .orderType(order.getOrderType())
                .securityId(order.getSecurityId())
                .quantity(order.getQuantity())
                .limitPrice(order.getLimitPrice())
                .tradeOrderId(tradeOrderId)
                .orderTimestamp(order.getOrderTimestamp())
                .version(order.getVersion())
                .build();

        orderRepository.save(updatedOrder);
        logger.debug("Updated order {} with tradeOrderId {} and SENT status",
                order.getId(), tradeOrderId);
    }

    // Cache for SENT status to avoid repeated database lookups
    private volatile Status cachedSentStatus;
    private final Object statusCacheLock = new Object();

    /**
     * Get SENT status with caching to reduce database queries.
     */
    private Status getSentStatus() {
        if (cachedSentStatus == null) {
            synchronized (statusCacheLock) {
                if (cachedSentStatus == null) {
                    cachedSentStatus = statusRepository.findAll().stream()
                            .filter(s -> "SENT".equals(s.getAbbreviation()))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("SENT status not found"));
                }
            }
        }
        return cachedSentStatus;
    }



    /**
     * Load and validate orders for bulk submission.
     * Efficiently loads orders in batch and filters them for bulk submission eligibility.
     * 
     * @param orderIds List of order IDs to load and validate
     * @return List of valid orders ready for bulk submission
     */
    public List<Order> loadAndValidateOrdersForBulkSubmission(List<Integer> orderIds) {
        long startTime = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        
        if (orderIds == null || orderIds.isEmpty()) {
            logger.warn("BULK_VALIDATION: Empty or null order IDs list provided, thread={}", threadName);
            return new ArrayList<>();
        }
        
        logger.info("BULK_VALIDATION: Loading and validating {} orders for bulk submission, thread={}, timestamp={}",
                orderIds.size(), threadName, startTime);

        return readOnlyTransactionTemplate.execute(status -> {
            try {
                // Batch load all orders using findAllById for efficiency
                long dbStartTime = System.currentTimeMillis();
                List<Order> allOrders = orderRepository.findAllById(orderIds);
                long dbDuration = System.currentTimeMillis() - dbStartTime;
                
                logger.info("BULK_VALIDATION: Loaded {} orders from database out of {} requested in {}ms, thread={}",
                        allOrders.size(), orderIds.size(), dbDuration, threadName);

                // Track missing orders for detailed logging
                if (allOrders.size() < orderIds.size()) {
                    Set<Integer> foundIds = allOrders.stream()
                            .map(Order::getId)
                            .collect(HashSet::new, Set::add, Set::addAll);
                    
                    List<Integer> missingIds = orderIds.stream()
                            .filter(id -> !foundIds.contains(id))
                            .toList();
                    
                    logger.warn("BULK_VALIDATION: {} orders not found in database: {}, thread={}",
                            missingIds.size(), missingIds, threadName);
                }

                // Filter orders for bulk submission eligibility with detailed tracking
                long validationStartTime = System.currentTimeMillis();
                List<Order> validOrders = new ArrayList<>();
                List<String> validationErrors = new ArrayList<>();
                
                for (Order order : allOrders) {
                    try {
                        if (isOrderValidForBulkSubmission(order)) {
                            validOrders.add(order);
                            logger.debug("BULK_VALIDATION: Order {} is valid for bulk submission, thread={}",
                                    order.getId(), threadName);
                        } else {
                            String reason = getOrderValidationFailureReason(order);
                            validationErrors.add(String.format("Order %s: %s", order.getId(), reason));
                            logger.debug("BULK_VALIDATION: Order {} is invalid for bulk submission: {}, thread={}",
                                    order.getId(), reason, threadName);
                        }
                    } catch (Exception e) {
                        String errorMsg = String.format("Order %s: validation error - %s", 
                                order != null ? order.getId() : "null", e.getMessage());
                        validationErrors.add(errorMsg);
                        logger.error("BULK_VALIDATION: Error validating order {}, thread={}, error={}",
                                order != null ? order.getId() : "null", threadName, e.getMessage(), e);
                    }
                }
                
                long validationDuration = System.currentTimeMillis() - validationStartTime;
                long totalDuration = System.currentTimeMillis() - startTime;
                
                logger.info("BULK_VALIDATION: Validated {} orders out of {} loaded in {}ms (total: {}ms), thread={}",
                        validOrders.size(), allOrders.size(), validationDuration, totalDuration, threadName);

                // Log validation errors for monitoring
                if (!validationErrors.isEmpty()) {
                    logger.warn("BULK_VALIDATION: {} validation errors encountered, thread={}", 
                            validationErrors.size(), threadName);
                    for (String error : validationErrors) {
                        logger.debug("BULK_VALIDATION: Validation error: {}, thread={}", error, threadName);
                    }
                }

                // Log performance metrics
                if (allOrders.size() > 0) {
                    double avgValidationTimePerOrder = (double) validationDuration / allOrders.size();
                    logger.info("BULK_VALIDATION_PERFORMANCE: Validated {} orders in {}ms (avg {:.2f}ms per order), success_rate={:.2f}%, thread={}",
                            allOrders.size(), validationDuration, avgValidationTimePerOrder,
                            (double) validOrders.size() / allOrders.size() * 100, threadName);
                }

                return validOrders;

            } catch (Exception e) {
                long totalDuration = System.currentTimeMillis() - startTime;
                logger.error("BULK_VALIDATION: Failed to load and validate orders after {}ms, thread={}, error={}",
                        totalDuration, threadName, e.getMessage(), e);
                status.setRollbackOnly();
                throw new RuntimeException("Failed to load and validate orders for bulk submission", e);
            }
        });
    }

    /**
     * Get detailed reason why an order failed validation for bulk submission.
     * Used for comprehensive error logging and debugging.
     * 
     * @param order The order that failed validation
     * @return String describing the validation failure reason
     */
    private String getOrderValidationFailureReason(Order order) {
        if (order == null) {
            return "Order is null";
        }

        if (order.getStatus() == null || !"NEW".equals(order.getStatus().getAbbreviation())) {
            return String.format("Invalid status '%s' (must be 'NEW')", 
                    order.getStatus() != null ? order.getStatus().getAbbreviation() : "null");
        }

        if (order.getTradeOrderId() != null) {
            return String.format("Already processed (tradeOrderId: %s)", order.getTradeOrderId());
        }

        if (!hasRequiredFieldsForTradeService(order)) {
            return "Missing required fields for trade service";
        }

        return "Unknown validation failure";
    }

    /**
     * Validate if an order is eligible for bulk submission.
     * Checks status, required fields, and processing state.
     * 
     * @param order The order to validate
     * @return true if order is valid for bulk submission, false otherwise
     */
    private boolean isOrderValidForBulkSubmission(Order order) {
        if (order == null) {
            logger.debug("Order is null - invalid for bulk submission");
            return false;
        }

        // Check if order is in NEW status
        if (order.getStatus() == null || !"NEW".equals(order.getStatus().getAbbreviation())) {
            logger.debug("Order {} has invalid status '{}' - must be 'NEW' for bulk submission", 
                    order.getId(), order.getStatus() != null ? order.getStatus().getAbbreviation() : "null");
            return false;
        }

        // Check if order is already processed (has tradeOrderId)
        if (order.getTradeOrderId() != null) {
            logger.debug("Order {} already has tradeOrderId {} - already processed", 
                    order.getId(), order.getTradeOrderId());
            return false;
        }

        // Validate required fields for trade service submission
        if (!hasRequiredFieldsForTradeService(order)) {
            logger.debug("Order {} missing required fields for trade service submission", order.getId());
            return false;
        }

        logger.debug("Order {} is valid for bulk submission", order.getId());
        return true;
    }

    /**
     * Validate that an order has all required fields for trade service submission.
     * Checks all mandatory fields needed by the trade service bulk API.
     * 
     * @param order The order to validate
     * @return true if all required fields are present and valid, false otherwise
     */
    private boolean hasRequiredFieldsForTradeService(Order order) {
        if (order == null) {
            return false;
        }

        // Check portfolioId
        if (order.getPortfolioId() == null || order.getPortfolioId().trim().isEmpty()) {
            logger.debug("Order {} missing portfolioId", order.getId());
            return false;
        }

        // Check orderType
        if (order.getOrderType() == null || order.getOrderType().getAbbreviation() == null || 
            order.getOrderType().getAbbreviation().trim().isEmpty()) {
            logger.debug("Order {} missing or invalid orderType", order.getId());
            return false;
        }

        // Check securityId
        if (order.getSecurityId() == null || order.getSecurityId().trim().isEmpty()) {
            logger.debug("Order {} missing securityId", order.getId());
            return false;
        }

        // Check quantity (must be positive)
        if (order.getQuantity() == null || order.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            logger.debug("Order {} has invalid quantity: {}", order.getId(), order.getQuantity());
            return false;
        }

        // Check limitPrice (must be positive)
        if (order.getLimitPrice() == null || order.getLimitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            logger.debug("Order {} has invalid limitPrice: {}", order.getId(), order.getLimitPrice());
            return false;
        }

        // Check orderTimestamp
        if (order.getOrderTimestamp() == null) {
            logger.debug("Order {} missing orderTimestamp", order.getId());
            return false;
        }

        // Check blotter (required for blotterId)
        if (order.getBlotter() == null || order.getBlotter().getId() == null) {
            logger.debug("Order {} missing blotter or blotter ID", order.getId());
            return false;
        }

        return true;
    }



    /**
     * Build bulk trade order request from a list of validated orders.
     * Converts Order entities to the trade service bulk API format with comprehensive validation.
     * 
     * @param orders List of validated orders to include in the bulk request
     * @return BulkTradeOrderRequestDTO formatted for the trade service bulk API
     * @throws IllegalArgumentException if orders list is null, empty, or contains invalid orders
     */
    public BulkTradeOrderRequestDTO buildBulkTradeOrderRequest(List<Order> orders) {
        long startTime = System.currentTimeMillis();
        
        if (orders == null || orders.isEmpty()) {
            logger.error("Cannot build bulk request: orders list is null or empty");
            throw new IllegalArgumentException("Orders list cannot be null or empty");
        }

        logger.info("Building bulk trade order request for {} orders", orders.size());

        try {
            List<TradeOrderPostDTO> tradeOrders = new ArrayList<>();
            
            for (Order order : orders) {
                validateOrderForBulkRequest(order);
                
                TradeOrderPostDTO tradeOrder = TradeOrderPostDTO.builder()
                        .orderId(order.getId())
                        .portfolioId(order.getPortfolioId())
                        .orderType(order.getOrderType().getAbbreviation())
                        .securityId(order.getSecurityId())
                        .quantity(order.getQuantity())
                        .limitPrice(order.getLimitPrice())
                        .tradeTimestamp(order.getOrderTimestamp())
                        .blotterId(order.getBlotter() != null ? order.getBlotter().getId() : null)
                        .build();
                
                tradeOrders.add(tradeOrder);
                logger.debug("Added order {} to bulk request: portfolioId={}, orderType={}, securityId={}, quantity={}, limitPrice={}",
                        order.getId(), order.getPortfolioId(), order.getOrderType().getAbbreviation(),
                        order.getSecurityId(), order.getQuantity(), order.getLimitPrice());
            }

            BulkTradeOrderRequestDTO bulkRequest = BulkTradeOrderRequestDTO.of(tradeOrders);
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Successfully built bulk request with {} orders in {}ms", orders.size(), duration);
            
            return bulkRequest;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Failed to build bulk trade order request for {} orders after {}ms: {}", 
                    orders.size(), duration, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Validate an order for inclusion in a bulk request.
     * Performs comprehensive validation of all required fields.
     * 
     * @param order The order to validate
     * @throws IllegalArgumentException if the order is invalid for bulk submission
     */
    private void validateOrderForBulkRequest(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        if (order.getId() == null) {
            throw new IllegalArgumentException("Order ID is required");
        }

        if (order.getPortfolioId() == null || order.getPortfolioId().trim().isEmpty()) {
            throw new IllegalArgumentException("Portfolio ID is required for order " + order.getId());
        }

        if (order.getOrderType() == null || order.getOrderType().getAbbreviation() == null || 
            order.getOrderType().getAbbreviation().trim().isEmpty()) {
            throw new IllegalArgumentException("Order type is required for order " + order.getId());
        }

        if (order.getSecurityId() == null || order.getSecurityId().trim().isEmpty()) {
            throw new IllegalArgumentException("Security ID is required for order " + order.getId());
        }

        if (order.getQuantity() == null || order.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive for order " + order.getId());
        }

        if (order.getLimitPrice() == null || order.getLimitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Limit price must be positive for order " + order.getId());
        }

        if (order.getOrderTimestamp() == null) {
            throw new IllegalArgumentException("Order timestamp is required for order " + order.getId());
        }
    }

    /**
     * Call the trade service bulk endpoint with comprehensive error handling and logging.
     * Handles various error scenarios including HTTP errors, network issues, and service failures.
     * 
     * @param bulkRequest The bulk request to send to the trade service
     * @return BulkTradeOrderResponseDTO response from the trade service
     * @throws IllegalArgumentException if the bulk request is null or empty
     * @throws RuntimeException for various trade service errors with detailed error information
     */
    public BulkTradeOrderResponseDTO callTradeServiceBulk(BulkTradeOrderRequestDTO bulkRequest) {
        long startTime = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        
        // Validate input
        if (bulkRequest == null || bulkRequest.getTradeOrders() == null || bulkRequest.getTradeOrders().isEmpty()) {
            logger.error("Cannot call trade service: bulk request is null or empty");
            throw new IllegalArgumentException("Bulk request cannot be null or empty");
        }

        int orderCount = bulkRequest.getOrderCount();
        String fullUrl = tradeServiceUrl + "/api/v1/tradeOrders/bulk";
        
        logger.info("BULK_SUBMISSION: Sending HTTP POST to trade service bulk endpoint for {} orders, url={}, thread={}, timestamp={}",
                orderCount, fullUrl, threadName, startTime);

        try {
            ResponseEntity<BulkTradeOrderResponseDTO> response = restTemplate.postForEntity(
                    fullUrl,
                    new HttpEntity<>(bulkRequest),
                    BulkTradeOrderResponseDTO.class);

            long duration = System.currentTimeMillis() - startTime;
            HttpStatus statusCode = HttpStatus.valueOf(response.getStatusCode().value());
            
            logger.info("BULK_SUBMISSION: Trade service HTTP response received for {} orders, status={}, duration={}ms, thread={}",
                    orderCount, statusCode, duration, threadName);

            // Handle successful response (HTTP 201)
            if (statusCode == HttpStatus.CREATED) {
                BulkTradeOrderResponseDTO responseBody = response.getBody();
                
                if (responseBody == null) {
                    logger.error("BULK_SUBMISSION: Trade service returned null response body for {} orders, thread={}",
                            orderCount, threadName);
                    throw new RuntimeException("Trade service returned null response body");
                }

                logger.info("BULK_SUBMISSION: Trade service SUCCESS for {} orders, successful={}, failed={}, thread={}",
                        orderCount, responseBody.getSuccessful(), responseBody.getFailed(), threadName);
                
                // Log performance metrics
                if (responseBody.getSuccessful() > 0) {
                    double avgTimePerOrder = (double) duration / responseBody.getSuccessful();
                    logger.info("BULK_SUBMISSION_PERFORMANCE: Processed {} orders in {}ms (avg {:.2f}ms per order), success_rate={:.2f}%",
                            responseBody.getSuccessful(), duration, avgTimePerOrder, responseBody.getSuccessRate() * 100);
                }

                return responseBody;
            }
            // Handle HTTP 400 Bad Request
            else if (statusCode == HttpStatus.BAD_REQUEST) {
                BulkTradeOrderResponseDTO errorResponse = response.getBody();
                String errorMessage = errorResponse != null ? errorResponse.getMessage() : "Bad Request";
                
                logger.error("BULK_SUBMISSION: Trade service rejected bulk request with HTTP 400 for {} orders: {}, thread={}",
                        orderCount, errorMessage, threadName);
                
                throw new RuntimeException(String.format(
                        "Trade service rejected bulk request with HTTP 400: %s", errorMessage));
            }
            // Handle HTTP 500 Internal Server Error
            else if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR) {
                logger.error("BULK_SUBMISSION: Trade service internal error during bulk submission for {} orders, status={}, thread={}",
                        orderCount, statusCode, threadName);
                
                throw new RuntimeException("Trade service internal error during bulk submission");
            }
            // Handle other HTTP status codes
            else {
                logger.error("BULK_SUBMISSION: Trade service returned unexpected status {} for {} orders, thread={}",
                        statusCode, orderCount, threadName);
                
                throw new RuntimeException(String.format(
                        "Trade service returned unexpected status: %s", statusCode));
            }

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            long duration = System.currentTimeMillis() - startTime;
            String responseBody = e.getResponseBodyAsString();
            
            logger.error("BULK_SUBMISSION: Trade service HTTP client error for {} orders, status={}, duration={}ms, thread={}, response_body={}, error={}",
                    orderCount, e.getStatusCode(), duration, threadName, responseBody, e.getMessage(), e);
            
            throw new RuntimeException(String.format(
                    "Trade service HTTP client error: %s %s - %s", 
                    e.getStatusCode(), e.getStatusText(), responseBody), e);

        } catch (org.springframework.web.client.HttpServerErrorException e) {
            long duration = System.currentTimeMillis() - startTime;
            String responseBody = e.getResponseBodyAsString();
            
            logger.error("BULK_SUBMISSION: Trade service server error for {} orders, status={}, duration={}ms, thread={}, response_body={}, error={}",
                    orderCount, e.getStatusCode(), duration, threadName, responseBody, e.getMessage(), e);
            
            throw new RuntimeException(String.format(
                    "Trade service server error: %s %s - %s", 
                    e.getStatusCode(), e.getStatusText(), responseBody), e);

        } catch (org.springframework.web.client.ResourceAccessException e) {
            long duration = System.currentTimeMillis() - startTime;
            
            logger.error("BULK_SUBMISSION: Trade service connectivity error for {} orders, duration={}ms, thread={}, error={}",
                    orderCount, duration, threadName, e.getMessage(), e);
            
            throw new RuntimeException(String.format(
                    "Trade service connectivity error: %s", e.getMessage()), e);

        } catch (org.springframework.web.client.RestClientException e) {
            long duration = System.currentTimeMillis() - startTime;
            
            logger.error("BULK_SUBMISSION: Trade service REST client error for {} orders, duration={}ms, thread={}, error={}",
                    orderCount, duration, threadName, e.getMessage(), e);
            
            throw new RuntimeException(String.format(
                    "Trade service REST client error: %s", e.getMessage()), e);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            logger.error("BULK_SUBMISSION: Unexpected error during trade service bulk call for {} orders, duration={}ms, thread={}, error={}",
                    orderCount, duration, threadName, e.getMessage(), e);
            
            throw new RuntimeException(String.format(
                    "Unexpected error during trade service bulk call: %s", e.getMessage()), e);
        }
    }

    /**
     * Transform bulk trade service response to order service format.
     * Maps the trade service bulk response to the existing BatchSubmitResponseDTO format
     * to maintain API contract compatibility.
     * 
     * @param bulkResponse The bulk response from the trade service
     * @param originalOrderIds The original order IDs that were requested for submission
     * @return BatchSubmitResponseDTO in the expected order service format
     */
    public BatchSubmitResponseDTO transformBulkResponseToOrderServiceFormat(
            BulkTradeOrderResponseDTO bulkResponse, List<Integer> originalOrderIds) {
        long startTime = System.currentTimeMillis();
        
        if (bulkResponse == null) {
            logger.error("Cannot transform response: bulk response is null");
            throw new IllegalArgumentException("Bulk response cannot be null");
        }

        if (originalOrderIds == null || originalOrderIds.isEmpty()) {
            logger.error("Cannot transform response: original order IDs list is null or empty");
            throw new IllegalArgumentException("Original order IDs cannot be null or empty");
        }

        logger.info("Transforming bulk response to order service format: {} total requested, {} successful, {} failed",
                bulkResponse.getTotalRequested(), bulkResponse.getSuccessful(), bulkResponse.getFailed());

        try {
            List<OrderSubmitResultDTO> results = new ArrayList<>();
            
            // Create a map of request index to result for efficient lookup
            Map<Integer, TradeOrderResultDTO> resultMap = new HashMap<>();
            if (bulkResponse.getResults() != null) {
                for (TradeOrderResultDTO result : bulkResponse.getResults()) {
                    if (result.getRequestIndex() != null) {
                        resultMap.put(result.getRequestIndex(), result);
                    }
                }
            }

            // Transform each original order ID to a result
            for (int i = 0; i < originalOrderIds.size(); i++) {
                Integer orderId = originalOrderIds.get(i);
                TradeOrderResultDTO tradeResult = resultMap.get(i);
                
                if (tradeResult != null && tradeResult.isSuccess()) {
                    // Successful submission
                    Integer tradeOrderId = tradeResult.getTradeOrderId();
                    results.add(OrderSubmitResultDTO.success(orderId, tradeOrderId, i));
                    
                    logger.debug("Transformed successful result for order {}: tradeOrderId={}, requestIndex={}",
                            orderId, tradeOrderId, i);
                } else {
                    // Failed submission or no result found
                    String errorMessage = tradeResult != null ? tradeResult.getMessage() : 
                            "No result found in bulk response";
                    results.add(OrderSubmitResultDTO.failure(orderId, errorMessage, i));
                    
                    logger.debug("Transformed failed result for order {}: error={}, requestIndex={}",
                            orderId, errorMessage, i);
                }
            }

            // Determine overall status and message
            String status;
            String message;
            int successful = bulkResponse.getSuccessful();
            int failed = bulkResponse.getFailed();
            int total = originalOrderIds.size();

            if (successful == total) {
                status = "SUCCESS";
                message = String.format("All %d orders submitted successfully", total);
            } else if (successful == 0) {
                status = "FAILURE";
                message = String.format("All %d orders failed to submit", total);
            } else {
                status = "PARTIAL";
                message = String.format("%d of %d orders submitted successfully", successful, total);
            }

            BatchSubmitResponseDTO response = BatchSubmitResponseDTO.builder()
                    .status(status)
                    .message(message)
                    .totalRequested(total)
                    .successful(successful)
                    .failed(failed)
                    .results(results)
                    .build();

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Successfully transformed bulk response in {}ms: status={}, successful={}, failed={}",
                    duration, status, successful, failed);

            return response;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Failed to transform bulk response after {}ms: {}", duration, e.getMessage(), e);
            throw new RuntimeException("Failed to transform bulk response", e);
        }
    }

    /**
     * Update order statuses from bulk response using efficient batch operations.
     * Handles partial success scenarios where some orders succeed and others fail.
     * Uses simplified transaction management for improved performance.
     * 
     * @param orders List of orders that were submitted in the bulk request
     * @param bulkResponse The bulk response from the trade service
     */
    public void updateOrderStatusesFromBulkResponse(List<Order> orders, BulkTradeOrderResponseDTO bulkResponse) {
        long startTime = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        
        // Validate input parameters with detailed logging
        if (orders == null || orders.isEmpty()) {
            logger.error("BULK_STATUS_UPDATE: Cannot update order statuses - orders list is null or empty, thread={}",
                    threadName);
            throw new IllegalArgumentException("Orders list cannot be null or empty");
        }

        if (bulkResponse == null) {
            logger.error("BULK_STATUS_UPDATE: Cannot update order statuses - bulk response is null, thread={}",
                    threadName);
            throw new IllegalArgumentException("Bulk response cannot be null");
        }

        int orderCount = orders.size();
        int resultCount = bulkResponse.getResults() != null ? bulkResponse.getResults().size() : 0;
        
        logger.info("BULK_STATUS_UPDATE: Starting status update for {} orders with {} results, thread={}, timestamp={}",
                orderCount, resultCount, threadName, startTime);

        // Validate response consistency
        if (bulkResponse.getResults() == null || bulkResponse.getResults().isEmpty()) {
            logger.warn("BULK_STATUS_UPDATE: Bulk response contains no results for {} orders, thread={}",
                    orderCount, threadName);
        }

        transactionTemplate.execute(status -> {
            try {
                // Create maps for efficient lookups with error tracking
                Map<Integer, Order> orderMap = new HashMap<>();
                for (Order order : orders) {
                    if (order != null && order.getId() != null) {
                        orderMap.put(order.getId(), order);
                    } else {
                        logger.warn("BULK_STATUS_UPDATE: Skipping null order or order with null ID, thread={}",
                                threadName);
                    }
                }
                
                Map<Integer, TradeOrderResultDTO> resultMap = new HashMap<>();
                if (bulkResponse.getResults() != null) {
                    for (TradeOrderResultDTO result : bulkResponse.getResults()) {
                        if (result.getRequestIndex() != null && result.getRequestIndex() < orders.size()) {
                            Order order = orders.get(result.getRequestIndex());
                            if (order != null && order.getId() != null) {
                                resultMap.put(order.getId(), result);
                                logger.debug("BULK_STATUS_UPDATE: Mapped result for order {} at index {}: success={}, tradeOrderId={}, thread={}",
                                        order.getId(), result.getRequestIndex(), result.isSuccess(), 
                                        result.getTradeOrderId(), threadName);
                            } else {
                                logger.warn("BULK_STATUS_UPDATE: Invalid order at index {}, thread={}",
                                        result.getRequestIndex(), threadName);
                            }
                        } else {
                            logger.warn("BULK_STATUS_UPDATE: Invalid result with requestIndex={} for {} orders, thread={}",
                                    result.getRequestIndex(), orders.size(), threadName);
                        }
                    }
                }

                // Get SENT status once for all successful updates
                Status sentStatus;
                try {
                    sentStatus = getSentStatus();
                    logger.debug("BULK_STATUS_UPDATE: Retrieved SENT status for batch update, thread={}", threadName);
                } catch (Exception e) {
                    logger.error("BULK_STATUS_UPDATE: Failed to retrieve SENT status, thread={}, error={}",
                            threadName, e.getMessage(), e);
                    throw new RuntimeException("Failed to retrieve SENT status", e);
                }
                
                // Track successful and failed updates with detailed logging
                List<Order> successfulOrders = new ArrayList<>();
                List<Order> failedOrders = new ArrayList<>();
                List<String> updateErrors = new ArrayList<>();
                
                // Process each order based on its result
                for (Order order : orders) {
                    try {
                        TradeOrderResultDTO result = resultMap.get(order.getId());
                        
                        if (result != null && result.isSuccess() && result.getTradeOrderId() != null) {
                            // Order was successfully processed by trade service
                            Order updatedOrder = Order.builder()
                                    .id(order.getId())
                                    .blotter(order.getBlotter())
                                    .status(sentStatus)
                                    .portfolioId(order.getPortfolioId())
                                    .orderType(order.getOrderType())
                                    .securityId(order.getSecurityId())
                                    .quantity(order.getQuantity())
                                    .limitPrice(order.getLimitPrice())
                                    .tradeOrderId(result.getTradeOrderId())
                                    .orderTimestamp(order.getOrderTimestamp())
                                    .version(order.getVersion())
                                    .build();
                            
                            successfulOrders.add(updatedOrder);
                            logger.debug("BULK_STATUS_UPDATE: Prepared order {} for successful update with tradeOrderId {}, thread={}",
                                    order.getId(), result.getTradeOrderId(), threadName);
                        } else {
                            // Order failed or no result found - leave in NEW status
                            failedOrders.add(order);
                            String failureReason = result != null ? result.getMessage() : "No result found in bulk response";
                            logger.debug("BULK_STATUS_UPDATE: Order {} failed in bulk submission: {}, thread={}",
                                    order.getId(), failureReason, threadName);
                        }
                    } catch (Exception e) {
                        String errorMsg = String.format("Failed to process order %s: %s", order.getId(), e.getMessage());
                        updateErrors.add(errorMsg);
                        failedOrders.add(order);
                        logger.error("BULK_STATUS_UPDATE: Error processing order {}, thread={}, error={}",
                                order.getId(), threadName, e.getMessage(), e);
                    }
                }
                
                // Log any processing errors
                if (!updateErrors.isEmpty()) {
                    logger.warn("BULK_STATUS_UPDATE: Encountered {} processing errors during status update, thread={}",
                            updateErrors.size(), threadName);
                    for (String error : updateErrors) {
                        logger.warn("BULK_STATUS_UPDATE: Processing error: {}, thread={}", error, threadName);
                    }
                }
                
                // Perform batch updates for successful orders with error handling
                if (!successfulOrders.isEmpty()) {
                    try {
                        logger.info("BULK_STATUS_UPDATE: Performing batch database update for {} successful orders, thread={}",
                                successfulOrders.size(), threadName);
                        
                        long dbStartTime = System.currentTimeMillis();
                        orderRepository.saveAll(successfulOrders);
                        long dbDuration = System.currentTimeMillis() - dbStartTime;
                        
                        logger.info("BULK_STATUS_UPDATE: Successfully updated {} orders to SENT status with trade order IDs in {}ms, thread={}",
                                successfulOrders.size(), dbDuration, threadName);
                        
                        // Log performance metrics for database operations
                        if (successfulOrders.size() > 0) {
                            double avgDbTimePerOrder = (double) dbDuration / successfulOrders.size();
                            logger.info("BULK_STATUS_UPDATE_PERFORMANCE: Database batch update: {} orders in {}ms (avg {:.2f}ms per order), thread={}",
                                    successfulOrders.size(), dbDuration, avgDbTimePerOrder, threadName);
                        }
                        
                    } catch (Exception e) {
                        logger.error("BULK_STATUS_UPDATE: Failed to perform batch database update for {} orders, thread={}, error={}",
                                successfulOrders.size(), threadName, e.getMessage(), e);
                        throw new RuntimeException("Batch database update failed", e);
                    }
                } else {
                    logger.warn("BULK_STATUS_UPDATE: No successful orders to update in database, thread={}", threadName);
                }
                
                // Log comprehensive summary of batch update operation
                long totalDuration = System.currentTimeMillis() - startTime;
                logger.info("BULK_STATUS_UPDATE: Completed status update in {}ms - {} successful, {} failed out of {} total orders, thread={}",
                        totalDuration, successfulOrders.size(), failedOrders.size(), orderCount, threadName);
                
                // Log success rate metrics
                if (orderCount > 0) {
                    double successRate = (double) successfulOrders.size() / orderCount * 100;
                    logger.info("BULK_STATUS_UPDATE_METRICS: Success rate: {:.2f}% ({}/{}), processing_time={}ms, thread={}",
                            successRate, successfulOrders.size(), orderCount, totalDuration, threadName);
                }
                
                // Log failed orders for monitoring
                if (!failedOrders.isEmpty()) {
                    List<Integer> failedOrderIds = failedOrders.stream()
                            .map(Order::getId)
                            .toList();
                    logger.warn("BULK_STATUS_UPDATE: Failed order IDs: {}, thread={}", failedOrderIds, threadName);
                }
                
                return null;
                
            } catch (Exception e) {
                long totalDuration = System.currentTimeMillis() - startTime;
                logger.error("BULK_STATUS_UPDATE: Failed to update order statuses from bulk response after {}ms, thread={}, error={}",
                        totalDuration, threadName, e.getMessage(), e);
                status.setRollbackOnly();
                throw new RuntimeException("Bulk status update failed", e);
            }
        });
    }

    // Complete mapping from Order to OrderWithDetailsDTO
    private OrderWithDetailsDTO toDto(Order order) {
        if (order == null)
            return null;

        // Fetch security information from cache
        SecurityDTO security = null;
        if (order.getSecurityId() != null) {
            try {
                security = securityCacheService.getSecurityBySecurityId(order.getSecurityId());
                if (security == null) {
                    // Create a fallback SecurityDTO with just the ID if service is unavailable
                    security = SecurityDTO.builder()
                            .securityId(order.getSecurityId())
                            .ticker(null) // Will be null if service is unavailable
                            .build();
                    logger.debug("Security service unavailable for securityId: {}, using fallback",
                            order.getSecurityId());
                }
            } catch (Exception e) {
                logger.warn("Failed to fetch security data for securityId: {} - {}", order.getSecurityId(),
                        e.getMessage());
                // Create fallback SecurityDTO
                security = SecurityDTO.builder()
                        .securityId(order.getSecurityId())
                        .ticker(null)
                        .build();
            }
        }

        // Fetch portfolio information from cache
        PortfolioDTO portfolio = null;
        if (order.getPortfolioId() != null) {
            try {
                portfolio = portfolioCacheService.getPortfolioByPortfolioId(order.getPortfolioId());
                if (portfolio == null) {
                    // Create a fallback PortfolioDTO with just the ID if service is unavailable
                    portfolio = PortfolioDTO.builder()
                            .portfolioId(order.getPortfolioId())
                            .name(null) // Will be null if service is unavailable
                            .build();
                    logger.debug("Portfolio service unavailable for portfolioId: {}, using fallback",
                            order.getPortfolioId());
                }
            } catch (Exception e) {
                logger.warn("Failed to fetch portfolio data for portfolioId: {} - {}", order.getPortfolioId(),
                        e.getMessage());
                // Create fallback PortfolioDTO
                portfolio = PortfolioDTO.builder()
                        .portfolioId(order.getPortfolioId())
                        .name(null)
                        .build();
            }
        }

        return OrderWithDetailsDTO.builder()
                .id(order.getId())
                .blotter(toBlotterDTO(order.getBlotter()))
                .status(toStatusDTO(order.getStatus()))
                .portfolio(portfolio)
                .orderType(toOrderTypeDTO(order.getOrderType()))
                .security(security)
                .quantity(order.getQuantity())
                .limitPrice(order.getLimitPrice())
                .tradeOrderId(order.getTradeOrderId())
                .orderTimestamp(order.getOrderTimestamp())
                .version(order.getVersion())
                .build();
    }

    // Helper method to convert Blotter entity to BlotterDTO
    private BlotterDTO toBlotterDTO(Blotter blotter) {
        if (blotter == null)
            return null;
        return BlotterDTO.builder()
                .id(blotter.getId())
                .name(blotter.getName())
                .version(blotter.getVersion())
                .build();
    }

    // Helper method to convert Status entity to StatusDTO
    private StatusDTO toStatusDTO(Status status) {
        if (status == null)
            return null;
        return StatusDTO.builder()
                .id(status.getId())
                .abbreviation(status.getAbbreviation())
                .description(status.getDescription())
                .version(status.getVersion())
                .build();
    }

    // Helper method to convert OrderType entity to OrderTypeDTO
    private OrderTypeDTO toOrderTypeDTO(OrderType orderType) {
        if (orderType == null)
            return null;
        return OrderTypeDTO.builder()
                .id(orderType.getId())
                .abbreviation(orderType.getAbbreviation())
                .description(orderType.getDescription())
                .version(orderType.getVersion())
                .build();
    }

    public List<OrderWithDetailsDTO> getAll() {
        List<Order> orders = orderRepository.findAll();
        return orders.stream().map(this::toDto).toList();
    }

    /**
     * Get all orders with paging, sorting, and filtering support.
     * This method properly resolves external service identifiers (security.ticker,
     * portfolio.name)
     * to their corresponding IDs before applying database filters.
     * 
     * @param limit        Maximum number of results to return
     * @param offset       Number of results to skip
     * @param sort         Comma-separated list of sort fields with optional
     *                     direction prefix
     * @param filterParams Map of filter field names to comma-separated values
     * @return Page of orders with pagination metadata
     */
    public Page<OrderWithDetailsDTO> getAll(Integer limit, Integer offset, String sort,
            Map<String, String> filterParams) {
        logger.debug("Getting orders with limit={}, offset={}, sort={}, filters={}",
                limit, offset, sort, filterParams);

        // Create Pageable with sorting
        Sort sortSpec = SortingSpecification.parseSort(sort);
        Pageable pageable = PageRequest.of(offset / limit, limit, sortSpec);

        // Resolve external service identifiers to IDs
        Map<String, String> resolvedSecurityIds = resolveSecurityTickers(filterParams);
        Map<String, String> resolvedPortfolioIds = resolvePortfolioNames(filterParams);

        // Create filtering specification with resolved IDs
        Specification<Order> filterSpec = FilteringSpecification.createFilterSpecification(
                filterParams, resolvedSecurityIds, resolvedPortfolioIds);

        // Execute query with paging, sorting, and filtering
        Page<Order> orderPage;
        if (filterSpec != null) {
            orderPage = orderRepository.findAll(filterSpec, pageable);
        } else {
            orderPage = orderRepository.findAll(pageable);
        }

        logger.debug("Found {} orders (total: {}, page: {}/{})",
                orderPage.getNumberOfElements(), orderPage.getTotalElements(),
                orderPage.getNumber() + 1, orderPage.getTotalPages());

        // Convert to DTOs with external service data
        return orderPage.map(this::toDto);
    }

    /**
     * Resolve security tickers to security IDs using the security service v2 API.
     * 
     * @param filterParams Map of filter parameters
     * @return Map of ticker symbols to security IDs for found securities
     */
    private Map<String, String> resolveSecurityTickers(Map<String, String> filterParams) {
        Set<String> tickers = FilteringSpecification.extractSecurityTickers(filterParams);
        Map<String, String> resolvedIds = new HashMap<>();

        if (tickers.isEmpty()) {
            return resolvedIds; // No security tickers to resolve
        }

        logger.debug("Resolving security tickers to IDs: {}", tickers);

        try {
            // Convert Set to List for the service call
            List<String> tickersList = new ArrayList<>(tickers);

            // Call the security service to resolve tickers to IDs
            resolvedIds = securityServiceClient.searchSecuritiesByTickers(tickersList);

            if (resolvedIds.isEmpty()) {
                logger.info("No security tickers could be resolved to IDs. Tickers requested: {}", tickers);
            } else {
                logger.info("Successfully resolved {} of {} security tickers to IDs",
                        resolvedIds.size(), tickers.size());

                // Log which tickers were not resolved
                Set<String> unresolvedTickers = new HashSet<>(tickers);
                unresolvedTickers.removeAll(resolvedIds.keySet());
                if (!unresolvedTickers.isEmpty()) {
                    logger.info("Unresolved security tickers (will be ignored in filtering): {}", unresolvedTickers);
                }
            }

        } catch (SecurityServiceClient.SecurityServiceException e) {
            logger.error("Security service error while resolving tickers {}: {}", tickers, e.getMessage());
            // Return empty map to skip filtering rather than failing the entire request
            logger.warn("Security ticker filtering will be skipped due to service error");

        } catch (Exception e) {
            logger.error("Unexpected error while resolving security tickers {}: {}", tickers, e.getMessage(), e);
            // Return empty map to skip filtering rather than failing the entire request
            logger.warn("Security ticker filtering will be skipped due to unexpected error");
        }

        return resolvedIds;
    }

    /**
     * Resolve portfolio names to portfolio IDs using the portfolio service.
     * 
     * CURRENT LIMITATION: The portfolio service only supports lookup by
     * portfolioId,
     * not by name. This filtering feature cannot work until the portfolio service
     * provides an endpoint to search portfolios by name (e.g., GET
     * /api/v1/portfolios?name=MyPortfolio).
     * 
     * @param filterParams Map of filter parameters
     * @return Empty map (portfolio name filtering is not currently supported)
     */
    private Map<String, String> resolvePortfolioNames(Map<String, String> filterParams) {
        Set<String> names = FilteringSpecification.extractPortfolioNames(filterParams);
        Map<String, String> resolvedIds = new HashMap<>();

        if (names.isEmpty()) {
            return resolvedIds; // No portfolio names to resolve
        }

        logger.debug("Resolving portfolio names to IDs: {}", names);

        try {
            // Convert Set to List for the service call
            List<String> namesList = new ArrayList<>(names);

            // Call the portfolio service to resolve names to IDs
            resolvedIds = portfolioServiceClient.searchPortfoliosByNames(namesList);

            if (resolvedIds.isEmpty()) {
                logger.info("No portfolio names could be resolved to IDs. Names requested: {}", names);
            } else {
                logger.info("Successfully resolved {} of {} portfolio names to IDs",
                        resolvedIds.size(), names.size());

                // Log which names were not resolved
                Set<String> unresolvedNames = new HashSet<>(names);
                unresolvedNames.removeAll(resolvedIds.keySet());
                if (!unresolvedNames.isEmpty()) {
                    logger.info("Unresolved portfolio names (will be ignored in filtering): {}", unresolvedNames);
                }
            }

        } catch (PortfolioServiceClient.PortfolioServiceException e) {
            logger.error("Portfolio service error while resolving names {}: {}", names, e.getMessage());
            // Return empty map to skip filtering rather than failing the entire request
            logger.warn("Portfolio name filtering will be skipped due to service error");

        } catch (Exception e) {
            logger.error("Unexpected error while resolving portfolio names {}: {}", names, e.getMessage(), e);
            // Return empty map to skip filtering rather than failing the entire request
            logger.warn("Portfolio name filtering will be skipped due to unexpected error");
        }

        return resolvedIds;
    }

    public Optional<OrderWithDetailsDTO> getById(Integer id) {
        return orderRepository.findById(id).map(this::toDto);
    }

    public OrderWithDetailsDTO create(OrderPostDTO dto) {
        try {
            Order order = new Order();

            // Fetch and validate blotter
            Blotter blotter = blotterRepository.findById(dto.getBlotterId()).orElse(null);
            if (blotter == null) {
                logger.info("Failed to create order: Blotter with ID {} not found", dto.getBlotterId());
                return null;
            }
            order.setBlotter(blotter);

            // Fetch and validate status
            Status status = statusRepository.findById(dto.getStatusId()).orElse(null);
            if (status == null) {
                logger.info("Failed to create order: Status with ID {} not found", dto.getStatusId());
                return null;
            }
            order.setStatus(status);

            // Fetch and validate order type
            OrderType orderType = orderTypeRepository.findById(dto.getOrderTypeId()).orElse(null);
            if (orderType == null) {
                logger.info("Failed to create order: OrderType with ID {} not found", dto.getOrderTypeId());
                return null;
            }
            order.setOrderType(orderType);

            // Set remaining fields
            order.setPortfolioId(dto.getPortfolioId());
            order.setSecurityId(dto.getSecurityId());
            order.setQuantity(dto.getQuantity());
            order.setLimitPrice(dto.getLimitPrice());
            order.setTradeOrderId(dto.getTradeOrderId());
            order.setOrderTimestamp(dto.getOrderTimestamp());
            order.setVersion(dto.getVersion());

            Order saved = orderRepository.save(order);
            logger.debug("Order created successfully with ID {}", saved.getId());
            return toDto(saved);

        } catch (Exception e) {
            logger.info("Exception during order creation: {}", e.getMessage(), e);
            return null;
        }
    }

    public Optional<OrderWithDetailsDTO> update(Integer id, OrderDTO dto) {
        return orderRepository.findById(id).map(order -> {
            order.setBlotter(blotterRepository.findById(dto.getBlotterId()).orElse(null));
            order.setStatus(statusRepository.findById(dto.getStatusId()).orElse(null));
            order.setPortfolioId(dto.getPortfolioId());
            order.setOrderType(orderTypeRepository.findById(dto.getOrderTypeId()).orElse(null));
            order.setSecurityId(dto.getSecurityId());
            order.setQuantity(dto.getQuantity());
            order.setLimitPrice(dto.getLimitPrice());
            order.setTradeOrderId(dto.getTradeOrderId());
            order.setOrderTimestamp(dto.getOrderTimestamp());
            order.setVersion(dto.getVersion());
            Order saved = orderRepository.save(order);
            return toDto(saved);
        });
    }

    public boolean delete(Integer id, Integer version) {
        if (orderRepository.existsById(id)) {
            orderRepository.deleteById(id);
            return true;
        }
        return false;
    }

    // Add a mapping method from Order to OrderDTO
    private OrderDTO toOrderDTO(Order order) {
        if (order == null)
            return null;
        return OrderDTO.builder()
                .id(order.getId())
                .blotterId(order.getBlotter() != null ? order.getBlotter().getId() : null)
                .statusId(order.getStatus() != null ? order.getStatus().getId() : null)
                .portfolioId(order.getPortfolioId())
                .orderTypeId(order.getOrderType() != null ? order.getOrderType().getId() : null)
                .securityId(order.getSecurityId())
                .quantity(order.getQuantity())
                .limitPrice(order.getLimitPrice())
                .tradeOrderId(order.getTradeOrderId())
                .orderTimestamp(order.getOrderTimestamp())
                .version(order.getVersion())
                .build();
    }

    /**
     * Process a batch of orders with comprehensive error handling and validation.
     * This method processes each order individually, continuing even if some orders
     * fail.
     * Each order is processed in its own transaction to avoid holding connections
     * for extended periods.
     * 
     * @param orders List of OrderPostDTO to process (max 1000)
     * @return OrderListResponseDTO containing results for all orders
     */
    public OrderListResponseDTO processBatchOrders(List<OrderPostDTO> orders) {
        logger.info("Starting batch order processing for {} orders", orders.size());
        long startTime = System.currentTimeMillis();

        // Validate batch size
        if (orders.size() > MAX_BATCH_SIZE) {
            String errorMessage = String.format("Batch size %d exceeds maximum allowed size of %d",
                    orders.size(), MAX_BATCH_SIZE);
            logger.warn("Batch processing rejected: {}", errorMessage);
            return OrderListResponseDTO.validationFailure(errorMessage);
        }

        // Handle empty batch
        if (orders.isEmpty()) {
            logger.warn("Batch processing rejected: empty order list");
            return OrderListResponseDTO.validationFailure("No orders provided for processing");
        }

        List<OrderPostResponseDTO> orderResults = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        // Track failure patterns for better diagnostics
        int validationFailures = 0;
        int referenceFailures = 0;
        int creationFailures = 0;
        int exceptionFailures = 0;

        // Process each order individually in separate transactions
        for (int i = 0; i < orders.size(); i++) {
            OrderPostDTO orderDto = orders.get(i);
            OrderPostResponseDTO result = processIndividualOrderInTransaction(orderDto, i);
            orderResults.add(result);

            if (result.isSuccess()) {
                successCount++;
            } else {
                failureCount++;

                // Categorize failure types for reporting
                String errorMessage = result.getMessage();
                if (errorMessage != null) {
                    if (errorMessage.contains("is required") ||
                            errorMessage.contains("must be positive") ||
                            errorMessage.contains("data is required")) {
                        validationFailures++;
                    } else if (errorMessage.contains("not found")) {
                        referenceFailures++;
                    } else if (errorMessage.contains("Failed to create order")) {
                        creationFailures++;
                    } else if (errorMessage.contains("Internal error") ||
                            errorMessage.contains("Unexpected error")) {
                        exceptionFailures++;
                    }
                }
            }
        }

        long endTime = System.currentTimeMillis();
        long processingTime = endTime - startTime;

        // Log batch processing metrics with failure breakdown
        logger.info("Batch processing completed: {} total, {} successful, {} failed, {}ms processing time",
                orders.size(), successCount, failureCount, processingTime);

        // Log failure breakdown if there were failures
        if (failureCount > 0) {
            logger.info(
                    "Failure breakdown: {} validation errors, {} reference errors, {} creation errors, {} exceptions",
                    validationFailures, referenceFailures, creationFailures, exceptionFailures);

            // Log first few failure examples for diagnostics
            int exampleCount = Math.min(3, failureCount);
            logger.info("Sample failure messages from first {} failed orders:", exampleCount);
            for (int i = 0; i < orderResults.size() && exampleCount > 0; i++) {
                OrderPostResponseDTO result = orderResults.get(i);
                if (!result.isSuccess()) {
                    logger.info("  Index {}: {}", result.getRequestIndex(), result.getMessage());
                    exampleCount--;
                }
            }
        }

        // Return appropriate response based on results
        return OrderListResponseDTO.fromResults(orderResults);
    }

    /**
     * Process a single order within a batch context with its own transaction.
     * This method ensures each order is processed in a separate transaction to
     * avoid
     * holding database connections for extended periods during batch processing.
     * 
     * @param orderDto     The order to process
     * @param requestIndex The index of this order in the original batch request
     * @return OrderPostResponseDTO containing the result
     */
    @Transactional
    public OrderPostResponseDTO processIndividualOrderInTransaction(OrderPostDTO orderDto, int requestIndex) {
        return processIndividualOrder(orderDto, requestIndex);
    }

    /**
     * Process a single order within a batch context.
     * This method handles all individual order validation and processing logic.
     * 
     * @param orderDto     The order to process
     * @param requestIndex The index of this order in the original batch request
     * @return OrderPostResponseDTO containing the result
     */
    private OrderPostResponseDTO processIndividualOrder(OrderPostDTO orderDto, int requestIndex) {
        try {
            // Validate required fields
            String validationError = validateOrderPostDTO(orderDto);
            if (validationError != null) {
                logger.info("Order validation failed at index {}: {}", requestIndex, validationError);
                return OrderPostResponseDTO.failure(validationError, requestIndex);
            }

            // Validate foreign key references exist
            String referenceError = validateOrderReferences(orderDto);
            if (referenceError != null) {
                logger.info("Order reference validation failed at index {}: {}", requestIndex, referenceError);
                return OrderPostResponseDTO.failure(referenceError, requestIndex);
            }

            // Create the order using existing logic
            OrderWithDetailsDTO createdOrder = create(orderDto);
            if (createdOrder == null) {
                logger.info("Order creation returned null at index {}: Failed to create order - unknown error",
                        requestIndex);
                return OrderPostResponseDTO.failure("Failed to create order: unknown error", requestIndex);
            }

            logger.debug("Order created successfully at index {} with ID {}", requestIndex, createdOrder.getId());
            return OrderPostResponseDTO.success(createdOrder, createdOrder.getId().longValue(), requestIndex);

        } catch (Exception e) {
            logger.info("Unexpected error processing order at index {}: {}", requestIndex, e.getMessage(), e);
            return OrderPostResponseDTO.failure(
                    "Internal error processing order: " + e.getMessage(), requestIndex);
        }
    }

    /**
     * Validate basic required fields in OrderPostDTO.
     * 
     * @param dto The order DTO to validate
     * @return Error message if validation fails, null if valid
     */
    private String validateOrderPostDTO(OrderPostDTO dto) {
        if (dto == null) {
            return "Order data is required";
        }

        if (dto.getBlotterId() == null) {
            return "Blotter ID is required";
        }

        if (dto.getStatusId() == null) {
            return "Status ID is required";
        }

        if (dto.getPortfolioId() == null || dto.getPortfolioId().trim().isEmpty()) {
            return "Portfolio ID is required";
        }

        if (dto.getOrderTypeId() == null) {
            return "Order Type ID is required";
        }

        if (dto.getSecurityId() == null || dto.getSecurityId().trim().isEmpty()) {
            return "Security ID is required";
        }

        if (dto.getQuantity() == null || dto.getQuantity().signum() <= 0) {
            return "Quantity must be positive";
        }

        if (dto.getLimitPrice() != null && dto.getLimitPrice().signum() <= 0) {
            return "Limit price must be positive when provided";
        }

        return null; // Valid
    }

    @Autowired
    private ValidationCacheService validationCacheService;

    /**
     * Validate that referenced entities exist in the database.
     * Uses cached validation to reduce database calls during batch processing.
     * 
     * @param dto The order DTO to validate
     * @return Error message if validation fails, null if valid
     */
    private String validateOrderReferences(OrderPostDTO dto) {
        // Check if blotter exists (cached)
        if (!validationCacheService.blotterExists(dto.getBlotterId())) {
            return String.format("Blotter with ID %d not found", dto.getBlotterId());
        }

        // Check if status exists (cached)
        if (!validationCacheService.statusExists(dto.getStatusId())) {
            return String.format("Status with ID %d not found", dto.getStatusId());
        }

        // Check if order type exists (cached)
        if (!validationCacheService.orderTypeExists(dto.getOrderTypeId())) {
            return String.format("Order Type with ID %d not found", dto.getOrderTypeId());
        }

        return null; // Valid
    }

}