package com.zjgsu.zy.dto;

public record ApiError(
        String code,
        String message
) {
}