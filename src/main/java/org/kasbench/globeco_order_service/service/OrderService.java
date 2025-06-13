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
import org.kasbench.globeco_order_service.repository.OrderRepository;
import org.kasbench.globeco_order_service.repository.StatusRepository;
import org.kasbench.globeco_order_service.repository.BlotterRepository;
import org.kasbench.globeco_order_service.repository.OrderTypeRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.kasbench.globeco_order_service.dto.OrderWithDetailsDTO;
import org.kasbench.globeco_order_service.dto.OrderPostDTO;
import org.kasbench.globeco_order_service.dto.OrderDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import org.kasbench.globeco_order_service.service.SecurityCacheService;
import org.kasbench.globeco_order_service.service.PortfolioCacheService;
import org.kasbench.globeco_order_service.dto.SecurityDTO;
import org.kasbench.globeco_order_service.dto.PortfolioDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.util.Map;

@Service
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_BATCH_SIZE = 1000;
    
    private final OrderRepository orderRepository;
    private final StatusRepository statusRepository;
    private final BlotterRepository blotterRepository;
    private final OrderTypeRepository orderTypeRepository;
    private final RestTemplate restTemplate;
    private final SecurityCacheService securityCacheService;
    private final PortfolioCacheService portfolioCacheService;

    public OrderService(
        OrderRepository orderRepository,
        StatusRepository statusRepository,
        BlotterRepository blotterRepository,
        OrderTypeRepository orderTypeRepository,
        RestTemplate restTemplate,
        SecurityCacheService securityCacheService,
        PortfolioCacheService portfolioCacheService
    ) {
        this.orderRepository = orderRepository;
        this.statusRepository = statusRepository;
        this.blotterRepository = blotterRepository;
        this.orderTypeRepository = orderTypeRepository;
        this.restTemplate = restTemplate;
        this.securityCacheService = securityCacheService;
        this.portfolioCacheService = portfolioCacheService;
    }

    @Transactional
    public OrderDTO submitOrder(Integer id) {
        Order order = orderRepository.findById(id).orElseThrow();
        if (!order.getStatus().getAbbreviation().equals("NEW")) {
            return null;
        }
        TradeOrderPostDTO tradeOrder = TradeOrderPostDTO.builder()
                .orderId(id)
                .portfolioId(order.getPortfolioId())
                .orderType(order.getOrderType().getAbbreviation())
                .securityId(order.getSecurityId())
                .quantity(order.getQuantity())
                .limitPrice(order.getLimitPrice())
                .build();
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://globeco-trade-service:8082/api/v1/tradeOrders",
                new HttpEntity<>(tradeOrder),
                String.class
        );
        Integer tradeOrderId = null;
        if (response.getStatusCode() == HttpStatus.CREATED) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(response.getBody());
                if (node.has("id") && node.get("id").isInt()) {
                    tradeOrderId = node.get("id").asInt();
                }
                System.out.println("DEBUG: parsed tradeOrderId: " + tradeOrderId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse trade service response", e);
            }
        }
        Status sentStatus = statusRepository.findAll().stream()
                .filter(s -> "SENT".equals(s.getAbbreviation()))
                .findFirst()
                .orElseThrow();
        if (tradeOrderId == null) {
            return null;
        }
        // Create a new Order instance with updated tradeOrderId and status
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
        System.out.println("DEBUG: about to save order: " + updatedOrder);
        Order savedOrder = orderRepository.save(updatedOrder);
        return toOrderDTO(savedOrder);
    }

    // Complete mapping from Order to OrderWithDetailsDTO
    private OrderWithDetailsDTO toDto(Order order) {
        if (order == null) return null;
        
        // Fetch security information from cache
        SecurityDTO security = null;
        if (order.getSecurityId() != null) {
            try {
                security = securityCacheService.getSecurityBySecurityId(order.getSecurityId());
                if (security == null) {
                    // Create a fallback SecurityDTO with just the ID if service is unavailable
                    security = SecurityDTO.builder()
                            .securityId(order.getSecurityId())
                            .ticker(null)  // Will be null if service is unavailable
                            .build();
                    logger.debug("Security service unavailable for securityId: {}, using fallback", order.getSecurityId());
                }
            } catch (Exception e) {
                logger.warn("Failed to fetch security data for securityId: {} - {}", order.getSecurityId(), e.getMessage());
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
                            .name(null)  // Will be null if service is unavailable
                            .build();
                    logger.debug("Portfolio service unavailable for portfolioId: {}, using fallback", order.getPortfolioId());
                }
            } catch (Exception e) {
                logger.warn("Failed to fetch portfolio data for portfolioId: {} - {}", order.getPortfolioId(), e.getMessage());
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
        if (blotter == null) return null;
        return BlotterDTO.builder()
                .id(blotter.getId())
                .name(blotter.getName())
                .version(blotter.getVersion())
                .build();
    }

    // Helper method to convert Status entity to StatusDTO
    private StatusDTO toStatusDTO(Status status) {
        if (status == null) return null;
        return StatusDTO.builder()
                .id(status.getId())
                .abbreviation(status.getAbbreviation())
                .description(status.getDescription())
                .version(status.getVersion())
                .build();
    }

    // Helper method to convert OrderType entity to OrderTypeDTO
    private OrderTypeDTO toOrderTypeDTO(OrderType orderType) {
        if (orderType == null) return null;
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
     * 
     * @param limit Maximum number of results to return
     * @param offset Number of results to skip
     * @param sort Comma-separated list of sort fields with optional direction prefix
     * @param filterParams Map of filter field names to comma-separated values
     * @return Page of orders with pagination metadata
     */
    public Page<OrderWithDetailsDTO> getAll(Integer limit, Integer offset, String sort, Map<String, String> filterParams) {
        logger.debug("Getting orders with limit={}, offset={}, sort={}, filters={}", 
                limit, offset, sort, filterParams);
        
        // Create Pageable with sorting
        Sort sortSpec = SortingSpecification.parseSort(sort);
        Pageable pageable = PageRequest.of(offset / limit, limit, sortSpec);
        
        // Create filtering specification
        Specification<Order> filterSpec = FilteringSpecification.createFilterSpecification(filterParams);
        
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
        if (order == null) return null;
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
     * This method processes each order individually, continuing even if some orders fail.
     * 
     * @param orders List of OrderPostDTO to process (max 1000)
     * @return OrderListResponseDTO containing results for all orders
     */
    @Transactional
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
        
        // Process each order individually
        for (int i = 0; i < orders.size(); i++) {
            OrderPostDTO orderDto = orders.get(i);
            OrderPostResponseDTO result = processIndividualOrder(orderDto, i);
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
            logger.info("Failure breakdown: {} validation errors, {} reference errors, {} creation errors, {} exceptions",
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
     * Process a single order within a batch context.
     * This method handles all individual order validation and processing logic.
     * 
     * @param orderDto The order to process
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
                logger.info("Order creation returned null at index {}: Failed to create order - unknown error", requestIndex);
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
    
    /**
     * Validate that referenced entities exist in the database.
     * 
     * @param dto The order DTO to validate
     * @return Error message if validation fails, null if valid
     */
    private String validateOrderReferences(OrderPostDTO dto) {
        // Check if blotter exists
        if (!blotterRepository.existsById(dto.getBlotterId())) {
            return String.format("Blotter with ID %d not found", dto.getBlotterId());
        }
        
        // Check if status exists
        if (!statusRepository.existsById(dto.getStatusId())) {
            return String.format("Status with ID %d not found", dto.getStatusId());
        }
        
        // Check if order type exists
        if (!orderTypeRepository.existsById(dto.getOrderTypeId())) {
            return String.format("Order Type with ID %d not found", dto.getOrderTypeId());
        }
        
        return null; // Valid
    }
} 