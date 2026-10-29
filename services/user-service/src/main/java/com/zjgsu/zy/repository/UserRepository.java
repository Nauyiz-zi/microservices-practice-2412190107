package com.zjgsu.zy.repository;

import com.zjgsu.zy.model.CampusUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<CampusUser, Long> {
    boolean existsByStudentNo(String studentNo);
}

