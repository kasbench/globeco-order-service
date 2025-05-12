package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.dto.BlotterDTO;
import org.kasbench.globeco_order_service.dto.BlotterPostDTO;
import org.kasbench.globeco_order_service.entity.Blotter;
import org.kasbench.globeco_order_service.repository.BlotterRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BlotterServiceTest {
    @Mock
    private BlotterRepository blotterRepository;

    @InjectMocks
    private BlotterService blotterService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAll() {
        Blotter b1 = Blotter.builder().id(1).name("Default").version(1).build();
        Blotter b2 = Blotter.builder().id(2).name("Equity").version(1).build();
        when(blotterRepository.findAll()).thenReturn(Arrays.asList(b1, b2));
        List<BlotterDTO> result = blotterService.getAll();
        assertEquals(2, result.size());
        assertEquals("Default", result.get(0).getName());
    }

    @Test
    void testGetById_found() {
        Blotter b = Blotter.builder().id(1).name("Default").version(1).build();
        when(blotterRepository.findById(1)).thenReturn(Optional.of(b));
        Optional<BlotterDTO> result = blotterService.getById(1);
        assertTrue(result.isPresent());
        assertEquals("Default", result.get().getName());
    }

    @Test
    void testGetById_notFound() {
        when(blotterRepository.findById(1)).thenReturn(Optional.empty());
        Optional<BlotterDTO> result = blotterService.getById(1);
        assertFalse(result.isPresent());
    }

    @Test
    void testCreate() {
        BlotterPostDTO dto = BlotterPostDTO.builder().name("Default").version(1).build();
        Blotter saved = Blotter.builder().id(1).name("Default").version(1).build();
        when(blotterRepository.save(any(Blotter.class))).thenReturn(saved);
        BlotterDTO result = blotterService.create(dto);
        assertEquals("Default", result.getName());
        assertEquals(1, result.getId());
    }

    @Test
    void testUpdate_found() {
        Blotter existing = Blotter.builder().id(1).name("Old").version(1).build();
        BlotterDTO dto = BlotterDTO.builder().id(1).name("Default").version(2).build();
        when(blotterRepository.findById(1)).thenReturn(Optional.of(existing));
        when(blotterRepository.save(any(Blotter.class))).thenReturn(existing);
        Optional<BlotterDTO> result = blotterService.update(1, dto);
        assertTrue(result.isPresent());
        assertEquals("Default", result.get().getName());
        assertEquals(2, result.get().getVersion());
    }

    @Test
    void testUpdate_notFound() {
        BlotterDTO dto = BlotterDTO.builder().id(1).name("Default").version(2).build();
        when(blotterRepository.findById(1)).thenReturn(Optional.empty());
        Optional<BlotterDTO> result = blotterService.update(1, dto);
        assertFalse(result.isPresent());
    }

    @Test
    void testDelete_success() {
        Blotter existing = Blotter.builder().id(1).name("Default").version(1).build();
        when(blotterRepository.findById(1)).thenReturn(Optional.of(existing));
        doNothing().when(blotterRepository).deleteById(1);
        boolean result = blotterService.delete(1, 1);
        assertTrue(result);
    }

    @Test
    void testDelete_versionMismatch() {
        Blotter existing = Blotter.builder().id(1).name("Default").version(2).build();
        when(blotterRepository.findById(1)).thenReturn(Optional.of(existing));
        boolean result = blotterService.delete(1, 1);
        assertFalse(result);
    }

    @Test
    void testDelete_notFound() {
        when(blotterRepository.findById(1)).thenReturn(Optional.empty());
        boolean result = blotterService.delete(1, 1);
        assertFalse(result);
    }
} 