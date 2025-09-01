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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
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
import java.util.concurrent.Semaphore;
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

    // Semaphore to limit concurrent database operations and prevent connection pool
    // exhaustion
    private final Semaphore databaseOperationSemaphore = new Semaphore(25); // Max 25 concurrent DB ops

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
     * Submit multiple orders in batch to the trade service.
     * Processes orders individually (non-atomic batch) and continues processing
     * even if some orders fail. Each order is processed in its own transaction.
     * 
     * @param orderIds List of order IDs to submit
     * @return BatchSubmitResponseDTO containing results for each order
     */
    public BatchSubmitResponseDTO submitOrdersBatch(List<Integer> orderIds) {
        logger.info("Starting batch order submission for {} orders", orderIds.size());

        // Check for duplicate order IDs in the request
        Set<Integer> uniqueOrderIds = new HashSet<>(orderIds);
        if (uniqueOrderIds.size() != orderIds.size()) {
            logger.warn(
                    "DUPLICATE_TRACKING: Duplicate order IDs detected in batch request! Total: {}, Unique: {}, OrderIds: {}",
                    orderIds.size(), uniqueOrderIds.size(), orderIds);
        }

        // Validate batch size
        if (orderIds.size() > MAX_SUBMIT_BATCH_SIZE) {
            String errorMessage = String.format("Batch size %d exceeds maximum allowed size of %d",
                    orderIds.size(), MAX_SUBMIT_BATCH_SIZE);
            logger.warn("Batch submission rejected: {}", errorMessage);
            return BatchSubmitResponseDTO.validationFailure(errorMessage);
        }

        List<OrderSubmitResultDTO> results = new ArrayList<>();

        // Process each order individually in separate transactions
        for (int i = 0; i < orderIds.size(); i++) {
            Integer orderId = orderIds.get(i);
            logger.debug("Processing order {} (index {}) in batch", orderId, i);

            OrderSubmitResultDTO result = submitIndividualOrderInTransaction(orderId, i);
            results.add(result);

            logger.debug("Order {} processed with status: {}", orderId, result.getStatus());
        }

        // Create response based on results
        BatchSubmitResponseDTO response = BatchSubmitResponseDTO.fromResults(results);

        logger.info("Batch submission completed: {} successful, {} failed out of {} total",
                response.getSuccessful(), response.getFailed(), response.getTotalRequested());

        return response;
    }

    /**
     * Submit multiple orders in batch to the trade service with parallel
     * processing.
     * This is an enhanced version that processes orders in parallel for better
     * performance.
     * Trade service calls are made concurrently while maintaining individual error
     * handling.
     * Each order is processed in its own transaction.
     * 
     * @param orderIds List of order IDs to submit
     * @return BatchSubmitResponseDTO containing results for each order
     */
    public BatchSubmitResponseDTO submitOrdersBatchParallel(List<Integer> orderIds) {
        logger.info("Starting parallel batch order submission for {} orders", orderIds.size());

        // Check for duplicate order IDs in the request
        Set<Integer> uniqueOrderIds = new HashSet<>(orderIds);
        if (uniqueOrderIds.size() != orderIds.size()) {
            logger.warn(
                    "DUPLICATE_TRACKING: Duplicate order IDs detected in PARALLEL batch request! Total: {}, Unique: {}, OrderIds: {}",
                    orderIds.size(), uniqueOrderIds.size(), orderIds);
        }

        // Validate batch size
        if (orderIds.size() > MAX_SUBMIT_BATCH_SIZE) {
            String errorMessage = String.format("Batch size %d exceeds maximum allowed size of %d",
                    orderIds.size(), MAX_SUBMIT_BATCH_SIZE);
            logger.warn("Parallel batch submission rejected: {}", errorMessage);
            return BatchSubmitResponseDTO.validationFailure(errorMessage);
        }

        // Create thread pool for parallel processing
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(orderIds.size(), 10));

        try {
            // Create CompletableFuture for each order
            List<CompletableFuture<OrderSubmitResultDTO>> futures = IntStream.range(0, orderIds.size())
                    .mapToObj(i -> CompletableFuture
                            .supplyAsync(() -> submitIndividualOrderInTransaction(orderIds.get(i), i), executor))
                    .toList();

            // Wait for all futures to complete and collect results
            List<OrderSubmitResultDTO> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            // Create response based on results
            BatchSubmitResponseDTO response = BatchSubmitResponseDTO.fromResults(results);

            logger.info("Parallel batch submission completed: {} successful, {} failed out of {} total",
                    response.getSuccessful(), response.getFailed(), response.getTotalRequested());

            return response;

        } finally {
            executor.shutdown();
        }
    }

    /**
     * Submit a single order as part of batch processing with optimized transaction
     * management.
     * This method minimizes database connection holding time by separating database
     * operations
     * from external service calls to prevent connection leaks.
     * 
     * @param orderId      The ID of the order to submit
     * @param requestIndex The index of this order in the batch request
     * @return OrderSubmitResultDTO containing the result of the submission
     */
    public OrderSubmitResultDTO submitIndividualOrderInTransaction(Integer orderId, Integer requestIndex) {
        return submitIndividualOrder(orderId, requestIndex);
    }

    /**
     * Submit a single order as part of batch processing with optimized transaction
     * management.
     * This method uses atomic reservation to prevent race conditions and duplicate
     * submissions.
     * 
     * @param orderId      The ID of the order to submit
     * @param requestIndex The index of this order in the batch request
     * @return OrderSubmitResultDTO containing the result of the submission
     */
    private OrderSubmitResultDTO submitIndividualOrder(Integer orderId, Integer requestIndex) {
        String threadName = Thread.currentThread().getName();
        long startTime = System.currentTimeMillis();

        logger.info("DUPLICATE_TRACKING: Starting submission for orderId={}, requestIndex={}, thread={}, timestamp={}",
                orderId, requestIndex, threadName, startTime);

        try {
            // Step 1: Atomically reserve the order for submission
            // This prevents race conditions by using database-level atomic operations
            logger.info("DUPLICATE_TRACKING: Attempting atomic reservation for orderId={}, thread={}", orderId,
                    threadName);
            boolean reserved = atomicallyReserveOrderForSubmission(orderId);

            if (!reserved) {
                logger.warn("DUPLICATE_TRACKING: Failed to reserve order {} - already reserved or invalid, thread={}",
                        orderId, threadName);
                return OrderSubmitResultDTO.failure(orderId,
                        "Order already being processed or not available for submission", requestIndex);
            }

            logger.info("DUPLICATE_TRACKING: Successfully reserved order {} for submission, thread={}", orderId,
                    threadName);

            try {
                // Step 2: Load order details for trade service call
                Order order = loadAndValidateOrder(orderId);
                if (order == null) {
                    logger.warn(
                            "DUPLICATE_TRACKING: Order {} not found after reservation, releasing reservation, thread={}",
                            orderId, threadName);
                    releaseOrderReservation(orderId);
                    return OrderSubmitResultDTO.failure(orderId, "Order not found", requestIndex);
                }

                // Step 3: Call trade service WITHOUT holding database connection
                logger.info("DUPLICATE_TRACKING: Calling trade service for reserved order {}, thread={}", orderId,
                        threadName);
                long tradeServiceStart = System.currentTimeMillis();
                Integer tradeOrderId = callTradeService(order);
                long tradeServiceEnd = System.currentTimeMillis();

                if (tradeOrderId == null) {
                    logger.warn(
                            "DUPLICATE_TRACKING: Trade service call failed for order {}, releasing reservation, thread={}",
                            orderId, threadName);
                    releaseOrderReservation(orderId);
                    return OrderSubmitResultDTO.failure(orderId, "Trade service submission failed", requestIndex);
                }

                logger.info(
                        "DUPLICATE_TRACKING: Trade service returned tradeOrderId={} for order {} in {}ms, thread={}",
                        tradeOrderId, orderId, (tradeServiceEnd - tradeServiceStart), threadName);

                // Step 4: Update the reserved order with the actual trade order ID
                logger.info("DUPLICATE_TRACKING: Updating reserved order {} with tradeOrderId={}, thread={}",
                        orderId, tradeOrderId, threadName);
                boolean updateSuccess = updateReservedOrderWithTradeOrderId(orderId, tradeOrderId);

                if (!updateSuccess) {
                    logger.error(
                            "DUPLICATE_TRACKING: Failed to update reserved order {} with tradeOrderId={}, thread={} - THIS SHOULD NOT HAPPEN!",
                            orderId, tradeOrderId, threadName);
                    // Don't release reservation here as the order might be in an inconsistent state
                    return OrderSubmitResultDTO.failure(orderId,
                            "Failed to update order after successful trade service call", requestIndex);
                }

                // Step 5: Update order status to SENT
                updateOrderStatusToSent(orderId);

                long totalTime = System.currentTimeMillis() - startTime;
                logger.info(
                        "DUPLICATE_TRACKING: Order {} successfully submitted with tradeOrderId={} in {}ms, thread={}",
                        orderId, tradeOrderId, totalTime, threadName);
                return OrderSubmitResultDTO.success(orderId, tradeOrderId, requestIndex);

            } catch (Exception e) {
                logger.error(
                        "DUPLICATE_TRACKING: Error during submission of reserved order {}, releasing reservation, thread={}: {}",
                        orderId, threadName, e.getMessage(), e);
                releaseOrderReservation(orderId);
                throw e;
            }

        } catch (Exception e) {
            logger.error("DUPLICATE_TRACKING: Unexpected error submitting order {} in batch: {}, thread={}",
                    orderId, e.getMessage(), threadName, e);
            return OrderSubmitResultDTO.failure(orderId,
                    "Internal error during submission: " + e.getMessage(), requestIndex);
        }
    }

    /**
     * Load and validate order using manual transaction management with semaphore
     * protection.
     * 
     * @param orderId The order ID to load
     * @return The order if found and valid, null otherwise
     */
    public Order loadAndValidateOrder(Integer orderId) {
        try {
            // Acquire semaphore with timeout to prevent indefinite waiting
            if (!databaseOperationSemaphore.tryAcquire(2, TimeUnit.SECONDS)) {
                logger.warn("Database operation semaphore timeout for order {} load", orderId);
                return null;
            }

            try {
                return readOnlyTransactionTemplate.execute(status -> {
                    try {
                        return orderRepository.findById(orderId).orElse(null);
                    } catch (Exception e) {
                        logger.warn("Failed to load order {}: {}", orderId, e.getMessage());
                        status.setRollbackOnly();
                        return null;
                    }
                });
            } finally {
                databaseOperationSemaphore.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for database semaphore for order {}", orderId);
            return null;
        }
    }

    /**
     * Atomically reserve an order for submission by setting a processing flag.
     * This prevents race conditions by using database-level atomic operations.
     * 
     * @param orderId The order ID to reserve
     * @return true if successfully reserved, false if already reserved or invalid
     */
    public boolean atomicallyReserveOrderForSubmission(Integer orderId) {
        String threadName = Thread.currentThread().getName();

        try {
            if (!databaseOperationSemaphore.tryAcquire(2, TimeUnit.SECONDS)) {
                logger.warn("Database operation semaphore timeout for order {} reservation", orderId);
                return false;
            }

            try {
                Boolean result = transactionTemplate.execute(status -> {
                    try {
                        logger.info(
                                "DUPLICATE_TRACKING: Attempting atomic reservation for orderId={} (will use -{} as reservation value), thread={}",
                                orderId, orderId, threadName);

                        // Use a custom query to atomically check and update
                        // This will only update if status='NEW' AND tradeOrderId IS NULL
                        // Sets tradeOrderId to negative order ID to ensure uniqueness
                        int updatedRows = orderRepository.atomicallyReserveForSubmission(orderId);

                        boolean success = updatedRows > 0;
                        logger.info(
                                "DUPLICATE_TRACKING: Atomic reservation for orderId={}, success={}, updatedRows={}, reservationValue={}, thread={}",
                                orderId, success, updatedRows, success ? -orderId : "N/A", threadName);

                        return success;

                    } catch (Exception e) {
                        logger.error("DUPLICATE_TRACKING: Failed to atomically reserve order {} - {}, thread={}",
                                orderId, e.getMessage(), threadName);
                        status.setRollbackOnly();
                        return false;
                    }
                });

                return result != null ? result : false;

            } finally {
                databaseOperationSemaphore.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for database semaphore for order {} reservation", orderId);
            return false;
        }
    }

    /**
     * Update order status after successful trade service submission using manual
     * transaction management
     * with semaphore protection to prevent connection pool exhaustion.
     * Uses optimistic locking to prevent race conditions.
     * 
     * @param orderId      The order ID to update
     * @param tradeOrderId The trade order ID from the trade service
     * @return true if update was successful, false otherwise
     */
    public boolean updateOrderAfterSubmissionSafe(Integer orderId, Integer tradeOrderId) {
        String threadName = Thread.currentThread().getName();
        long updateStart = System.currentTimeMillis();

        logger.info("DUPLICATE_TRACKING: Starting database update for orderId={}, tradeOrderId={}, thread={}",
                orderId, tradeOrderId, threadName);

        try {
            // Acquire semaphore with timeout to prevent indefinite waiting
            if (!databaseOperationSemaphore.tryAcquire(2, TimeUnit.SECONDS)) {
                logger.warn("Database operation semaphore timeout for order {} update", orderId);
                return false;
            }

            try {
                Boolean result = transactionTemplate.execute(status -> {
                    try {
                        logger.info(
                                "DUPLICATE_TRACKING: Inside transaction - re-loading order {} for update, thread={}",
                                orderId, threadName);

                        // Re-load the order to ensure we have the latest version
                        Optional<Order> orderOpt = orderRepository.findById(orderId);
                        if (orderOpt.isEmpty()) {
                            logger.warn("Order {} not found during status update", orderId);
                            return false;
                        }

                        Order order = orderOpt.get();

                        logger.info(
                                "DUPLICATE_TRACKING: Re-loaded order {} - status={}, existing_tradeOrderId={}, new_tradeOrderId={}, thread={}",
                                orderId, order.getStatus().getAbbreviation(), order.getTradeOrderId(), tradeOrderId,
                                threadName);

                        // CRITICAL: Check both status AND that tradeOrderId is not already set
                        // This prevents duplicate processing even if status hasn't changed yet
                        if (!"NEW".equals(order.getStatus().getAbbreviation())) {
                            logger.warn(
                                    "DUPLICATE_TRACKING: Order {} status changed during processing (current: {}), thread={} - RACE CONDITION!",
                                    orderId, order.getStatus().getAbbreviation(), threadName);
                            return false;
                        }

                        if (order.getTradeOrderId() != null) {
                            logger.warn(
                                    "DUPLICATE_TRACKING: Order {} already has tradeOrderId {} - preventing duplicate submission, thread={} - RACE CONDITION!",
                                    orderId, order.getTradeOrderId(), threadName);
                            return false;
                        }

                        logger.info("DUPLICATE_TRACKING: Proceeding with order {} update, thread={}", orderId,
                                threadName);
                        updateOrderAfterSubmission(order, tradeOrderId);
                        logger.info("DUPLICATE_TRACKING: Order {} update completed successfully, thread={}", orderId,
                                threadName);
                        return true;

                    } catch (org.springframework.dao.OptimisticLockingFailureException e) {
                        logger.warn(
                                "DUPLICATE_TRACKING: Optimistic locking failure for order {} - another thread updated it, thread={}",
                                orderId, threadName);
                        status.setRollbackOnly();
                        return false;
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        logger.warn(
                                "DUPLICATE_TRACKING: Data integrity violation for order {} - likely duplicate key: {}, thread={}",
                                orderId, e.getMessage(), threadName);
                        status.setRollbackOnly();
                        return false;
                    } catch (Exception e) {
                        logger.error("DUPLICATE_TRACKING: Failed to update order {} after submission: {}, thread={}",
                                orderId, e.getMessage(), threadName, e);
                        status.setRollbackOnly();
                        return false;
                    }
                });

                long updateEnd = System.currentTimeMillis();
                logger.info(
                        "DUPLICATE_TRACKING: Database update completed for orderId={}, success={}, duration={}ms, thread={}",
                        orderId, (result != null ? result : false), (updateEnd - updateStart), threadName);

                return result != null ? result : false;

            } finally {
                databaseOperationSemaphore.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for database semaphore for order {} update", orderId);
            return false;
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
     * Update a reserved order with the actual trade order ID from the trade
     * service.
     * 
     * @param orderId      The order ID to update
     * @param tradeOrderId The actual trade order ID
     * @return true if successful, false otherwise
     */
    private boolean updateReservedOrderWithTradeOrderId(Integer orderId, Integer tradeOrderId) {
        String threadName = Thread.currentThread().getName();

        try {
            if (!databaseOperationSemaphore.tryAcquire(2, TimeUnit.SECONDS)) {
                logger.warn("Database operation semaphore timeout for order {} trade ID update", orderId);
                return false;
            }

            try {
                Boolean result = transactionTemplate.execute(status -> {
                    try {
                        int updatedRows = orderRepository.updateReservedOrderWithTradeOrderId(orderId, tradeOrderId);
                        logger.info(
                                "DUPLICATE_TRACKING: Updated reserved order {} with tradeOrderId={}, updatedRows={}, thread={}",
                                orderId, tradeOrderId, updatedRows, threadName);
                        return updatedRows > 0;
                    } catch (Exception e) {
                        logger.error(
                                "DUPLICATE_TRACKING: Failed to update reserved order {} with tradeOrderId={}, thread={}: {}",
                                orderId, tradeOrderId, threadName, e.getMessage());
                        status.setRollbackOnly();
                        return false;
                    }
                });

                return result != null ? result : false;

            } finally {
                databaseOperationSemaphore.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for database semaphore for order {} trade ID update", orderId);
            return false;
        }
    }

    /**
     * Release a reserved order back to available state if submission fails.
     * 
     * @param orderId The order ID to release
     * @return true if successful, false otherwise
     */
    private boolean releaseOrderReservation(Integer orderId) {
        String threadName = Thread.currentThread().getName();

        try {
            if (!databaseOperationSemaphore.tryAcquire(2, TimeUnit.SECONDS)) {
                logger.warn("Database operation semaphore timeout for order {} reservation release", orderId);
                return false;
            }

            try {
                Boolean result = transactionTemplate.execute(status -> {
                    try {
                        int updatedRows = orderRepository.releaseReservedOrder(orderId);
                        logger.info("DUPLICATE_TRACKING: Released reservation for order {}, updatedRows={}, thread={}",
                                orderId, updatedRows, threadName);
                        return updatedRows > 0;
                    } catch (Exception e) {
                        logger.error("DUPLICATE_TRACKING: Failed to release reservation for order {}, thread={}: {}",
                                orderId, threadName, e.getMessage());
                        status.setRollbackOnly();
                        return false;
                    }
                });

                return result != null ? result : false;

            } finally {
                databaseOperationSemaphore.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for database semaphore for order {} reservation release", orderId);
            return false;
        }
    }

    /**
     * Load and validate orders for bulk submission.
     * Efficiently loads orders in batch and filters them for bulk submission eligibility.
     * 
     * @param orderIds List of order IDs to load and validate
     * @return List of valid orders ready for bulk submission
     */
    public List<Order> loadAndValidateOrdersForBulkSubmission(List<Integer> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            logger.warn("Empty or null order IDs list provided for bulk submission");
            return new ArrayList<>();
        }
        
        logger.info("Loading and validating {} orders for bulk submission", orderIds.size());

        try {
            // Acquire semaphore to prevent database connection exhaustion
            if (!databaseOperationSemaphore.tryAcquire(2, TimeUnit.SECONDS)) {
                logger.warn("Database operation semaphore timeout for bulk order loading");
                return new ArrayList<>();
            }

            try {
                return readOnlyTransactionTemplate.execute(status -> {
                    try {
                        // Batch load all orders using findAllById for efficiency
                        List<Order> allOrders = orderRepository.findAllById(orderIds);
                        logger.debug("Loaded {} orders from database out of {} requested", 
                                allOrders.size(), orderIds.size());

                        // Filter orders for bulk submission eligibility
                        List<Order> validOrders = allOrders.stream()
                                .filter(this::isOrderValidForBulkSubmission)
                                .toList();

                        logger.info("Validated {} orders out of {} loaded for bulk submission", 
                                validOrders.size(), allOrders.size());

                        return validOrders;

                    } catch (Exception e) {
                        logger.error("Failed to load and validate orders for bulk submission: {}", e.getMessage(), e);
                        status.setRollbackOnly();
                        return new ArrayList<>();
                    }
                });

            } finally {
                databaseOperationSemaphore.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for database semaphore for bulk order loading");
            return new ArrayList<>();
        }
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
     * Update order status to SENT after successful submission.
     * 
     * @param orderId The order ID to update
     */
    private void updateOrderStatusToSent(Integer orderId) {
        String threadName = Thread.currentThread().getName();

        try {
            if (!databaseOperationSemaphore.tryAcquire(2, TimeUnit.SECONDS)) {
                logger.warn("Database operation semaphore timeout for order {} status update", orderId);
                return;
            }

            try {
                transactionTemplate.execute(status -> {
                    try {
                        Optional<Order> orderOpt = orderRepository.findById(orderId);
                        if (orderOpt.isPresent()) {
                            Order order = orderOpt.get();
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
                                    .tradeOrderId(order.getTradeOrderId())
                                    .orderTimestamp(order.getOrderTimestamp())
                                    .version(order.getVersion())
                                    .build();

                            orderRepository.save(updatedOrder);
                            logger.info("DUPLICATE_TRACKING: Updated order {} status to SENT, thread={}", orderId,
                                    threadName);
                        }
                        return null;
                    } catch (Exception e) {
                        logger.error("DUPLICATE_TRACKING: Failed to update order {} status to SENT, thread={}: {}",
                                orderId, threadName, e.getMessage());
                        status.setRollbackOnly();
                        return null;
                    }
                });

            } finally {
                databaseOperationSemaphore.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for database semaphore for order {} status update", orderId);
        }
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

    /**
     * Build a bulk trade order request from a list of Order entities.
     * Maps order fields to TradeOrderPostDTO format and validates required fields.
     * 
     * @param orders List of Order entities to convert
     * @return BulkTradeOrderRequestDTO containing the mapped trade orders
     * @throws IllegalArgumentException if any order is missing required fields
     */
    public BulkTradeOrderRequestDTO buildBulkTradeOrderRequest(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            throw new IllegalArgumentException("Orders list cannot be null or empty");
        }

        logger.info("Building bulk trade order request for {} orders", orders.size());

        List<TradeOrderPostDTO> tradeOrders = new ArrayList<>();
        
        for (Order order : orders) {
            // Validate required fields before building request
            validateOrderForBulkSubmission(order);
            
            // Map Order entity to TradeOrderPostDTO
            TradeOrderPostDTO tradeOrder = TradeOrderPostDTO.builder()
                    .orderId(order.getId())
                    .portfolioId(order.getPortfolioId())
                    .orderType(order.getOrderType().getAbbreviation())
                    .securityId(order.getSecurityId())
                    .quantity(order.getQuantity())
                    .limitPrice(order.getLimitPrice())
                    .tradeTimestamp(order.getOrderTimestamp()) // Use order timestamp as trade timestamp
                    .blotterId(order.getBlotter() != null ? order.getBlotter().getId() : null)
                    .build();
            
            tradeOrders.add(tradeOrder);
            
            logger.debug("Mapped order {} to trade order: portfolioId={}, orderType={}, securityId={}, quantity={}, limitPrice={}, blotterId={}",
                    order.getId(), tradeOrder.getPortfolioId(), tradeOrder.getOrderType(), 
                    tradeOrder.getSecurityId(), tradeOrder.getQuantity(), tradeOrder.getLimitPrice(), tradeOrder.getBlotterId());
        }

        BulkTradeOrderRequestDTO bulkRequest = BulkTradeOrderRequestDTO.of(tradeOrders);
        
        logger.info("Successfully built bulk trade order request with {} trade orders", bulkRequest.getOrderCount());
        
        return bulkRequest;
    }

    /**
     * Validate that an order has all required fields for bulk submission to the trade service.
     * 
     * @param order The order to validate
     * @throws IllegalArgumentException if any required field is missing or invalid
     */
    private void validateOrderForBulkSubmission(Order order) {
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

        // Note: blotterId is optional in the trade service API, so we don't validate it as required
        
        logger.debug("Order {} passed validation for bulk submission", order.getId());
    }
}