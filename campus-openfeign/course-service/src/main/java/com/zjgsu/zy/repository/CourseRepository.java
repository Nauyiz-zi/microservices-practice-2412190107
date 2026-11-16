package com.zjgsu.zy.repository;
import com.zjgsu.zy.model.Course;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByCode(String code);
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Course c set c.selectedCount = c.selectedCount + 1 where c.id = :id and c.selectedCount < c.capacity")
    int reserve(@Param("id") Long id);
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Course c set c.selectedCount = c.selectedCount - 1 where c.id = :id and c.selectedCount > 0")
    int release(@Param("id") Long id);
}
