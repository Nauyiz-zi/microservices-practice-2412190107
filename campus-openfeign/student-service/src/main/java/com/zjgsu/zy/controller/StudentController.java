package com.zjgsu.zy.controller;

import com.zjgsu.zy.model.Student;
import com.zjgsu.zy.repository.StudentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentRepository studentRepository;

    public StudentController(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public record CreateStudentRequest(
            String studentNo,
            String name
    ) {
    }

    @PostMapping
    public ResponseEntity<Student> create(
            @RequestBody CreateStudentRequest request) {

        Student student = new Student(
                request.studentNo(),
                request.name()
        );

        Student savedStudent = studentRepository.save(student);

        return ResponseEntity
                .status(201)
                .body(savedStudent);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Student> findById(
            @PathVariable Long id) {

        return studentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}