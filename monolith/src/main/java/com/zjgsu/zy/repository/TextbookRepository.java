package com.zjgsu.zy.repository;

import com.zjgsu.zy.model.Textbook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TextbookRepository extends JpaRepository<Textbook, Long> {

    @Query("""
            select t from Textbook t
            where t.listed = true
              and (
                    :keyword = ''
                    or lower(t.title) like lower(concat('%', :keyword, '%'))
                    or t.isbn like concat('%', :keyword, '%')
              )
            order by t.createdAt desc
            """)
    List<Textbook> searchListed(@Param("keyword") String keyword);
}