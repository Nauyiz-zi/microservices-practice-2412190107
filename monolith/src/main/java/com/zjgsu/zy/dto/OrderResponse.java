package com.zjgsu.zy.dto;

import com.zjgsu.zy.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        String orderNo,
        Long buyerId,
        Long sellerId,
        Long textbookId,
        String textbookTitle,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        OrderStatus status,
        LocalDateTime createdAt
) {
}