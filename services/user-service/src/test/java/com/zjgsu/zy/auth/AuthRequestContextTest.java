package com.zjgsu.zy.auth;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("unit")
@Import(TestJwtKeys.class)
class AuthRequestContextTest {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;

    String token() {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("campus-textbook-auth")
                .subject("demo-user").audience(List.of("campus-api"))
                .issuedAt(now).expiresAt(now.plusSeconds(900))
                .claim("roles", List.of("USER")).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("campus-auth-rs256-v1").build(),
                claims)).getTokenValue();
    }

    @Test void identityHeadersCannotReplaceJwt() throws Exception {
        mvc.perform(get("/api/auth/request-context")
                        .header("X-Authenticated-User", "demo-admin")
                        .header("X-Authenticated-Roles", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test void verifiedIdentityComesFromJwtNotHeaders() throws Exception {
        mvc.perform(get("/api/auth/request-context")
                        .header("Authorization", "Bearer " + token())
                        .header("X-Authenticated-User", "attacker")
                        .header("X-Authenticated-Roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo-user"))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test void headersCannotElevateBackendAdminPermission() throws Exception {
        mvc.perform(get("/api/auth/accounts")
                        .header("Authorization", "Bearer " + token())
                        .header("X-Authenticated-Roles", "ADMIN"))
                .andExpect(status().isForbidden());
    }

}