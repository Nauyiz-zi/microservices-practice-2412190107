package com.zjgsu.zy.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Allow-listed non-sensitive diagnostics; never expose arbitrary environment values. */
@RestController
public class ConfigProofController {
    private final DataSource dataSource;
    private final Environment environment;
    public ConfigProofController(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }
    public record Proof(String service, String profile, String marker,
                        int configuredMaxPoolSize, int actualMaxPoolSize, int actualMinimumIdle) {}
    @GetMapping("/api/users/config-proof")
    public Proof proof() throws SQLException {
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
        String[] active = environment.getActiveProfiles();
        String profile = String.join(",", active.length == 0 ? environment.getDefaultProfiles() : active);
        return new Proof(environment.getProperty("spring.application.name"), profile,
                environment.getProperty("campus.config.marker", "local-unconfigured"),
                environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class, -1),
                pool.getMaximumPoolSize(), pool.getMinimumIdle());
    }
}
