package org.kasbench.globeco_order_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.dto.BlotterDTO;
import org.kasbench.globeco_order_service.dto.BlotterPostDTO;
import org.kasbench.globeco_order_service.service.BlotterService;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BlotterController.class)
public class BlotterControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BlotterService blotterService;

    @Autowired
    private ObjectMapper objectMapper;

    private BlotterDTO blotterDTO;
    private BlotterPostDTO blotterPostDTO;

    @BeforeEach
    void setUp() {
        blotterDTO = BlotterDTO.builder().id(1).name("Default").version(1).build();
        blotterPostDTO = BlotterPostDTO.builder().name("Default").version(1).build();
    }

    @Test
    void testGetAllBlotters() throws Exception {
        Mockito.when(blotterService.getAll()).thenReturn(Arrays.asList(blotterDTO));
        mockMvc.perform(get("/api/v1/blotters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Default"));
    }

    @Test
    void testGetBlotterById_found() throws Exception {
        Mockito.when(blotterService.getById(1)).thenReturn(Optional.of(blotterDTO));
        mockMvc.perform(get("/api/v1/blotter/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Default"));
    }

    @Test
    void testGetBlotterById_notFound() throws Exception {
        Mockito.when(blotterService.getById(1)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/blotter/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateBlotter() throws Exception {
        Mockito.when(blotterService.create(any(BlotterPostDTO.class))).thenReturn(blotterDTO);
        mockMvc.perform(post("/api/v1/blotters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blotterPostDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Default"));
    }

    @Test
    void testUpdateBlotter_found() throws Exception {
        Mockito.when(blotterService.update(eq(1), any(BlotterDTO.class))).thenReturn(Optional.of(blotterDTO));
        mockMvc.perform(put("/api/v1/blotter/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blotterDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Default"));
    }

    @Test
    void testUpdateBlotter_notFound() throws Exception {
        Mockito.when(blotterService.update(eq(1), any(BlotterDTO.class))).thenReturn(Optional.empty());
        mockMvc.perform(put("/api/v1/blotter/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blotterDTO)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteBlotter_success() throws Exception {
        Mockito.when(blotterService.delete(1, 1)).thenReturn(true);
        mockMvc.perform(delete("/api/v1/blotter/1?version=1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteBlotter_conflict() throws Exception {
        Mockito.when(blotterService.delete(1, 1)).thenReturn(false);
        mockMvc.perform(delete("/api/v1/blotter/1?version=1"))
                .andExpect(status().isConflict());
    }
} 