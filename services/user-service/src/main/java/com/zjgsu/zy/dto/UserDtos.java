package com.zjgsu.zy.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public final class UserDtos {
    private UserDtos() {}

    public record Create(
            @NotBlank @Size(max = 30) String studentNo,
            @NotBlank @Size(max = 80) String displayName) {}

    public record View(Long id, String studentNo, String displayName,
                       Boolean enabled, LocalDateTime createdAt) {}
}

