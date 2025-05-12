package org.kasbench.globeco_order_service.service;

import lombok.RequiredArgsConstructor;
import org.kasbench.globeco_order_service.dto.StatusDTO;
import org.kasbench.globeco_order_service.dto.StatusPostDTO;
import org.kasbench.globeco_order_service.entity.Status;
import org.kasbench.globeco_order_service.repository.StatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatusService {
    private final StatusRepository statusRepository;

    public List<StatusDTO> getAll() {
        return statusRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public Optional<StatusDTO> getById(Integer id) {
        return statusRepository.findById(id).map(this::toDTO);
    }

    @Transactional
    public StatusDTO create(StatusPostDTO dto) {
        Status status = Status.builder()
                .abbreviation(dto.getAbbreviation())
                .description(dto.getDescription())
                .version(dto.getVersion())
                .build();
        return toDTO(statusRepository.save(status));
    }

    @Transactional
    public Optional<StatusDTO> update(Integer id, StatusDTO dto) {
        return statusRepository.findById(id).map(existing -> {
            existing.setAbbreviation(dto.getAbbreviation());
            existing.setDescription(dto.getDescription());
            existing.setVersion(dto.getVersion());
            return toDTO(statusRepository.save(existing));
        });
    }

    @Transactional
    public boolean delete(Integer id, Integer version) {
        return statusRepository.findById(id).map(existing -> {
            if (!existing.getVersion().equals(version)) {
                return false;
            }
            statusRepository.deleteById(id);
            return true;
        }).orElse(false);
    }

    private StatusDTO toDTO(Status status) {
        return StatusDTO.builder()
                .id(status.getId())
                .abbreviation(status.getAbbreviation())
                .description(status.getDescription())
                .version(status.getVersion())
                .build();
    }
} 