package com.zjgsu.zy.auth;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtKeys.class)
class AuthApiTest {
    @Autowired MockMvc mvc;
    @Autowired AuthAccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;
    @Value("${auth.jwt.issuer}") String issuer;
    @Value("${auth.jwt.audience}") String audience;
    String password;

    @BeforeEach
    void prepare() {
        accounts.deleteAll();
        password = UUID.randomUUID() + "Aa1!";
        add("test-user", AuthAccount.Role.USER);
        add("test-admin", AuthAccount.Role.ADMIN);
    }

    void add(String username, AuthAccount.Role role) {
        AuthAccount account = new AuthAccount();
        account.username = username;
        account.passwordHash = passwords.encode(password);
        account.role = role;
        accounts.saveAndFlush(account);
    }

    String token(String username) throws Exception {
        String json = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    @Test void passwordIsHashedAndJwtVerifies() throws Exception {
        var account = accounts.findByUsername("test-user").orElseThrow();
        assertNotEquals(password, account.passwordHash);
        assertTrue(account.passwordHash.startsWith("$2a$12$"));
        assertTrue(passwords.matches(password, account.passwordHash));
        assertNotEquals(account.passwordHash,
                accounts.findByUsername("test-admin").orElseThrow().passwordHash);
        Jwt jwt = decoder.decode(token("test-user"));
        assertEquals("test-user", jwt.getSubject());
        assertEquals(List.of("USER"), jwt.getClaimAsStringList("roles"));
    }

    @Test void wrongPasswordIs401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-user\",\"password\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test void missingTokenIs401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test void userCanReadMeButNotAccounts() throws Exception {
        String token = token("test-user");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("USER"));
        mvc.perform(get("/api/auth/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test void adminCanReadAccountsWithoutPasswordHashes() throws Exception {
        mvc.perform(get("/api/auth/accounts").header("Authorization", "Bearer " + token("test-admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test void tamperedSignatureIs401() throws Exception {
        String[] parts = token("test-user").split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + String.join(".", parts)))
                .andExpect(status().isUnauthorized());
    }

    String signed(String iss, String aud, boolean expired) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(iss).subject("test-user")
                .audience(List.of(aud)).issuedAt(now.minusSeconds(600))
                .expiresAt(expired ? now.minusSeconds(300) : now.plusSeconds(300))
                .claim("roles", List.of("USER")).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("campus-auth-rs256-v1").build(),
                claims)).getTokenValue();
    }

    @Test void expiredTokenIs401() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + signed(issuer, audience, true)))
                .andExpect(status().isUnauthorized());
    }

    @Test void wrongIssuerIs401() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + signed("other-issuer", audience, false)))
                .andExpect(status().isUnauthorized());
    }

    @Test void wrongAudienceIs401() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + signed(issuer, "other-audience", false)))
                .andExpect(status().isUnauthorized());
    }

    @Test void disabledAccountCannotLogin() throws Exception {
        var account = accounts.findByUsername("test-user").orElseThrow();
        account.enabled = false;
        accounts.saveAndFlush(account);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-user\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test void bootstrapCreatesOnceAndPreservesExistingCredentials() {
        String hash = passwords.encode(password);
        var bootstrap = new AuthBootstrap(
                accounts, "seed-admin", hash, "seed-user", hash);
        bootstrap.run(null);
        bootstrap.run(null);
        assertEquals(4, accounts.count());
        var seeded = accounts.findByUsername("seed-admin").orElseThrow();
        assertEquals(AuthAccount.Role.ADMIN, seeded.role);
        assertEquals(hash, seeded.passwordHash);
        seeded.enabled = false;
        accounts.saveAndFlush(seeded);
        bootstrap.run(null);
        assertFalse(accounts.findByUsername("seed-admin").orElseThrow().enabled);
    }

    @Test void jwksContainsPublicKeyOnly() throws Exception {
        mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist());
    }
}
