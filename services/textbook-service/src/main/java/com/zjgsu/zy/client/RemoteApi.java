package com.zjgsu.zy.client;

import com.zjgsu.zy.service.ApiException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@Component
public class RemoteApi {
    public <T> T call(String dependency, Supplier<T> action) {
        try {
            T result = action.get();
            if (result == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "INVALID_DEPENDENCY_RESPONSE", dependency + "返回空响应");
            }
            return result;
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "DEPENDENCY_NOT_FOUND", dependency + "中的资源不存在，拒绝本次操作");
            }
            if (status == 400 || status == 409 || status == 422) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "DEPENDENCY_REJECTED", dependency + "拒绝请求，请检查状态、库存或请求编号");
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "DEPENDENCY_UNAVAILABLE", dependency + "返回错误，本次不写入本地业务数据");
        } catch (ResourceAccessException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "DEPENDENCY_UNAVAILABLE",
                    dependency + "连接失败或超时；本次不写入本地业务数据。库存请求结果可能未知，重试必须使用原requestId");
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "INVALID_DEPENDENCY_RESPONSE", dependency + "响应无法解析，拒绝本次操作");
        }
    }
}

