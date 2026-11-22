package com.zjgsu.zy.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record Login(
            @NotBlank @Size(max = 40) String username,
            @NotBlank @Size(max = 72) String password) {}
    public record Token(String accessToken, String tokenType, long expiresIn,
                        String username, List<String> roles) {}
    public record Me(String username, List<String> roles) {}
    public record Account(String username, String role, boolean enabled) {}
}
