package com.zjgsu.zy.controller;

import com.zjgsu.zy.client.*;
import com.zjgsu.zy.model.Enrollment;
import com.zjgsu.zy.repository.EnrollmentRepository;
import feign.FeignException;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {
    private final EnrollmentRepository repository;
    private final StudentClient students;
    private final CourseClient courses;

    public EnrollmentController(EnrollmentRepository repository, StudentClient students, CourseClient courses) {
        this.repository = repository;
        this.students = students;
        this.courses = courses;
    }

    public record CreateEnrollmentRequest(Long studentId, Long courseId) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Enrollment create(@RequestBody CreateEnrollmentRequest request) {
        if (request.studentId() == null || request.courseId() == null
                || request.studentId() <= 0 || request.courseId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId and courseId must be positive");
        }
        remote("student", () -> students.findById(request.studentId()));
        remote("course", () -> courses.findById(request.courseId()));
        if (repository.existsByStudentIdAndCourseId(request.studentId(), request.courseId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Enrollment already exists");
        }
        remote("course", () -> { courses.reserve(request.courseId()); return Boolean.TRUE; });
        try {
            return repository.saveAndFlush(new Enrollment(request.studentId(), request.courseId()));
        } catch (RuntimeException ex) {
            // The repository transaction has rolled back before compensation starts.
            try {
                remote("course", () -> { courses.release(request.courseId()); return Boolean.TRUE; });
            } catch (RuntimeException compensationFailure) {
                compensationFailure.addSuppressed(ex);
                org.slf4j.LoggerFactory.getLogger(getClass()).error(
                        "Seat compensation failed: studentId={}, courseId={}",
                        request.studentId(), request.courseId(), compensationFailure);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Seat compensation failed; reconciliation required", compensationFailure);
            }
            if (ex instanceof DataIntegrityViolationException) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Enrollment already exists", ex);
            }
            throw ex;
        }
    }

    @GetMapping("/{id}")
    public Enrollment findById(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void drop(@PathVariable Long id) {
        // Serialize concurrent drops of the same enrollment, including across instances.
        Enrollment enrollment = repository.findLockedById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found"));
        remote("course", () -> { courses.release(enrollment.getCourseId()); return Boolean.TRUE; });
        repository.delete(enrollment);
        repository.flush();
    }

    private <T> T remote(String service, Supplier<T> action) {
        try {
            T result = action.get();
            if (result == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, service + " returned an empty response");
            }
            return result;
        } catch (FeignException ex) {
            if (ex.status() == 404) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, service + " not found", ex);
            }
            if (ex.status() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Course is full or seat cannot be released", ex);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, service + " service unavailable", ex);
        }
    }
}
