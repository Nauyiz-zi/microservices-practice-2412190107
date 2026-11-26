package com.zjgsu.zy.controller;

import com.zjgsu.zy.service.StockWarningPolicy;
import java.lang.management.ManagementFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class StockWarningController {
    private final StockWarningPolicy policy;
    private final Environment environment;

    private final String instance =
            System.getenv().getOrDefault("HOSTNAME", "local");
    private final long pid = ProcessHandle.current().pid();
    private final long startedAt =
            ManagementFactory.getRuntimeMXBean().getStartTime();

    public StockWarningController(
            StockWarningPolicy policy, Environment environment) {
        this.policy = policy;
        this.environment = environment;
    }

    public record View(
            String service,
            String profile,
            String instance,
            long pid,
            long startedAt,
            int stock,
            int threshold,
            boolean warning,
            String revision) {}

    @GetMapping("/api/textbooks/stock-warning")
    public View warning(@RequestParam(defaultValue = "5") int stock) {
        if (stock < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "stock must be non-negative");
        }

        String[] active = environment.getActiveProfiles();
        String profile = String.join(",",
                active.length == 0
                        ? environment.getDefaultProfiles()
                        : active);

        var result = policy.evaluate(stock);

        return new View(
                "textbook-service",
                profile,
                instance,
                pid,
                startedAt,
                result.stock(),
                result.threshold(),
                result.warning(),
                result.revision());
    }
}