package com.zjgsu.zy.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class JwtConfig {
    @Bean
    @Profile("!unit")
    KeyPair jwtKeyPair(
            @Value("${auth.jwt.private-key}") Resource privatePem,
            @Value("${auth.jwt.public-key}") Resource publicPem) throws Exception {
        try (var privateIn = privatePem.getInputStream();
             var publicIn = publicPem.getInputStream()) {
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privateIn);
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(publicIn);
            if (privateKey == null || publicKey == null
                    || publicKey.getModulus().bitLength() < 2048
                    || !privateKey.getModulus().equals(publicKey.getModulus())) {
                throw new IllegalStateException("JWT RSA密钥不匹配或长度不足");
            }
            return new KeyPair(publicKey, privateKey);
        }
    }

    @Bean
    RSAKey signingJwk(KeyPair pair) {
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("campus-auth-rs256-v1")
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey signingJwk) {
        return new NimbusJwtEncoder(
                new ImmutableJWKSet<>(new JWKSet(signingJwk)));
    }

    @Bean
    JwtDecoder jwtDecoder(KeyPair pair,
                          @Value("${auth.jwt.issuer}") String issuer,
                          @Value("${auth.jwt.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) pair.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience().contains(audience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "JWT audience不匹配", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
