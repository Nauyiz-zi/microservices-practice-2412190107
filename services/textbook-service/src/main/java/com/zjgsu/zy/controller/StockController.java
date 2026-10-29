package com.zjgsu.zy.controller;

import com.zjgsu.zy.dto.TextbookDtos;
import com.zjgsu.zy.service.StockService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class StockController {
    private final StockService stock;
    public StockController(StockService stock) { this.stock = stock; }

    @PutMapping("/internal/textbooks/{id}/stock-deductions/{orderNo}")
    public TextbookDtos.Deduction deduct(@PathVariable Long id, @PathVariable String orderNo,
                                         @Valid @RequestBody TextbookDtos.Deduct request) {
        return stock.deduct(id, orderNo, request);
    }
}

