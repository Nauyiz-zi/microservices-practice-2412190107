package com.zjgsu.zy.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class TextbookDtos {
    private TextbookDtos() {}

    public record Create(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 30) String isbn,
            @NotBlank @Size(max = 80) String author,
            @Size(max = 120) String publisher,
            @NotBlank @Size(max = 200) String conditionDescription,
            @NotNull @DecimalMin("0.01") @DecimalMax("99999999.99")
            @Digits(integer = 8, fraction = 2) BigDecimal price,
            @NotNull @Min(0) Integer stock,
            @NotNull @Positive Long sellerId) {}

    public record View(Long id, String title, String isbn, String author, String publisher,
                       String conditionDescription, BigDecimal price, Integer stock,
                       Long sellerId, Boolean listed, LocalDateTime createdAt) {}

    public record Deduct(@NotNull @Positive Long buyerId,
                         @NotNull @Positive Integer quantity) {}

    public record Deduction(String orderNo, Long textbookId, Long buyerId,
                            Long sellerId, String textbookTitle,
                            Integer quantity, BigDecimal unitPrice) {}
}

