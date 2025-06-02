package org.kasbench.globeco_order_service.controller;

import lombok.RequiredArgsConstructor;
import org.kasbench.globeco_order_service.dto.OrderDTO;
import org.kasbench.globeco_order_service.dto.OrderPostDTO;
import org.kasbench.globeco_order_service.dto.OrderWithDetailsDTO;
import org.kasbench.globeco_order_service.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @GetMapping("/orders")
    public List<OrderWithDetailsDTO> getAllOrders() {
        return orderService.getAll();
    }

    @GetMapping("/order/{id}")
    public ResponseEntity<OrderWithDetailsDTO> getOrderById(@PathVariable Integer id) {
        return orderService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderWithDetailsDTO> createOrder(@RequestBody OrderPostDTO dto) {
        OrderWithDetailsDTO created = orderService.create(dto);
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping("/order/{id}")
    public ResponseEntity<OrderWithDetailsDTO> updateOrder(@PathVariable Integer id, @RequestBody OrderDTO dto) {
        return orderService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/order/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Integer id, @RequestParam Integer version) {
        boolean deleted = orderService.delete(id, version);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.status(409).build(); // Conflict if version mismatch
        }
    }

    @PostMapping("/orders/{id}/submit")
    public ResponseEntity<?> submitOrder(@PathVariable Integer id) {
        OrderDTO orderDTO = orderService.submitOrder(id);
        if (orderDTO != null) {
            return ResponseEntity.ok(orderDTO);
        } else {
            return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("status", "not submitted"));
        }
    }
} 