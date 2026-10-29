package com.zjgsu.zy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "textbooks", uniqueConstraints = @UniqueConstraint(
        name = "uk_textbook_seller_isbn", columnNames = {"seller_id", "isbn"}))
public class Textbook {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, length = 120)
    public String title;
    @Column(nullable = false, length = 30)
    public String isbn;
    @Column(nullable = false, length = 80)
    public String author;
    @Column(length = 120)
    public String publisher;
    @Column(name = "condition_description", nullable = false, length = 200)
    public String conditionDescription;
    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal price;
    @Column(nullable = false)
    public Integer stock;
    @Column(name = "seller_id", nullable = false)
    public Long sellerId;
    @Column(nullable = false)
    public Boolean listed;
    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}

