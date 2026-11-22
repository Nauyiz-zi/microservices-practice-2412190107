package com.zjgsu.zy.gateway.security;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.*;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@TestConfiguration
public class GatewayTestKeys {
    static final KeyPair PAIR;
    static {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            PAIR = generator.generateKeyPair();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Bean RSAPublicKey testPublicKey() {
        return (RSAPublicKey) PAIR.getPublic();
    }

    static String token(String role, String issuer, String audience, boolean expired) {
        var jwk = new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey) PAIR.getPublic())
                .privateKey((RSAPrivateKey) PAIR.getPrivate()).keyID("test-key").build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().subject("demo-user")
                .issuer(issuer).audience(List.of(audience))
                .issuedAt(now.minusSeconds(600))
                .expiresAt(expired ? now.minusSeconds(300) : now.plusSeconds(900))
                .claim("roles", List.of(role)).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-key").build(),
                claims)).getTokenValue();
    }

    static String token(String role) {
        return token(role, "campus-textbook-auth", "campus-api", false);
    }

}