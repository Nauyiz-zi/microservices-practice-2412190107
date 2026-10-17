package com.zjgsu.zy.controller;

import com.zjgsu.zy.dto.CreateOrderRequest;
import com.zjgsu.zy.dto.OrderResponse;
import com.zjgsu.zy.service.TradeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final TradeService tradeService;

    public OrderController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestBody CreateOrderRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(tradeService.createOrder(request));
    }

    @GetMapping("/{id}")
    public OrderResponse detail(@PathVariable Long id) {
        return tradeService.getOrder(id);
    }
}