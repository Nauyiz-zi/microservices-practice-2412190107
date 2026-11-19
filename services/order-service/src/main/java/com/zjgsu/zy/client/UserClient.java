package com.zjgsu.zy.client;

import com.zjgsu.zy.service.ApiException;
import feign.FeignException;
import feign.RetryableException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UserClient {

    public record UserInfo(
            Long id,
            String studentNo,
            String displayName,
            Boolean enabled) {}

    private final UserFeignClient feign;

    public UserClient(UserFeignClient feign) {
        this.feign = feign;
    }

    public UserInfo requireEnabled(Long id) {
        UserInfo user;

        try {
            user = feign.get(id);
        } catch (RetryableException ex) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DEPENDENCY_UNAVAILABLE",
                    "用户服务连接失败或超时，请稍后使用原requestId重试");
        } catch (FeignException ex) {
            int status = ex.status();

            if (status == 404) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "DEPENDENCY_NOT_FOUND",
                        "用户不存在，无法创建订单");
            }

            if (status == 400 || status == 409 || status == 422) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "DEPENDENCY_REJECTED",
                        "用户服务拒绝请求，请检查用户编号及状态");
            }

            if (status >= 500 || status < 0) {
                throw new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "DEPENDENCY_UNAVAILABLE",
                        "用户服务暂时不可用，请稍后使用原requestId重试");
            }

            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "INVALID_DEPENDENCY_RESPONSE",
                    "用户服务返回异常响应，无法创建订单");
        }

        if (user == null || !id.equals(user.id())
                || user.enabled() == null) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "INVALID_DEPENDENCY_RESPONSE",
                    "用户服务返回的数据不完整或用户编号不匹配");
        }

        if (!Boolean.TRUE.equals(user.enabled())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "USER_DISABLED",
                    "用户未启用，无法创建订单");
        }

        return user;
    }
}