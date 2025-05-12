package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.dto.OrderTypeDTO;
import org.kasbench.globeco_order_service.dto.OrderTypePostDTO;
import org.kasbench.globeco_order_service.entity.OrderType;
import org.kasbench.globeco_order_service.repository.OrderTypeRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class OrderTypeServiceTest {
    @Mock
    private OrderTypeRepository orderTypeRepository;

    @InjectMocks
    private OrderTypeService orderTypeService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAll() {
        OrderType o1 = OrderType.builder().id(1).abbreviation("BUY").description("Buy").version(1).build();
        OrderType o2 = OrderType.builder().id(2).abbreviation("SELL").description("Sell").version(1).build();
        when(orderTypeRepository.findAll()).thenReturn(Arrays.asList(o1, o2));
        List<OrderTypeDTO> result = orderTypeService.getAll();
        assertEquals(2, result.size());
        assertEquals("BUY", result.get(0).getAbbreviation());
    }

    @Test
    void testGetById_found() {
        OrderType o = OrderType.builder().id(1).abbreviation("BUY").description("Buy").version(1).build();
        when(orderTypeRepository.findById(1)).thenReturn(Optional.of(o));
        Optional<OrderTypeDTO> result = orderTypeService.getById(1);
        assertTrue(result.isPresent());
        assertEquals("BUY", result.get().getAbbreviation());
    }

    @Test
    void testGetById_notFound() {
        when(orderTypeRepository.findById(1)).thenReturn(Optional.empty());
        Optional<OrderTypeDTO> result = orderTypeService.getById(1);
        assertFalse(result.isPresent());
    }

    @Test
    void testCreate() {
        OrderTypePostDTO dto = OrderTypePostDTO.builder().abbreviation("BUY").description("Buy").version(1).build();
        OrderType saved = OrderType.builder().id(1).abbreviation("BUY").description("Buy").version(1).build();
        when(orderTypeRepository.save(any(OrderType.class))).thenReturn(saved);
        OrderTypeDTO result = orderTypeService.create(dto);
        assertEquals("BUY", result.getAbbreviation());
        assertEquals(1, result.getId());
    }

    @Test
    void testUpdate_found() {
        OrderType existing = OrderType.builder().id(1).abbreviation("OLD").description("Old").version(1).build();
        OrderTypeDTO dto = OrderTypeDTO.builder().id(1).abbreviation("BUY").description("Buy").version(2).build();
        when(orderTypeRepository.findById(1)).thenReturn(Optional.of(existing));
        when(orderTypeRepository.save(any(OrderType.class))).thenReturn(existing);
        Optional<OrderTypeDTO> result = orderTypeService.update(1, dto);
        assertTrue(result.isPresent());
        assertEquals("BUY", result.get().getAbbreviation());
        assertEquals(2, result.get().getVersion());
    }

    @Test
    void testUpdate_notFound() {
        OrderTypeDTO dto = OrderTypeDTO.builder().id(1).abbreviation("BUY").description("Buy").version(2).build();
        when(orderTypeRepository.findById(1)).thenReturn(Optional.empty());
        Optional<OrderTypeDTO> result = orderTypeService.update(1, dto);
        assertFalse(result.isPresent());
    }

    @Test
    void testDelete_success() {
        OrderType existing = OrderType.builder().id(1).abbreviation("BUY").description("Buy").version(1).build();
        when(orderTypeRepository.findById(1)).thenReturn(Optional.of(existing));
        doNothing().when(orderTypeRepository).deleteById(1);
        boolean result = orderTypeService.delete(1, 1);
        assertTrue(result);
    }

    @Test
    void testDelete_versionMismatch() {
        OrderType existing = OrderType.builder().id(1).abbreviation("BUY").description("Buy").version(2).build();
        when(orderTypeRepository.findById(1)).thenReturn(Optional.of(existing));
        boolean result = orderTypeService.delete(1, 1);
        assertFalse(result);
    }

    @Test
    void testDelete_notFound() {
        when(orderTypeRepository.findById(1)).thenReturn(Optional.empty());
        boolean result = orderTypeService.delete(1, 1);
        assertFalse(result);
    }
} 