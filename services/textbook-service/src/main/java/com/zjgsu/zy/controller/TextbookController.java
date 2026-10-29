package com.zjgsu.zy.controller;

import com.zjgsu.zy.dto.TextbookDtos;
import com.zjgsu.zy.service.TextbookService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/textbooks")
public class TextbookController {
    private final TextbookService service;
    public TextbookController(TextbookService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<TextbookDtos.View> create(@Valid @RequestBody TextbookDtos.Create request) {
        return ResponseEntity.status(201).body(service.create(request));
    }

    @GetMapping
    public List<TextbookDtos.View> list(@RequestParam(defaultValue = "") String keyword) {
        return service.list(keyword);
    }

    @GetMapping("/{id}")
    public TextbookDtos.View get(@PathVariable Long id) { return service.get(id); }
}

