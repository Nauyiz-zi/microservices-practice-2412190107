package com.zjgsu.zy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_orders")
public class TradeOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "order_no", nullable = false, unique = true, length = 40)
    public String orderNo;
    @Column(name = "request_id", nullable = false, unique = true, length = 36)
    public String requestId;
    @Column(name = "buyer_id", nullable = false)
    public Long buyerId;
    @Column(name = "seller_id", nullable = false)
    public Long sellerId;
    @Column(name = "textbook_id", nullable = false)
    public Long textbookId;
    @Column(name = "textbook_title", nullable = false, length = 120)
    public String textbookTitle;
    @Column(nullable = false)
    public Integer quantity;
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    public BigDecimal unitPrice;
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    public BigDecimal totalAmount;
    @Column(nullable = false, length = 30)
    public String status;
    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }
}

