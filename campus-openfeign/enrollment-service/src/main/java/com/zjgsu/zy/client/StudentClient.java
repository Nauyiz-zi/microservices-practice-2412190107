package com.zjgsu.zy.client;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name = "student-service")
public interface StudentClient {
    @GetMapping("/api/students/{id}")
    StudentView findById(@PathVariable("id") Long id);
    record StudentView(Long id, String studentNo, String name) {}
}
