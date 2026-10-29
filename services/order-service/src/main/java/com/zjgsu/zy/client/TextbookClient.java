package com.zjgsu.zy.client;

import com.zjgsu.zy.service.ApiException;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
    private final String baseUrl;

    public TextbookClient(RestTemplate rest, RemoteApi remote,
                          @Value("${clients.textbook.base-url}") String baseUrl) {
        this.rest = rest;
        this.remote = remote;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public BookInfo get(Long id) {
        BookInfo book = remote.call("textbook-service",
                () -> rest.getForObject(baseUrl + "/api/textbooks/{id}", BookInfo.class, id));
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
                        baseUrl + "/internal/textbooks/{id}/stock-deductions/{orderNo}",
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

