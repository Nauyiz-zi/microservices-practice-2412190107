package com.zjgsu.zy.client;

import com.zjgsu.zy.service.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class UserClient {
    public record UserInfo(Long id, String studentNo, String displayName, Boolean enabled) {}

    private final RestTemplate rest;
    private final RemoteApi remote;
    private final String baseUrl;

    public UserClient(RestTemplate rest, RemoteApi remote,
                      @Value("${clients.user.base-url}") String baseUrl) {
        this.rest = rest;
        this.remote = remote;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public UserInfo requireEnabled(Long id) {
        UserInfo user = remote.call("user-service",
                () -> rest.getForObject(baseUrl + "/api/users/{id}", UserInfo.class, id));
        if (!id.equals(user.id())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "INVALID_DEPENDENCY_RESPONSE", "用户服务返回的ID不匹配");
        }
        if (!Boolean.TRUE.equals(user.enabled())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "USER_DISABLED", "用户未启用，拒绝本次操作");
        }
        return user;
    }
}

