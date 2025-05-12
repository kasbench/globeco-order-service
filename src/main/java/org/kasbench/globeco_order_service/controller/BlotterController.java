package org.kasbench.globeco_order_service.controller;

import lombok.RequiredArgsConstructor;
import org.kasbench.globeco_order_service.dto.BlotterDTO;
import org.kasbench.globeco_order_service.dto.BlotterPostDTO;
import org.kasbench.globeco_order_service.service.BlotterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BlotterController {
    private final BlotterService blotterService;

    @GetMapping("/blotters")
    public List<BlotterDTO> getAllBlotters() {
        return blotterService.getAll();
    }

    @GetMapping("/blotter/{id}")
    public ResponseEntity<BlotterDTO> getBlotterById(@PathVariable Integer id) {
        return blotterService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/blotters")
    public ResponseEntity<BlotterDTO> createBlotter(@RequestBody BlotterPostDTO dto) {
        BlotterDTO created = blotterService.create(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/blotter/{id}")
    public ResponseEntity<BlotterDTO> updateBlotter(@PathVariable Integer id, @RequestBody BlotterDTO dto) {
        return blotterService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/blotter/{id}")
    public ResponseEntity<Void> deleteBlotter(@PathVariable Integer id, @RequestParam Integer version) {
        boolean deleted = blotterService.delete(id, version);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.status(409).build(); // Conflict if version mismatch
        }
    }
} 