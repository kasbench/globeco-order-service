package org.kasbench.globeco_order_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.dto.StatusDTO;
import org.kasbench.globeco_order_service.dto.StatusPostDTO;
import org.kasbench.globeco_order_service.service.StatusService;
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

@WebMvcTest(StatusController.class)
public class StatusControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StatusService statusService;

    @Autowired
    private ObjectMapper objectMapper;

    private StatusDTO statusDTO;
    private StatusPostDTO statusPostDTO;

    @BeforeEach
    void setUp() {
        statusDTO = StatusDTO.builder().id(1).abbreviation("NEW").description("New").version(1).build();
        statusPostDTO = StatusPostDTO.builder().abbreviation("NEW").description("New").version(1).build();
    }

    @Test
    void testGetAllStatuses() throws Exception {
        Mockito.when(statusService.getAll()).thenReturn(Arrays.asList(statusDTO));
        mockMvc.perform(get("/api/v1/statuses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].abbreviation").value("NEW"));
    }

    @Test
    void testGetStatusById_found() throws Exception {
        Mockito.when(statusService.getById(1)).thenReturn(Optional.of(statusDTO));
        mockMvc.perform(get("/api/v1/status/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abbreviation").value("NEW"));
    }

    @Test
    void testGetStatusById_notFound() throws Exception {
        Mockito.when(statusService.getById(1)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/status/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateStatus() throws Exception {
        Mockito.when(statusService.create(any(StatusPostDTO.class))).thenReturn(statusDTO);
        mockMvc.perform(post("/api/v1/statuses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusPostDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abbreviation").value("NEW"));
    }

    @Test
    void testUpdateStatus_found() throws Exception {
        Mockito.when(statusService.update(eq(1), any(StatusDTO.class))).thenReturn(Optional.of(statusDTO));
        mockMvc.perform(put("/api/v1/status/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abbreviation").value("NEW"));
    }

    @Test
    void testUpdateStatus_notFound() throws Exception {
        Mockito.when(statusService.update(eq(1), any(StatusDTO.class))).thenReturn(Optional.empty());
        mockMvc.perform(put("/api/v1/status/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusDTO)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteStatus_success() throws Exception {
        Mockito.when(statusService.delete(1, 1)).thenReturn(true);
        mockMvc.perform(delete("/api/v1/status/1?version=1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteStatus_conflict() throws Exception {
        Mockito.when(statusService.delete(1, 1)).thenReturn(false);
        mockMvc.perform(delete("/api/v1/status/1?version=1"))
                .andExpect(status().isConflict());
    }
} 