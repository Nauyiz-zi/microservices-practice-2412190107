package com.zjgsu.zy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("unit")
class ConfigProofTest {
    @Autowired MockMvc mvc;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("campus.config.marker", () -> "fixture-textbook");
        r.add("spring.datasource.hikari.maximum-pool-size", () -> 8);
        r.add("spring.datasource.hikari.minimum-idle", () -> 2);
    }
    @Test void reflectsActualPoolAndHidesSecrets() throws Exception {
        mvc.perform(get("/api/textbooks/config-proof"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("textbook-service"))
                .andExpect(jsonPath("$.marker").value("fixture-textbook"))
                .andExpect(jsonPath("$.configuredMaxPoolSize").value(8))
                .andExpect(jsonPath("$.actualMaxPoolSize").value(8))
                .andExpect(jsonPath("$.actualMinimumIdle").value(2))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.jdbcUrl").doesNotExist());
    }
}
