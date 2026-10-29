package com.zjgsu.zy.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class CampusUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "student_no", nullable = false, unique = true, length = 30)
    public String studentNo;
    @Column(name = "display_name", nullable = false, length = 80)
    public String displayName;
    @Column(nullable = false)
    public Boolean enabled;
    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }
}

