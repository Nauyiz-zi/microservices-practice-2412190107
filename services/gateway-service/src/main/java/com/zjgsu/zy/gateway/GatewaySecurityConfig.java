package com.zjgsu.zy.gateway.security;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {
    @Bean
    @Profile("!test")
    RSAPublicKey gatewayPublicKey(
            @Value("${auth.jwt.public-key}") Resource resource) throws Exception {
        try (var in = resource.getInputStream()) {
            RSAPublicKey key = RsaKeyConverters.x509().convert(in);
            if (key == null || key.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("网关RSA公钥无效或长度不足");
            }
            return key;
        }
    }

    @Bean
    ReactiveJwtDecoder gatewayJwtDecoder(RSAPublicKey key,
                                         @Value("${auth.jwt.issuer}") String issuer,
                                         @Value("${auth.jwt.audience}") String audience) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            boolean valid = jwt.getExpiresAt() != null
                    && jwt.getSubject() != null
                    && jwt.getSubject().matches("[A-Za-z0-9][A-Za-z0-9_.-]{2,39}")
                    && jwt.getAudience() != null
                    && jwt.getAudience().contains(audience);
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "JWT必要声明缺失或audience不匹配", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), requiredClaims));
        return decoder;
    }

    @Bean
    SecurityWebFilterChain gatewaySecurity(ServerHttpSecurity http) {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(c -> c.requestCache(NoOpServerRequestCache.getInstance()))
                .authorizeExchange(a -> a
                        .pathMatchers("/actuator/health", "/actuator/health/**",
                                "/.well-known/jwks.json").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .pathMatchers("/api/auth/accounts").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/users", "/api/users/").hasRole("ADMIN")
                        .pathMatchers("/api/**").hasAnyRole("USER", "ADMIN")
                        .anyExchange().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex) -> error(exchange, 401))
                        .accessDeniedHandler((exchange, ex) -> error(exchange, 403)))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(
                                new ReactiveJwtAuthenticationConverterAdapter(converter)))
                        .authenticationEntryPoint((exchange, ex) -> error(exchange, 401))
                        .accessDeniedHandler((exchange, ex) -> error(exchange, 403)))
                .build();
    }

    private static Mono<Void> error(ServerWebExchange exchange, int status) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("X-Auth-Enforcer", "gateway");
        if (status == 401) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        String body = status == 401
                ? "{\"code\":\"UNAUTHORIZED\",\"message\":\"请登录并携带有效Token\"}"
                : "{\"code\":\"FORBIDDEN\",\"message\":\"当前角色无权访问此接口\"}";
        return response.writeWith(Mono.just(response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

}