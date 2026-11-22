package com.zjgsu.zy.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthRequestContextController {
    public record Context(String username, List<String> roles,
                          String forwardedUser, String forwardedRoles,
                          String legacyUserId, String legacyRole) {}

    @GetMapping("/api/auth/request-context")
    public Context context(@AuthenticationPrincipal Jwt jwt,
                           HttpServletRequest request) {
        // 身份与角色来自本服务验签后的JWT。请求头仅用于核验网关转发。
        return new Context(jwt.getSubject(), jwt.getClaimAsStringList("roles"),
                request.getHeader("X-Authenticated-User"),
                request.getHeader("X-Authenticated-Roles"),
                request.getHeader("X-User-Id"), request.getHeader("X-Role"));
    }

}