package com.zjgsu.zy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

@Service
@RefreshScope
public class StockWarningPolicy {
    private final int threshold;
    private final String revision;

    public StockWarningPolicy(
            @Value("${campus.business.stock-warning-threshold:3}") int threshold,
            @Value("${campus.business.revision:v1}") String revision) {
        if (threshold < 0 || threshold > 1000000) {
            throw new IllegalArgumentException(
                    "Invalid stock warning threshold");
        }
        this.threshold = threshold;
        this.revision = revision;
    }

    public record Decision(
            int stock, int threshold, boolean warning, String revision) {}

    public Decision evaluate(int stock) {
        return new Decision(
                stock, threshold, stock <= threshold, revision);
    }
}