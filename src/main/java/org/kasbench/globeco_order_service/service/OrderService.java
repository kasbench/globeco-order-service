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

@Service
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_BATCH_SIZE = 1000;
    
    private final OrderRepository orderRepository;
    private final StatusRepository statusRepository;
    private final BlotterRepository blotterRepository;
    private final OrderTypeRepository orderTypeRepository;
    private final RestTemplate restTemplate;

    public OrderService(
        OrderRepository orderRepository,
        StatusRepository statusRepository,
        BlotterRepository blotterRepository,
        OrderTypeRepository orderTypeRepository,
        RestTemplate restTemplate
    ) {
        this.orderRepository = orderRepository;
        this.statusRepository = statusRepository;
        this.blotterRepository = blotterRepository;
        this.orderTypeRepository = orderTypeRepository;
        this.restTemplate = restTemplate;
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
        
        return OrderWithDetailsDTO.builder()
                .id(order.getId())
                .blotter(toBlotterDTO(order.getBlotter()))
                .status(toStatusDTO(order.getStatus()))
                .portfolioId(order.getPortfolioId())
                .orderType(toOrderTypeDTO(order.getOrderType()))
                .securityId(order.getSecurityId())
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

    public Optional<OrderWithDetailsDTO> getById(Integer id) {
        return orderRepository.findById(id).map(this::toDto);
    }

    public OrderWithDetailsDTO create(OrderPostDTO dto) {
        Order order = new Order();
        // Basic field copying for test compatibility
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
        
        // Process each order individually
        for (int i = 0; i < orders.size(); i++) {
            OrderPostDTO orderDto = orders.get(i);
            OrderPostResponseDTO result = processIndividualOrder(orderDto, i);
            orderResults.add(result);
            
            if (result.isSuccess()) {
                successCount++;
            } else {
                failureCount++;
            }
        }
        
        long endTime = System.currentTimeMillis();
        long processingTime = endTime - startTime;
        
        // Log batch processing metrics
        logger.info("Batch processing completed: {} total, {} successful, {} failed, {}ms processing time",
                orders.size(), successCount, failureCount, processingTime);
        
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
                logger.debug("Order validation failed at index {}: {}", requestIndex, validationError);
                return OrderPostResponseDTO.failure(validationError, requestIndex);
            }
            
            // Validate foreign key references exist
            String referenceError = validateOrderReferences(orderDto);
            if (referenceError != null) {
                logger.debug("Order reference validation failed at index {}: {}", requestIndex, referenceError);
                return OrderPostResponseDTO.failure(referenceError, requestIndex);
            }
            
            // Create the order using existing logic
            OrderWithDetailsDTO createdOrder = create(orderDto);
            if (createdOrder == null) {
                logger.warn("Order creation returned null at index {}", requestIndex);
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
        
        if (dto.getLimitPrice() == null || dto.getLimitPrice().signum() <= 0) {
            return "Limit price must be positive";
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