package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.dto.StatusDTO;
import org.kasbench.globeco_order_service.dto.StatusPostDTO;
import org.kasbench.globeco_order_service.entity.Status;
import org.kasbench.globeco_order_service.repository.StatusRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class StatusServiceTest {
    @Mock
    private StatusRepository statusRepository;

    @InjectMocks
    private StatusService statusService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAll() {
        Status s1 = Status.builder().id(1).abbreviation("NEW").description("New").version(1).build();
        Status s2 = Status.builder().id(2).abbreviation("SENT").description("Sent").version(1).build();
        when(statusRepository.findAll()).thenReturn(Arrays.asList(s1, s2));
        List<StatusDTO> result = statusService.getAll();
        assertEquals(2, result.size());
        assertEquals("NEW", result.get(0).getAbbreviation());
    }

    @Test
    void testGetById_found() {
        Status s = Status.builder().id(1).abbreviation("NEW").description("New").version(1).build();
        when(statusRepository.findById(1)).thenReturn(Optional.of(s));
        Optional<StatusDTO> result = statusService.getById(1);
        assertTrue(result.isPresent());
        assertEquals("NEW", result.get().getAbbreviation());
    }

    @Test
    void testGetById_notFound() {
        when(statusRepository.findById(1)).thenReturn(Optional.empty());
        Optional<StatusDTO> result = statusService.getById(1);
        assertFalse(result.isPresent());
    }

    @Test
    void testCreate() {
        StatusPostDTO dto = StatusPostDTO.builder().abbreviation("NEW").description("New").version(1).build();
        Status saved = Status.builder().id(1).abbreviation("NEW").description("New").version(1).build();
        when(statusRepository.save(any(Status.class))).thenReturn(saved);
        StatusDTO result = statusService.create(dto);
        assertEquals("NEW", result.getAbbreviation());
        assertEquals(1, result.getId());
    }

    @Test
    void testUpdate_found() {
        Status existing = Status.builder().id(1).abbreviation("OLD").description("Old").version(1).build();
        StatusDTO dto = StatusDTO.builder().id(1).abbreviation("NEW").description("New").version(2).build();
        when(statusRepository.findById(1)).thenReturn(Optional.of(existing));
        when(statusRepository.save(any(Status.class))).thenReturn(existing);
        Optional<StatusDTO> result = statusService.update(1, dto);
        assertTrue(result.isPresent());
        assertEquals("NEW", result.get().getAbbreviation());
        assertEquals(2, result.get().getVersion());
    }

    @Test
    void testUpdate_notFound() {
        StatusDTO dto = StatusDTO.builder().id(1).abbreviation("NEW").description("New").version(2).build();
        when(statusRepository.findById(1)).thenReturn(Optional.empty());
        Optional<StatusDTO> result = statusService.update(1, dto);
        assertFalse(result.isPresent());
    }

    @Test
    void testDelete_success() {
        Status existing = Status.builder().id(1).abbreviation("NEW").description("New").version(1).build();
        when(statusRepository.findById(1)).thenReturn(Optional.of(existing));
        doNothing().when(statusRepository).deleteById(1);
        boolean result = statusService.delete(1, 1);
        assertTrue(result);
    }

    @Test
    void testDelete_versionMismatch() {
        Status existing = Status.builder().id(1).abbreviation("NEW").description("New").version(2).build();
        when(statusRepository.findById(1)).thenReturn(Optional.of(existing));
        boolean result = statusService.delete(1, 1);
        assertFalse(result);
    }

    @Test
    void testDelete_notFound() {
        when(statusRepository.findById(1)).thenReturn(Optional.empty());
        boolean result = statusService.delete(1, 1);
        assertFalse(result);
    }
} 