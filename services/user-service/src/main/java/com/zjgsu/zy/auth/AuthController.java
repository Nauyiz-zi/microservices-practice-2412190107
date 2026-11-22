package com.zjgsu.zy.auth;

import com.nimbusds.jose.jwk.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
    private final AuthService auth;
    private final AuthAccountRepository accounts;
    private final RSAKey signingJwk;

    public AuthController(AuthService auth, AuthAccountRepository accounts,
                          RSAKey signingJwk) {
        this.auth = auth;
        this.accounts = accounts;
        this.signingJwk = signingJwk;
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<AuthDtos.Token> login(
            @Valid @RequestBody AuthDtos.Login request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache").body(auth.login(request));
    }

    @GetMapping("/api/auth/me")
    public AuthDtos.Me me(@AuthenticationPrincipal Jwt jwt) {
        return new AuthDtos.Me(jwt.getSubject(), jwt.getClaimAsStringList("roles"));
    }

    @GetMapping("/api/auth/accounts")
    public List<AuthDtos.Account> accounts() {
        return accounts.findAll().stream()
                .map(a -> new AuthDtos.Account(a.username, a.role.name(), a.enabled))
                .toList();
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        // 只公开公钥，不包含RSA私钥参数。
        return new JWKSet(signingJwk.toPublicJWK()).toJSONObject();
    }
}
