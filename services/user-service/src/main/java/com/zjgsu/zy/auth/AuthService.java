package com.zjgsu.zy.auth;

import com.zjgsu.zy.service.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AuthAccountRepository accounts;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final long ttl;
    private final String dummyHash;

    public AuthService(AuthAccountRepository accounts, PasswordEncoder passwords,
                       JwtEncoder encoder,
                       @Value("${auth.jwt.issuer}") String issuer,
                       @Value("${auth.jwt.audience}") String audience,
                       @Value("${auth.jwt.ttl-seconds:900}") long ttl) {
        if (ttl < 60 || ttl > 3600) {
            throw new IllegalArgumentException("JWT有效期必须在60至3600秒之间");
        }
        this.accounts = accounts;
        this.passwords = passwords;
        this.encoder = encoder;
        this.issuer = issuer;
        this.audience = audience;
        this.ttl = ttl;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public AuthDtos.Token login(AuthDtos.Login request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "密码UTF-8长度不能超过72字节");
        }
        AuthAccount account = accounts.findByUsername(request.username().trim())
                .orElse(null);
        boolean matches = passwords.matches(request.password(),
                account == null ? dummyHash : account.passwordHash);
        if (!matches || account == null || !account.enabled) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
                    "用户名或密码错误，或账号不可用");
        }

        Instant now = Instant.now();
        List<String> roles = List.of(account.role.name());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(account.username)
                .audience(List.of(audience))
                .issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(ttl))
                .id(UUID.randomUUID().toString())
                .claim("roles", roles).build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId("campus-auth-rs256-v1").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
        return new AuthDtos.Token(token, "Bearer", ttl, account.username, roles);
    }
}
