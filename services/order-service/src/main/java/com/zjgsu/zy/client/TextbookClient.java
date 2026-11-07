package com.zjgsu.zy.client;

import com.zjgsu.zy.service.ApiException;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

@Component
public class TextbookClient {
    public record BookInfo(Long id, Long sellerId, String title,
                           BigDecimal price, Integer stock, Boolean listed) {}
    public record DeductRequest(Long buyerId, Integer quantity) {}
    public record Deduction(String orderNo, Long textbookId, Long buyerId,
                            Long sellerId, String textbookTitle,
                            Integer quantity, BigDecimal unitPrice) {}

    private final RestTemplate rest;
    private final RemoteApi remote;
    private final DiscoveryClient discoveryClient;
    private final boolean discoveryEnabled;
    private final String testBaseUrl;

    public TextbookClient(
            RestTemplate rest,
            RemoteApi remote,
            DiscoveryClient discoveryClient,
            @Value("${clients.textbook.discovery-enabled:true}")
            boolean discoveryEnabled,
            @Value("${clients.textbook.base-url:}")
            String testBaseUrl) {
        this.rest = rest;
        this.remote = remote;
        this.discoveryClient = discoveryClient;
        this.discoveryEnabled = discoveryEnabled;
        this.testBaseUrl = testBaseUrl.replaceAll("/+$", "");
    }

    private String textbookBaseUrl() {
        // 测试环境使用本机 HTTP 桩，不依赖真实 Nacos。
        if (!discoveryEnabled) {
            if (testBaseUrl.isBlank()) {
                throw new IllegalStateException(
                        "测试模式下必须配置 clients.textbook.base-url");
            }
            return testBaseUrl;
        }

        List<ServiceInstance> instances;
        try {
            instances = discoveryClient.getInstances("textbook-service");
        } catch (RuntimeException ex) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DEPENDENCY_UNAVAILABLE",
                    "无法查询教材服务实例，请稍后重试");
        }

        if (instances == null || instances.isEmpty()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DEPENDENCY_UNAVAILABLE",
                    "教材服务没有可用实例，请稍后重试");
        }

        ServiceInstance instance = instances.get(
                ThreadLocalRandom.current().nextInt(instances.size()));
        return instance.getUri().toString();
    }
    public BookInfo get(Long id) {
        BookInfo book = remote.call("textbook-service",
                () -> rest.getForObject(textbookBaseUrl() + "/api/textbooks/{id}", BookInfo.class, id));
        if (!id.equals(book.id())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "INVALID_DEPENDENCY_RESPONSE", "教材服务返回的ID不匹配");
        }
        // 此处不依据库存阻止幂等重试；扣减端点负责原子检查与重复结果返回。
        return book;
    }

    public Deduction deduct(Long id, Long buyerId, Integer quantity, String orderNo) {
        Deduction result = remote.call("textbook-service",
                () -> rest.exchange(
                        textbookBaseUrl() + "/internal/textbooks/{id}/stock-deductions/{orderNo}",
                        HttpMethod.PUT,
                        new HttpEntity<>(new DeductRequest(buyerId, quantity)),
                        Deduction.class, id, orderNo).getBody());
        if (!orderNo.equals(result.orderNo()) || !id.equals(result.textbookId())
                || !buyerId.equals(result.buyerId()) || !quantity.equals(result.quantity())
                || result.sellerId() == null || result.textbookTitle() == null
                || result.unitPrice() == null || result.unitPrice().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "INVALID_DEPENDENCY_RESPONSE", "库存响应数据不匹配，请保留原requestId重试");
        }
        return result;
    }
}

