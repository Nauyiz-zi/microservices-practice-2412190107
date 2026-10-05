package com.zjgsu.zy.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {
    @GetMapping("/api/hello")
    public Map<String,Object> hello(){
        return Map.of(
                "message","校园二手教材交易平台",
                "application","monolith");
    }
}
