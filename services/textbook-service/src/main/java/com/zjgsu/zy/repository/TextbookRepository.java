package com.zjgsu.zy.repository;

import com.zjgsu.zy.model.Textbook;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TextbookRepository extends JpaRepository<Textbook, Long> {
    boolean existsBySellerIdAndIsbn(Long sellerId, String isbn);

    @Query("""
            select t from Textbook t where t.listed = true
            and (:keyword = '' or lower(t.title) like lower(concat('%', :keyword, '%'))
            or t.isbn like concat('%', :keyword, '%')) order by t.id desc
            """)
    List<Textbook> search(@Param("keyword") String keyword);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Textbook t where t.id = :id")
    Optional<Textbook> findLockedById(@Param("id") Long id);
}

