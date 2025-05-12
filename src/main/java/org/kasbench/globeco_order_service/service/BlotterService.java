package org.kasbench.globeco_order_service.service;

import lombok.RequiredArgsConstructor;
import org.kasbench.globeco_order_service.dto.BlotterDTO;
import org.kasbench.globeco_order_service.dto.BlotterPostDTO;
import org.kasbench.globeco_order_service.entity.Blotter;
import org.kasbench.globeco_order_service.repository.BlotterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlotterService {
    private final BlotterRepository blotterRepository;

    public List<BlotterDTO> getAll() {
        return blotterRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public Optional<BlotterDTO> getById(Integer id) {
        return blotterRepository.findById(id).map(this::toDTO);
    }

    @Transactional
    public BlotterDTO create(BlotterPostDTO dto) {
        Blotter blotter = Blotter.builder()
                .name(dto.getName())
                .version(dto.getVersion())
                .build();
        return toDTO(blotterRepository.save(blotter));
    }

    @Transactional
    public Optional<BlotterDTO> update(Integer id, BlotterDTO dto) {
        return blotterRepository.findById(id).map(existing -> {
            existing.setName(dto.getName());
            existing.setVersion(dto.getVersion());
            return toDTO(blotterRepository.save(existing));
        });
    }

    @Transactional
    public boolean delete(Integer id, Integer version) {
        return blotterRepository.findById(id).map(existing -> {
            if (!existing.getVersion().equals(version)) {
                return false;
            }
            blotterRepository.deleteById(id);
            return true;
        }).orElse(false);
    }

    private BlotterDTO toDTO(Blotter blotter) {
        return BlotterDTO.builder()
                .id(blotter.getId())
                .name(blotter.getName())
                .version(blotter.getVersion())
                .build();
    }
} 