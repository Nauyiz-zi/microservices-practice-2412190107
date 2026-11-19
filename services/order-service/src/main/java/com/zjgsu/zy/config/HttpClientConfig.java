package com.zjgsu.zy.config;

import feign.RequestInterceptor;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class HttpClientConfig {

    @Bean
    RestTemplate restTemplate(
            @Value("${clients.connect-timeout-ms}") int connectTimeout,
            @Value("${clients.read-timeout-ms}") int readTimeout) {

        if (connectTimeout <= 0 || readTimeout <= 0) {
            throw new IllegalArgumentException("HTTP 超时必须大于 0");
        }

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeout));
        factory.setReadTimeout(Duration.ofMillis(readTimeout));
        return new RestTemplate(factory);
    }

    @Bean
    RequestInterceptor forwardLbTestId() {
        return template -> {
            if (RequestContextHolder.getRequestAttributes()
                    instanceof ServletRequestAttributes attributes) {

                String testId = attributes.getRequest()
                        .getHeader("X-LB-Test-Id");

                if (testId != null && !testId.isBlank()) {
                    template.header("X-LB-Test-Id", testId);
                }
            }
        };
    }
}