package com.zjgsu.zy.dto;

public record CreateOrderRequest(
        Long buyerId,
        Long textbookId,
        Integer quantity
) {
}