package org.kasbench.globeco_order_service.controller;

import lombok.RequiredArgsConstructor;
import org.kasbench.globeco_order_service.dto.StatusDTO;
import org.kasbench.globeco_order_service.dto.StatusPostDTO;
import org.kasbench.globeco_order_service.service.StatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StatusController {
    private final StatusService statusService;

    @GetMapping("/statuses")
    public List<StatusDTO> getAllStatuses() {
        return statusService.getAll();
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<StatusDTO> getStatusById(@PathVariable Integer id) {
        return statusService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/statuses")
    public ResponseEntity<StatusDTO> createStatus(@RequestBody StatusPostDTO dto) {
        StatusDTO created = statusService.create(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/status/{id}")
    public ResponseEntity<StatusDTO> updateStatus(@PathVariable Integer id, @RequestBody StatusDTO dto) {
        return statusService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/status/{id}")
    public ResponseEntity<Void> deleteStatus(@PathVariable Integer id, @RequestParam Integer version) {
        boolean deleted = statusService.delete(id, version);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.status(409).build(); // Conflict if version mismatch
        }
    }
} 