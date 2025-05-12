package org.kasbench.globeco_order_service.service;

import lombok.RequiredArgsConstructor;
import org.kasbench.globeco_order_service.dto.OrderTypeDTO;
import org.kasbench.globeco_order_service.dto.OrderTypePostDTO;
import org.kasbench.globeco_order_service.entity.OrderType;
import org.kasbench.globeco_order_service.repository.OrderTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderTypeService {
    private final OrderTypeRepository orderTypeRepository;

    public List<OrderTypeDTO> getAll() {
        return orderTypeRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public Optional<OrderTypeDTO> getById(Integer id) {
        return orderTypeRepository.findById(id).map(this::toDTO);
    }

    @Transactional
    public OrderTypeDTO create(OrderTypePostDTO dto) {
        OrderType orderType = OrderType.builder()
                .abbreviation(dto.getAbbreviation())
                .description(dto.getDescription())
                .version(dto.getVersion())
                .build();
        return toDTO(orderTypeRepository.save(orderType));
    }

    @Transactional
    public Optional<OrderTypeDTO> update(Integer id, OrderTypeDTO dto) {
        return orderTypeRepository.findById(id).map(existing -> {
            existing.setAbbreviation(dto.getAbbreviation());
            existing.setDescription(dto.getDescription());
            existing.setVersion(dto.getVersion());
            return toDTO(orderTypeRepository.save(existing));
        });
    }

    @Transactional
    public boolean delete(Integer id, Integer version) {
        return orderTypeRepository.findById(id).map(existing -> {
            if (!existing.getVersion().equals(version)) {
                return false;
            }
            orderTypeRepository.deleteById(id);
            return true;
        }).orElse(false);
    }

    private OrderTypeDTO toDTO(OrderType orderType) {
        return OrderTypeDTO.builder()
                .id(orderType.getId())
                .abbreviation(orderType.getAbbreviation())
                .description(orderType.getDescription())
                .version(orderType.getVersion())
                .build();
    }
} 