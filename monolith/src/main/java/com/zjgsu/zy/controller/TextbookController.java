package com.zjgsu.zy.controller;

import com.zjgsu.zy.dto.CreateTextbookRequest;
import com.zjgsu.zy.dto.TextbookResponse;
import com.zjgsu.zy.service.TradeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/textbooks")
public class TextbookController {

    private final TradeService tradeService;

    public TextbookController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @PostMapping
    public ResponseEntity<TextbookResponse> create(
            @RequestBody CreateTextbookRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(tradeService.createTextbook(request));
    }

    @GetMapping
    public List<TextbookResponse> list(
            @RequestParam(defaultValue = "") String keyword
    ) {
        return tradeService.listTextbooks(keyword);
    }

    @GetMapping("/{id}")
    public TextbookResponse detail(@PathVariable Long id) {
        return tradeService.getTextbook(id);
    }
}