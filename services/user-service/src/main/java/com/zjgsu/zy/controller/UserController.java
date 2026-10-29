package com.zjgsu.zy.controller;

import com.zjgsu.zy.dto.UserDtos;
import com.zjgsu.zy.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<UserDtos.View> create(@Valid @RequestBody UserDtos.Create request) {
        return ResponseEntity.status(201).body(service.create(request));
    }

    @GetMapping("/{id}")
    public UserDtos.View get(@PathVariable Long id) { return service.get(id); }
}

