package com.zjgsu.zy.controller;

import com.zjgsu.zy.dto.OrderDtos;
import com.zjgsu.zy.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<OrderDtos.View> create(@Valid @RequestBody OrderDtos.Create request) {
        return ResponseEntity.status(201).body(service.create(request));
    }

    @GetMapping("/{id}")
    public OrderDtos.View get(@PathVariable Long id) { return service.get(id); }
}

