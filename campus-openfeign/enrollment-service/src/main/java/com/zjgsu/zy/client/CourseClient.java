package com.zjgsu.zy.client;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name = "course-service")
public interface CourseClient {
    @GetMapping("/api/courses/{id}")
    CourseView findById(@PathVariable("id") Long id);
    @PostMapping("/api/courses/{id}/reserve")
    void reserve(@PathVariable("id") Long id);
    @PostMapping("/api/courses/{id}/release")
    void release(@PathVariable("id") Long id);
    record CourseView(Long id, String code, String name, int capacity, int selectedCount) {}
}
