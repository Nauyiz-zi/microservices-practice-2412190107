package com.zjgsu.zy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.zjgsu.zy.auth.TestJwtKeys;
import org.springframework.context.annotation.Import;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("unit")
@Import(TestJwtKeys.class)
class ConfigProofTest {
    @Autowired MockMvc mvc;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("campus.config.marker", () -> "fixture-user");
        r.add("spring.datasource.hikari.maximum-pool-size", () -> 7);
        r.add("spring.datasource.hikari.minimum-idle", () -> 2);
    }
    @Test void reflectsActualPoolAndHidesSecrets() throws Exception {
        mvc.perform(get("/api/users/config-proof"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("user-service"))
                .andExpect(jsonPath("$.marker").value("fixture-user"))
                .andExpect(jsonPath("$.configuredMaxPoolSize").value(7))
                .andExpect(jsonPath("$.actualMaxPoolSize").value(7))
                .andExpect(jsonPath("$.actualMinimumIdle").value(2))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.jdbcUrl").doesNotExist());
    }
}
