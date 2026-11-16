package com.zjgsu.zy.controller;
import com.zjgsu.zy.model.Course;
import com.zjgsu.zy.repository.CourseRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/courses")
public class CourseController {
    private final CourseRepository courseRepository;
    public CourseController(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;}
    public record CreateCourseRequest(
            String code,
            String name,
            int capacity) { }
    @PostMapping
    public ResponseEntity<Course> create(
            @RequestBody CreateCourseRequest request) {
        Course course = new Course(
                request.code(),
                request.name(),
                request.capacity());
        Course savedCourse = courseRepository.save(course);
        return ResponseEntity
                .status(201)
                .body(savedCourse);}
    @PostMapping("/{id}/reserve")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void reserve(@PathVariable Long id) {
        if (courseRepository.reserve(id) == 0) seatFailure(id, "Course is full");
    }
    @PostMapping("/{id}/release")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void release(@PathVariable Long id) {
        if (courseRepository.release(id) == 0) seatFailure(id, "No occupied seat to release");
    }
    private void seatFailure(Long id, String reason) {
        if (!courseRepository.existsById(id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Course not found");
        }
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, reason);
    }
    @GetMapping("/{id}")
    public ResponseEntity<Course> findById(
            @PathVariable Long id) {
        return courseRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
