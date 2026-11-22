package com.zjgsu.zy.gateway.security;

import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TrustedIdentityFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal().ofType(JwtAuthenticationToken.class)
                .map(auth -> sanitize(exchange, auth))
                .defaultIfEmpty(sanitize(exchange, null))
                .flatMap(chain::filter);
    }

    private ServerWebExchange sanitize(ServerWebExchange exchange,
                                       JwtAuthenticationToken auth) {
        return exchange.mutate().request(request -> request.headers(headers -> {
            for (String name : new ArrayList<>(headers.headerNames())) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.startsWith("x-user-")
                        || lower.startsWith("x-auth-")
                        || lower.startsWith("x-authenticated-")
                        || lower.equals("x-username")
                        || lower.equals("x-role") || lower.equals("x-roles")
                        || lower.equals("x-forwarded-user")
                        || lower.equals("x-forwarded-roles")) {
                    headers.remove(name);
                }
            }
            if (auth != null) {
                headers.set("X-Authenticated-User", auth.getToken().getSubject());
                String roles = auth.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .filter(a -> a.startsWith("ROLE_"))
                        .map(a -> a.substring(5)).sorted()
                        .collect(Collectors.joining(","));
                headers.set("X-Authenticated-Roles", roles);
            }
        })).build();
    }

    @Override
    public int getOrder() {
        return -100;
    }

}