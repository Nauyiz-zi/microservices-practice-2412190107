package com.zjgsu.zy.service;

public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}