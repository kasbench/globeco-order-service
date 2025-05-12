package org.kasbench.globeco_order_service.controller;

import lombok.RequiredArgsConstructor;
import org.kasbench.globeco_order_service.dto.OrderTypeDTO;
import org.kasbench.globeco_order_service.dto.OrderTypePostDTO;
import org.kasbench.globeco_order_service.service.OrderTypeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderTypeController {
    private final OrderTypeService orderTypeService;

    @GetMapping("/orderTypes")
    public List<OrderTypeDTO> getAllOrderTypes() {
        return orderTypeService.getAll();
    }

    @GetMapping("/orderTypes/{id}")
    public ResponseEntity<OrderTypeDTO> getOrderTypeById(@PathVariable Integer id) {
        return orderTypeService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/orderTypes")
    public ResponseEntity<OrderTypeDTO> createOrderType(@RequestBody OrderTypePostDTO dto) {
        OrderTypeDTO created = orderTypeService.create(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/orderType/{id}")
    public ResponseEntity<OrderTypeDTO> updateOrderType(@PathVariable Integer id, @RequestBody OrderTypeDTO dto) {
        return orderTypeService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/orderType/{id}")
    public ResponseEntity<Void> deleteOrderType(@PathVariable Integer id, @RequestParam Integer version) {
        boolean deleted = orderTypeService.delete(id, version);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.status(409).build(); // Conflict if version mismatch
        }
    }
} 