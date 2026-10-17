package com.zjgsu.zy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TextbookResponse(
        Long id,
        String title,
        String isbn,
        String author,
        String publisher,
        String conditionDescription,
        BigDecimal price,
        Integer stock,
        Long sellerId,
        Boolean listed,
        LocalDateTime createdAt
) {
}