package com.zjgsu.zy.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class OrderDtos {
    private OrderDtos() {}

    public record Create(
            @NotBlank @Pattern(regexp =
                    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
            String requestId,
            @NotNull @Positive Long buyerId,
            @NotNull @Positive Long textbookId,
            @NotNull @Positive Integer quantity) {}

    public record View(Long id, String orderNo, String requestId, Long buyerId,
                       Long sellerId, Long textbookId, String textbookTitle, Integer quantity,
                       BigDecimal unitPrice, BigDecimal totalAmount,
                       String status, LocalDateTime createdAt) {}
}

