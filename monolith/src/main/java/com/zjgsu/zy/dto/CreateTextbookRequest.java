package com.zjgsu.zy.dto;

import java.math.BigDecimal;

public record CreateTextbookRequest(
        String title,
        String isbn,
        String author,
        String publisher,
        String conditionDescription,
        BigDecimal price,
        Integer stock,
        Long sellerId
) {
}