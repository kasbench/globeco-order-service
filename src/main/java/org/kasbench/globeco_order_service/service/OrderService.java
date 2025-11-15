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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;

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
    private final BulkSubmissionPerformanceMonitor performanceMonitor;
    private final BatchUpdateService batchUpdateService;
    
    // Performance monitoring metrics
    private final MeterRegistry meterRegistry;
    private final Timer bulkSubmissionTimer;
    private final Timer orderLoadTimer;
    private final Timer tradeServiceCallTimer;
    private final Timer databaseUpdateTimer;
    private final Counter bulkSubmissionSuccessCounter;
    private final Counter bulkSubmissionFailureCounter;
    private final Counter orderProcessedCounter;
    private volatile long lastBulkSubmissionDuration = 0;
    private volatile double lastSuccessRate = 0.0;



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
            MeterRegistry meterRegistry,
            BulkSubmissionPerformanceMonitor performanceMonitor,
            BatchUpdateService batchUpdateService,
            @Value("${trade.service.url:http://globeco-trade-service:8082}") String tradeServiceUrl) {
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
        this.meterRegistry = meterRegistry;
        this.performanceMonitor = performanceMonitor;
        this.batchUpdateService = batchUpdateService;

        // Create transaction templates for precise control
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setTimeout(5); // 5 second timeout

        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
        this.readOnlyTransactionTemplate.setTimeout(3); // 3 second timeout for reads
        
        // Initialize performance monitoring metrics
        this.bulkSubmissionTimer = Timer.builder("bulk_submission.duration")
                .description("Time taken for bulk order submission")
                .tag("service", "order")
                .register(meterRegistry);
                
        this.orderLoadTimer = Timer.builder("bulk_submission.order_load.duration")
                .description("Time taken to load and validate orders for bulk submission")
                .tag("service", "order")
                .register(meterRegistry);
                
        this.tradeServiceCallTimer = Timer.builder("bulk_submission.trade_service.duration")
                .description("Time taken for trade service bulk API call")
                .tag("service", "order")
                .register(meterRegistry);
                
        this.databaseUpdateTimer = Timer.builder("bulk_submission.database_update.duration")
                .description("Time taken to update order statuses after bulk submission")
                .tag("service", "order")
                .register(meterRegistry);
                
        this.bulkSubmissionSuccessCounter = Counter.builder("bulk_submission.success")
                .description("Number of successful bulk submissions")
                .tag("service", "order")
                .register(meterRegistry);
                
        this.bulkSubmissionFailureCounter = Counter.builder("bulk_submission.failure")
                .description("Number of failed bulk submissions")
                .tag("service", "order")
                .register(meterRegistry);
                
        this.orderProcessedCounter = Counter.builder("bulk_submission.orders_processed")
                .description("Total number of orders processed through bulk submission")
                .tag("service", "order")
                .register(meterRegistry);
                
        // Register gauges for real-time monitoring
        Gauge.builder("bulk_submission.last_duration_ms", this, service -> service.lastBulkSubmissionDuration)
                .description("Duration of the last bulk submission in milliseconds")
                .tag("service", "order")
                .register(meterRegistry);
                
        Gauge.builder("bulk_submission.last_success_rate", this, service -> service.lastSuccessRate)
                .description("Success rate of the last bulk submission")
                .tag("service", "order")
                .register(meterRegistry);
    }

    /**
     * Submit a single order to the trade service.
     * This method uses split transactions to minimize connection hold time:
     * 1. Load order in read-only transaction (3s timeout)
     * 2. Call external trade service (no transaction)
     * 3. Update order in write transaction (5s timeout)
     * 
     * @param id The order ID to submit
     * @return OrderDTO with updated status, or null if submission fails
     */
    public OrderDTO submitOrder(Integer id) {
        // Transaction 1: Load order (short, read-only)
        Order order = loadOrderInReadTransaction(id);
        if (order == null) {
            return null;
        }

        // No transaction: External service call
        Integer tradeOrderId = callTradeService(order);
        if (tradeOrderId == null) {
            return null;
        }

        // Transaction 2: Update order (short, write)
        updateOrderInWriteTransaction(id, tradeOrderId);

        // Load and return the updated order
        Order updatedOrder = loadOrderInReadTransaction(id);
        return toOrderDTO(updatedOrder);
    }

    /**
     * Load an order in a read-only transaction with short timeout.
     * Validates that the order is in NEW status and ready for submission.
     * 
     * @param id The order ID to load
     * @return Order entity if found and valid, null otherwise
     */
    @Transactional(readOnly = true, timeout = 3)
    private Order loadOrderInReadTransaction(Integer id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            logger.warn("Order {} not found", id);
            return null;
        }

        if (!order.getStatus().getAbbreviation().equals("NEW")) {
            logger.warn("Order {} is not in NEW status: {}", id, order.getStatus().getAbbreviation());
            return null;
        }

        logger.debug("Loaded order {} in read transaction", id);
        return order;
    }

    /**
     * Update an order with trade service results in a write transaction with short timeout.
     * Sets the order status to SENT and records the trade order ID.
     * 
     * @param id The order ID to update
     * @param tradeOrderId The trade order ID from the trade service
     */
    @Transactional(timeout = 5)
    private void updateOrderInWriteTransaction(Integer id, Integer tradeOrderId) {
        Order order = orderRepository.findById(id).orElseThrow(
            () -> new RuntimeException("Order " + id + " not found during update")
        );

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
        logger.debug("Updated order {} with tradeOrderId {} in write transaction", id, tradeOrderId);
    }

    /**
     * Submit multiple orders in batch to the trade service using bulk submission.
     * This method uses a single bulk API call to the trade service for improved performance,
     * replacing the previous individual order processing approach.
     * 
     * Transaction boundaries are optimized to minimize connection hold time:
     * 1. Load orders in read-only transaction (3s timeout)
     * 2. Call external trade service (no transaction)
     * 3. Update order statuses in write transaction (5s timeout)
     * 
     * @param orderIds List of order IDs to submit
     * @return BatchSubmitResponseDTO containing results for each order
     */
    public BatchSubmitResponseDTO submitOrdersBatch(List<Integer> orderIds) {
        Timer.Sample overallTimer = Timer.start(meterRegistry);
        long overallStartTime = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        
        // Validate input parameters first
        if (orderIds == null) {
            logger.error("BULK_SUBMISSION: Order IDs list is null, thread={}", threadName);
            bulkSubmissionFailureCounter.increment();
            return BatchSubmitResponseDTO.validationFailure("Order IDs cannot be null");
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("BULK_SUBMISSION: Starting bulk order submission for {} orders, thread={}, timestamp={}",
                    orderIds.size(), threadName, overallStartTime);
        }

        if (orderIds.isEmpty()) {
            logger.warn("BULK_SUBMISSION: Empty order list provided, thread={}", threadName);
            bulkSubmissionFailureCounter.increment();
            overallTimer.stop(bulkSubmissionTimer);
            return BatchSubmitResponseDTO.validationFailure("No orders provided for submission");
        }

        // Validate batch size
        if (orderIds.size() > MAX_SUBMIT_BATCH_SIZE) {
            String errorMessage = String.format("Batch size %d exceeds maximum allowed size of %d",
                    orderIds.size(), MAX_SUBMIT_BATCH_SIZE);
            logger.warn("BULK_SUBMISSION: Batch size validation failed - {}, thread={}", errorMessage, threadName);
            bulkSubmissionFailureCounter.increment();
            overallTimer.stop(bulkSubmissionTimer);
            return BatchSubmitResponseDTO.validationFailure(errorMessage);
        }

        try {
            // 1. Load and validate orders in batch
            Timer.Sample loadTimer = Timer.start(meterRegistry);
            long transactionStartTime = System.currentTimeMillis();
            
            List<Order> validOrders = loadAndValidateOrdersForBulkSubmission(orderIds);
            
            long loadDuration = (long) loadTimer.stop(orderLoadTimer);
            long transactionHoldTime = System.currentTimeMillis() - transactionStartTime;
            
            // Track transaction hold time for the read transaction
            performanceMonitor.recordTransactionHoldTime(transactionHoldTime);
            
            if (validOrders.isEmpty()) {
                logger.warn("BULK_SUBMISSION: No valid orders found from {} requested orders, thread={}",
                        orderIds.size(), threadName);
                bulkSubmissionFailureCounter.increment();
                overallTimer.stop(bulkSubmissionTimer);
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
            
            BulkTradeOrderRequestDTO bulkRequest = buildBulkTradeOrderRequest(validOrders);
            
            long buildDuration = System.currentTimeMillis() - buildStartTime;

            // 3. Call trade service bulk endpoint
            Timer.Sample tradeServiceTimer = Timer.start(meterRegistry);
            
            BulkTradeOrderResponseDTO tradeServiceResponse = callTradeServiceBulk(bulkRequest);
            
            long tradeServiceDuration = (long) tradeServiceTimer.stop(tradeServiceCallTimer);
            
            // Track external service call duration separately
            performanceMonitor.recordExternalServiceCall(tradeServiceDuration);

            // 4. Update order statuses in batch
            Timer.Sample updateTimer = Timer.start(meterRegistry);
            long updateTransactionStartTime = System.currentTimeMillis();
            
            updateOrderStatusesFromBulkResponse(validOrders, tradeServiceResponse);
            
            long updateDuration = (long) updateTimer.stop(databaseUpdateTimer);
            long updateTransactionHoldTime = System.currentTimeMillis() - updateTransactionStartTime;
            
            // Track transaction hold time for the write transaction
            performanceMonitor.recordTransactionHoldTime(updateTransactionHoldTime);

            // 5. Transform response to match existing API contract
            long transformStartTime = System.currentTimeMillis();
            
            BatchSubmitResponseDTO response = transformBulkResponseToOrderServiceFormat(tradeServiceResponse, orderIds);
            
            long transformDuration = System.currentTimeMillis() - transformStartTime;

            // Log comprehensive completion metrics and update performance tracking
            long overallDurationNanos = (long) overallTimer.stop(bulkSubmissionTimer);
            long overallDuration = System.currentTimeMillis() - overallStartTime;
            double successRate = response.getTotalRequested() > 0 ? 
                    (double) response.getSuccessful() / response.getTotalRequested() * 100 : 0;
            
            // Update performance tracking variables for gauges
            this.lastBulkSubmissionDuration = overallDuration;
            this.lastSuccessRate = successRate / 100.0; // Convert to 0-1 range
            
            // Update counters and performance monitoring
            bulkSubmissionSuccessCounter.increment();
            orderProcessedCounter.increment(response.getSuccessful());
            performanceMonitor.recordBulkSubmission(orderIds.size(), overallDuration, response.getSuccessful());
            
            // Summary log instead of detailed per-step logs
            logger.info("BULK_SUBMISSION: Completed in {}ms - {} successful, {} failed out of {} total (success_rate: {}%), thread={}",
                    overallDuration, response.getSuccessful(), response.getFailed(), response.getTotalRequested(),
                    String.format("%.2f", successRate), threadName);

            // Detailed performance breakdown only in debug mode
            if (logger.isDebugEnabled()) {
                logger.debug("BULK_SUBMISSION_PERFORMANCE: load={}ms, build={}ms, trade_service={}ms, update={}ms, transform={}ms, total={}ms, thread={}",
                        loadDuration, buildDuration, tradeServiceDuration, updateDuration, transformDuration, overallDuration, threadName);
                
                if (response.getSuccessful() > 0) {
                    double avgTimePerOrder = (double) overallDuration / response.getSuccessful();
                    logger.debug("BULK_SUBMISSION_PERFORMANCE: Average processing time per successful order: {:.2f}ms, thread={}",
                            avgTimePerOrder, threadName);
                }
            }

            return response;

        } catch (IllegalArgumentException e) {
            long overallDuration = (long) overallTimer.stop(bulkSubmissionTimer);
            bulkSubmissionFailureCounter.increment();
            logger.error("BULK_SUBMISSION: Validation error after {}ms for {} orders, thread={}, error={}",
                    overallDuration, orderIds.size(), threadName, e.getMessage());
            
            return BatchSubmitResponseDTO.validationFailure(e.getMessage());

        } catch (RuntimeException e) {
            long overallDuration = (long) overallTimer.stop(bulkSubmissionTimer);
            bulkSubmissionFailureCounter.increment();
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
            long overallDuration = (long) overallTimer.stop(bulkSubmissionTimer);
            bulkSubmissionFailureCounter.increment();
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
            TradeOrderPostDTO tradeOrderRequest = TradeOrderPostDTO.builder()
                    .orderId(order.getId())
                    .portfolioId(order.getPortfolioId())
                    .orderType(order.getOrderType().getAbbreviation())
                    .securityId(order.getSecurityId())
                    .quantity(order.getQuantity())
                    .limitPrice(order.getLimitPrice())
                    .build();

            String fullUrl = tradeServiceUrl + "/api/v1/tradeOrders";
            logger.debug(
                    "DUPLICATE_TRACKING: Sending HTTP POST to trade service for orderId={}, url={}, thread={}, timestamp={}",
                    order.getId(), fullUrl, threadName, callStart);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    fullUrl,
                    new HttpEntity<>(tradeOrderRequest),
                    String.class);

            long callEnd = System.currentTimeMillis();
            logger.debug(
                    "DUPLICATE_TRACKING: Trade service HTTP response received for orderId={}, status={}, duration={}ms, thread={}",
                    order.getId(), response.getStatusCode(), (callEnd - callStart), threadName);

            if (response.getStatusCode() == HttpStatus.CREATED) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode node = mapper.readTree(response.getBody());
                    if (node.has("id") && node.get("id").isInt()) {
                        Integer tradeOrderId = node.get("id").asInt();
                        logger.debug(
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
     * Uses read-only transaction with 3-second timeout to minimize connection hold time.
     * This method completes BEFORE the external trade service call.
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

        return readOnlyTransactionTemplate.execute(status -> {
            try {
                // Batch load all orders using eager fetching to eliminate N+1 queries
                long dbStartTime = System.currentTimeMillis();
                List<Order> allOrders = orderRepository.findAllByIdWithRelations(orderIds);
                long dbDuration = System.currentTimeMillis() - dbStartTime;
                
                // Track database query execution time
                performanceMonitor.recordDatabaseQueryTime(dbDuration);

                // Track missing orders
                if (allOrders.size() < orderIds.size()) {
                    Set<Integer> foundIds = allOrders.stream()
                            .map(Order::getId)
                            .collect(HashSet::new, Set::add, Set::addAll);
                    
                    List<Integer> missingIds = orderIds.stream()
                            .filter(id -> !foundIds.contains(id))
                            .toList();
                    
                    logger.warn("BULK_VALIDATION: {} orders not found in database, thread={}", 
                            missingIds.size(), threadName);
                    
                    if (logger.isDebugEnabled()) {
                        logger.debug("BULK_VALIDATION: Missing order IDs: {}, thread={}", missingIds, threadName);
                    }
                }

                // Filter orders for bulk submission eligibility
                long validationStartTime = System.currentTimeMillis();
                List<Order> validOrders = new ArrayList<>();
                int validationErrorCount = 0;
                
                for (Order order : allOrders) {
                    try {
                        if (isOrderValidForBulkSubmission(order)) {
                            validOrders.add(order);
                        } else {
                            validationErrorCount++;
                            if (logger.isDebugEnabled()) {
                                String reason = getOrderValidationFailureReason(order);
                                logger.debug("BULK_VALIDATION: Order {} invalid: {}, thread={}",
                                        order.getId(), reason, threadName);
                            }
                        }
                    } catch (Exception e) {
                        validationErrorCount++;
                        logger.error("BULK_VALIDATION: Error validating order {}, thread={}, error={}",
                                order != null ? order.getId() : "null", threadName, e.getMessage(), e);
                    }
                }
                
                long validationDuration = System.currentTimeMillis() - validationStartTime;
                long totalDuration = System.currentTimeMillis() - startTime;
                
                // Summary log instead of per-order logs
                double successRate = allOrders.size() > 0 ? 
                        (double) validOrders.size() / allOrders.size() * 100 : 0;
                logger.info("BULK_VALIDATION: Loaded {} orders in {}ms, validated {} (success_rate: {:.2f}%), thread={}",
                        allOrders.size(), dbDuration, validOrders.size(), successRate, threadName);

                // Log validation errors summary
                if (validationErrorCount > 0) {
                    logger.warn("BULK_VALIDATION: {} validation errors encountered, thread={}", 
                            validationErrorCount, threadName);
                }

                // Detailed performance metrics only in debug mode
                if (logger.isDebugEnabled()) {
                    double avgValidationTimePerOrder = allOrders.size() > 0 ? 
                            (double) validationDuration / allOrders.size() : 0;
                    logger.debug("BULK_VALIDATION_PERFORMANCE: Validated {} orders in {}ms (avg {:.2f}ms per order), total={}ms, thread={}",
                            allOrders.size(), validationDuration, avgValidationTimePerOrder, totalDuration, threadName);
                    
                    // Log memory usage for large batches
                    if (allOrders.size() > 50) {
                        Runtime runtime = Runtime.getRuntime();
                        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                        long maxMemory = runtime.maxMemory();
                        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
                        
                        logger.debug("BULK_VALIDATION_MEMORY: Memory usage: {:.2f}% ({} MB / {} MB), thread={}",
                                memoryUsagePercent, usedMemory / (1024 * 1024), maxMemory / (1024 * 1024), threadName);
                        
                        // Suggest garbage collection for large batches to optimize memory
                        if (memoryUsagePercent > 80) {
                            logger.warn("BULK_VALIDATION_MEMORY: High memory usage ({:.2f}%), suggesting GC, thread={}",
                                    memoryUsagePercent, threadName);
                            System.gc();
                        }
                    }
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
            return false;
        }

        // Check if order is in NEW status
        if (order.getStatus() == null || !"NEW".equals(order.getStatus().getAbbreviation())) {
            return false;
        }

        // Check if order is already processed (has tradeOrderId)
        if (order.getTradeOrderId() != null) {
            return false;
        }

        // Validate required fields for trade service submission
        if (!hasRequiredFieldsForTradeService(order)) {
            return false;
        }

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
            return false;
        }

        // Check orderType
        if (order.getOrderType() == null || order.getOrderType().getAbbreviation() == null || 
            order.getOrderType().getAbbreviation().trim().isEmpty()) {
            return false;
        }

        // Check securityId
        if (order.getSecurityId() == null || order.getSecurityId().trim().isEmpty()) {
            return false;
        }

        // Check quantity (must be positive)
        if (order.getQuantity() == null || order.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        // Check limitPrice (must be null or positive)
        if (order.getLimitPrice() != null && order.getLimitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        // Check orderTimestamp
        if (order.getOrderTimestamp() == null) {
            return false;
        }

        // Check blotter (required for blotterId)
        if (order.getBlotter() == null || order.getBlotter().getId() == null) {
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
        if (orders == null || orders.isEmpty()) {
            logger.error("Cannot build bulk request: orders list is null or empty");
            throw new IllegalArgumentException("Orders list cannot be null or empty");
        }

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
            }

            BulkTradeOrderRequestDTO bulkRequest = BulkTradeOrderRequestDTO.of(tradeOrders);
            
            if (logger.isDebugEnabled()) {
                logger.debug("Built bulk request with {} orders", orders.size());
            }
            
            return bulkRequest;

        } catch (Exception e) {
            logger.error("Failed to build bulk trade order request for {} orders: {}", 
                    orders.size(), e.getMessage(), e);
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

        if (order.getLimitPrice() != null && order.getLimitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Limit price must be null or positive for order " + order.getId());
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

        try {
            ResponseEntity<BulkTradeOrderResponseDTO> response = restTemplate.postForEntity(
                    fullUrl,
                    new HttpEntity<>(bulkRequest),
                    BulkTradeOrderResponseDTO.class);

            long duration = System.currentTimeMillis() - startTime;
            HttpStatus statusCode = HttpStatus.valueOf(response.getStatusCode().value());

            // Handle successful response (HTTP 201)
            if (statusCode == HttpStatus.CREATED) {
                BulkTradeOrderResponseDTO responseBody = response.getBody();
                
                if (responseBody == null) {
                    logger.error("BULK_SUBMISSION: Trade service returned null response body for {} orders, thread={}",
                            orderCount, threadName);
                    throw new RuntimeException("Trade service returned null response body");
                }

                if (logger.isDebugEnabled()) {
                    logger.debug("BULK_SUBMISSION: Trade service SUCCESS for {} orders in {}ms, successful={}, failed={}, thread={}",
                            orderCount, duration, responseBody.getSuccessful(), responseBody.getFailed(), threadName);
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
        if (bulkResponse == null) {
            logger.error("Cannot transform response: bulk response is null");
            throw new IllegalArgumentException("Bulk response cannot be null");
        }

        if (originalOrderIds == null || originalOrderIds.isEmpty()) {
            logger.error("Cannot transform response: original order IDs list is null or empty");
            throw new IllegalArgumentException("Original order IDs cannot be null or empty");
        }

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
                } else {
                    // Failed submission or no result found
                    String errorMessage = tradeResult != null ? tradeResult.getMessage() : 
                            "No result found in bulk response";
                    results.add(OrderSubmitResultDTO.failure(orderId, errorMessage, i));
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

            if (logger.isDebugEnabled()) {
                logger.debug("Transformed bulk response: status={}, successful={}, failed={}",
                        status, successful, failed);
            }

            return response;

        } catch (Exception e) {
            logger.error("Failed to transform bulk response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to transform bulk response", e);
        }
    }

    /**
     * Update order statuses from bulk response using efficient batch operations.
     * Handles partial success scenarios where some orders succeed and others fail.
     * Uses write transaction with 5-second timeout for optimal connection management.
     * This method is called AFTER the external trade service call completes (outside transaction).
     * 
     * @param orders List of orders that were submitted in the bulk request
     * @param bulkResponse The bulk response from the trade service
     */
    public void updateOrderStatusesFromBulkResponse(List<Order> orders, BulkTradeOrderResponseDTO bulkResponse) {
        long startTime = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        
        // Validate input parameters
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
                int invalidResults = 0;
                if (bulkResponse.getResults() != null) {
                    for (TradeOrderResultDTO result : bulkResponse.getResults()) {
                        if (result.getRequestIndex() != null && result.getRequestIndex() < orders.size()) {
                            Order order = orders.get(result.getRequestIndex());
                            if (order != null && order.getId() != null) {
                                resultMap.put(order.getId(), result);
                            } else {
                                invalidResults++;
                            }
                        } else {
                            invalidResults++;
                        }
                    }
                }
                
                if (invalidResults > 0 && logger.isDebugEnabled()) {
                    logger.debug("BULK_STATUS_UPDATE: {} invalid results encountered, thread={}", 
                            invalidResults, threadName);
                }

                // Get SENT status once for all successful updates
                Status sentStatus;
                try {
                    sentStatus = getSentStatus();
                } catch (Exception e) {
                    logger.error("BULK_STATUS_UPDATE: Failed to retrieve SENT status, thread={}, error={}",
                            threadName, e.getMessage(), e);
                    throw new RuntimeException("Failed to retrieve SENT status", e);
                }
                
                // Track successful and failed updates
                List<Order> successfulOrders = new ArrayList<>();
                List<Order> failedOrders = new ArrayList<>();
                int processingErrors = 0;
                
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
                        } else {
                            // Order failed or no result found - leave in NEW status
                            failedOrders.add(order);
                        }
                    } catch (Exception e) {
                        processingErrors++;
                        failedOrders.add(order);
                        logger.error("BULK_STATUS_UPDATE: Error processing order {}, thread={}, error={}",
                                order.getId(), threadName, e.getMessage(), e);
                    }
                }
                
                // Log processing errors summary
                if (processingErrors > 0) {
                    logger.warn("BULK_STATUS_UPDATE: Encountered {} processing errors during status update, thread={}",
                            processingErrors, threadName);
                }
                
                // Perform batch updates for successful orders using JDBC batch operations
                if (!successfulOrders.isEmpty()) {
                    try {
                        long dbStartTime = System.currentTimeMillis();
                        
                        // Use BatchUpdateService for efficient JDBC batch updates
                        int updatedCount = batchUpdateService.batchUpdateOrderStatuses(successfulOrders, sentStatus);
                        
                        long dbDuration = System.currentTimeMillis() - dbStartTime;
                        
                        // Summary log with key metrics
                        logger.info("BULK_STATUS_UPDATE: Updated {} orders to SENT status in {}ms using JDBC batch, thread={}",
                                updatedCount, dbDuration, threadName);
                        
                        // Detailed performance metrics only in debug mode
                        if (logger.isDebugEnabled() && updatedCount > 0) {
                            double avgDbTimePerOrder = (double) dbDuration / updatedCount;
                            logger.debug("BULK_STATUS_UPDATE_PERFORMANCE: JDBC batch update avg {:.2f}ms per order, thread={}",
                                    avgDbTimePerOrder, threadName);
                        }
                        
                    } catch (Exception e) {
                        logger.error("BULK_STATUS_UPDATE: Failed to perform JDBC batch database update for {} orders, thread={}, error={}",
                                successfulOrders.size(), threadName, e.getMessage(), e);
                        throw new RuntimeException("JDBC batch database update failed", e);
                    }
                } else {
                    logger.warn("BULK_STATUS_UPDATE: No successful orders to update in database, thread={}", threadName);
                }
                
                // Summary log of batch update operation
                long totalDuration = System.currentTimeMillis() - startTime;
                double successRate = orderCount > 0 ? (double) successfulOrders.size() / orderCount * 100 : 0;
                logger.info("BULK_STATUS_UPDATE: Completed in {}ms - {} successful, {} failed (success_rate: {:.2f}%), thread={}",
                        totalDuration, successfulOrders.size(), failedOrders.size(), successRate, threadName);
                
                // Log failed orders for monitoring
                if (!failedOrders.isEmpty()) {
                    if (logger.isDebugEnabled()) {
                        List<Integer> failedOrderIds = failedOrders.stream()
                                .map(Order::getId)
                                .toList();
                        logger.debug("BULK_STATUS_UPDATE: Failed order IDs: {}, thread={}", failedOrderIds, threadName);
                    }
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
                    if (logger.isDebugEnabled()) {
                        logger.debug("Security service unavailable for securityId: {}, using fallback",
                                order.getSecurityId());
                    }
                }
            } catch (Exception e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to fetch security data for securityId: {} - {}", order.getSecurityId(),
                            e.getMessage());
                }
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
                    if (logger.isDebugEnabled()) {
                        logger.debug("Portfolio service unavailable for portfolioId: {}, using fallback",
                                order.getPortfolioId());
                    }
                }
            } catch (Exception e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to fetch portfolio data for portfolioId: {} - {}", order.getPortfolioId(),
                            e.getMessage());
                }
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
                logger.error("No security tickers could be resolved to IDs. Tickers requested: {}", tickers);
            } else {
                logger.debug("Successfully resolved {} of {} security tickers to IDs",
                        resolvedIds.size(), tickers.size());

                // Log which tickers were not resolved
                Set<String> unresolvedTickers = new HashSet<>(tickers);
                unresolvedTickers.removeAll(resolvedIds.keySet());
                if (!unresolvedTickers.isEmpty()) {
                    logger.error("Unresolved security tickers (will be ignored in filtering): {}", unresolvedTickers);
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
                logger.debug("Successfully resolved {} of {} portfolio names to IDs",
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
            logger.error("Exception during order creation: {}", e.getMessage(), e);
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
        logger.debug("Starting batch order processing for {} orders", orders.size());
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
        logger.debug("Batch processing completed: {} total, {} successful, {} failed, {}ms processing time",
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
                logger.error("Order reference validation failed at index {}: {}", requestIndex, referenceError);
                return OrderPostResponseDTO.failure(referenceError, requestIndex);
            }

            // Create the order using existing logic
            OrderWithDetailsDTO createdOrder = create(orderDto);
            if (createdOrder == null) {
                logger.error("Order creation returned null at index {}: Failed to create order - unknown error",
                        requestIndex);
                return OrderPostResponseDTO.failure("Failed to create order: unknown error", requestIndex);
            }

            logger.debug("Order created successfully at index {} with ID {}", requestIndex, createdOrder.getId());
            return OrderPostResponseDTO.success(createdOrder, createdOrder.getId().longValue(), requestIndex);

        } catch (Exception e) {
            logger.error("Unexpected error processing order at index {}: {}", requestIndex, e.getMessage(), e);
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