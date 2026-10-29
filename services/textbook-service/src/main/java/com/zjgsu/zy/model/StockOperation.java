package com.zjgsu.zy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_operations")
public class StockOperation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "order_no", nullable = false, unique = true, length = 40)
    public String orderNo;
    @Column(name = "textbook_id", nullable = false)
    public Long textbookId;
    @Column(name = "buyer_id", nullable = false)
    public Long buyerId;
    @Column(nullable = false)
    public Integer quantity;
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    public BigDecimal unitPrice;
    @Column(name = "textbook_title", nullable = false, length = 120)
    public String textbookTitle;
    @Column(name = "seller_id", nullable = false)
    public Long sellerId;
    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }
}

