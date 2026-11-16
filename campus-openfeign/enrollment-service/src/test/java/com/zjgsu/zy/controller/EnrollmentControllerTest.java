package com.zjgsu.zy.controller;

import com.zjgsu.zy.client.*;
import com.zjgsu.zy.model.Enrollment;
import com.zjgsu.zy.repository.EnrollmentRepository;
import feign.FeignException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnrollmentControllerTest {
    EnrollmentRepository repository = mock(EnrollmentRepository.class);
    StudentClient students = mock(StudentClient.class);
    CourseClient courses = mock(CourseClient.class);
    EnrollmentController controller = new EnrollmentController(repository, students, courses);
    EnrollmentController.CreateEnrollmentRequest request = new EnrollmentController.CreateEnrollmentRequest(1L, 2L);

    @BeforeEach void found() {
        when(students.findById(1L)).thenReturn(new StudentClient.StudentView(1L,"S1","Student"));
        when(courses.findById(2L)).thenReturn(new CourseClient.CourseView(2L,"C1","Course",1,0));
    }
    FeignException remoteError(int status) {
        FeignException failure = mock(FeignException.class);
        when(failure.status()).thenReturn(status);
        return failure;
    }
    @Test void occupiesBeforeSaving() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        controller.create(request);
        var order = inOrder(courses, repository);
        order.verify(courses).findById(2L);
        order.verify(repository).existsByStudentIdAndCourseId(1L, 2L);
        order.verify(courses).reserve(2L);
        order.verify(repository).saveAndFlush(any());
        verify(courses,never()).release(any());
    }
    @Test void compensatesAfterDatabaseConflict() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.create(request)).getStatusCode().value());
        verify(courses).release(2L);
    }
    @Test void reportsFailedCompensation() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        doThrow(remoteError(503)).when(courses).release(2L);
        var failure=assertThrows(ResponseStatusException.class,()->controller.create(request));
        assertEquals(503,failure.getStatusCode().value());
        assertTrue(failure.getReason().contains("reconciliation required"));
    }
    @Test void fullCourseDoesNotWriteOrRelease() {
        doThrow(remoteError(409)).when(courses).reserve(2L);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.create(request)).getStatusCode().value());
        verify(repository,never()).saveAndFlush(any());
        verify(courses,never()).release(any());
    }
    @Test void failedDropKeepsLocalRecord() {
        when(repository.findLockedById(3L)).thenReturn(Optional.of(new Enrollment(1L,2L)));
        doThrow(remoteError(-1)).when(courses).release(2L);
        assertEquals(503,assertThrows(ResponseStatusException.class,()->controller.drop(3L)).getStatusCode().value());
        verify(repository,never()).delete(any(Enrollment.class));
    }
    @Test void dropReleasesBeforeDeleting() {
        var enrollment=new Enrollment(1L,2L);
        when(repository.findLockedById(3L)).thenReturn(Optional.of(enrollment));
        controller.drop(3L);
        var order=inOrder(courses,repository);
        order.verify(repository).findLockedById(3L);
        order.verify(courses).release(2L);
        order.verify(repository).delete(enrollment);
    }
}
