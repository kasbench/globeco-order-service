package org.kasbench.globeco_order_service.service;

import lombok.RequiredArgsConstructor;
import org.kasbench.globeco_order_service.dto.*;
import org.kasbench.globeco_order_service.entity.*;
import org.kasbench.globeco_order_service.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final BlotterRepository blotterRepository;
    private final StatusRepository statusRepository;
    private final OrderTypeRepository orderTypeRepository;
    private final RestTemplate restTemplate;

    public List<OrderWithDetailsDTO> getAll() {
        return orderRepository.findAll().stream()
                .map(this::toWithDetailsDTO)
                .collect(Collectors.toList());
    }

    public Optional<OrderWithDetailsDTO> getById(Integer id) {
        return orderRepository.findById(id).map(this::toWithDetailsDTO);
    }

    @Transactional
    public OrderWithDetailsDTO create(OrderPostDTO dto) {
        Order order = new Order();
        if (dto.getBlotterId() != null) {
            blotterRepository.findById(dto.getBlotterId()).ifPresent(order::setBlotter);
        }
        order.setStatus(statusRepository.findById(dto.getStatusId()).orElseThrow());
        order.setPortfolioId(dto.getPortfolioId());
        order.setOrderType(orderTypeRepository.findById(dto.getOrderTypeId()).orElseThrow());
        order.setSecurityId(dto.getSecurityId());
        order.setQuantity(dto.getQuantity());
        order.setLimitPrice(dto.getLimitPrice());
        order.setOrderTimestamp(dto.getOrderTimestamp());
        order.setVersion(dto.getVersion());
        return toWithDetailsDTO(orderRepository.save(order));
    }

    @Transactional
    public Optional<OrderWithDetailsDTO> update(Integer id, OrderDTO dto) {
        return orderRepository.findById(id).map(existing -> {
            if (dto.getBlotterId() != null) {
                blotterRepository.findById(dto.getBlotterId()).ifPresent(existing::setBlotter);
            } else {
                existing.setBlotter(null);
            }
            existing.setStatus(statusRepository.findById(dto.getStatusId()).orElseThrow());
            existing.setPortfolioId(dto.getPortfolioId());
            existing.setOrderType(orderTypeRepository.findById(dto.getOrderTypeId()).orElseThrow());
            existing.setSecurityId(dto.getSecurityId());
            existing.setQuantity(dto.getQuantity());
            existing.setLimitPrice(dto.getLimitPrice());
            existing.setOrderTimestamp(dto.getOrderTimestamp());
            existing.setVersion(dto.getVersion());
            return toWithDetailsDTO(orderRepository.save(existing));
        });
    }

    @Transactional
    public boolean delete(Integer id, Integer version) {
        return orderRepository.findById(id).map(existing -> {
            if (!existing.getVersion().equals(version)) {
                return false;
            }
            orderRepository.deleteById(id);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean submitOrder(Integer id) {
        Order order = orderRepository.findById(id).orElseThrow();
        if (!order.getStatus().getAbbreviation().equals("NEW")) {
            return false;
        }
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
        String url = "http://localhost:8082/api/v1/tradeOrders";
        HttpEntity<TradeOrderPostDTO> request = new HttpEntity<>(tradeOrder);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        if (response.getStatusCode() == HttpStatus.CREATED || response.getStatusCode() == HttpStatus.OK) {
            // Update status to SENT
            Status sentStatus = statusRepository.findAll().stream()
                .filter(s -> s.getAbbreviation().equals("SENT"))
                .findFirst().orElseThrow();
            order.setStatus(sentStatus);
            orderRepository.save(order);
            return true;
        }
        return false;
    }

    private OrderWithDetailsDTO toWithDetailsDTO(Order order) {
        return OrderWithDetailsDTO.builder()
                .id(order.getId())
                .blotter(order.getBlotter() != null ? toBlotterDTO(order.getBlotter()) : null)
                .status(toStatusDTO(order.getStatus()))
                .portfolioId(order.getPortfolioId())
                .orderType(toOrderTypeDTO(order.getOrderType()))
                .securityId(order.getSecurityId())
                .quantity(order.getQuantity())
                .limitPrice(order.getLimitPrice())
                .orderTimestamp(order.getOrderTimestamp())
                .version(order.getVersion())
                .build();
    }

    private BlotterDTO toBlotterDTO(Blotter blotter) {
        return BlotterDTO.builder()
                .id(blotter.getId())
                .name(blotter.getName())
                .version(blotter.getVersion())
                .build();
    }

    private StatusDTO toStatusDTO(Status status) {
        return StatusDTO.builder()
                .id(status.getId())
                .abbreviation(status.getAbbreviation())
                .description(status.getDescription())
                .version(status.getVersion())
                .build();
    }

    private OrderTypeDTO toOrderTypeDTO(OrderType orderType) {
        return OrderTypeDTO.builder()
                .id(orderType.getId())
                .abbreviation(orderType.getAbbreviation())
                .description(orderType.getDescription())
                .version(orderType.getVersion())
                .build();
    }
} 