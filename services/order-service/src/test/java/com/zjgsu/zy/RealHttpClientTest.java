package com.zjgsu.zy;

import com.sun.net.httpserver.HttpServer;
import com.zjgsu.zy.dto.OrderDtos;
import com.zjgsu.zy.repository.OrderRepository;
import com.zjgsu.zy.service.ApiException;
import com.zjgsu.zy.service.OrderService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RealHttpClientTest {
    // 使用本机随机端口 HTTP 桩：实际经过 socket，不替换 RestTemplate。
    static final HttpServer downstream;
    static final ExecutorService executor = Executors.newCachedThreadPool();
    static final AtomicInteger mode = new AtomicInteger();
    static final AtomicInteger requests = new AtomicInteger();
    static {
        try {
            downstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            downstream.setExecutor(executor);
            downstream.createContext("/", exchange -> {
                requests.incrementAndGet();
                try {
                    if (mode.get() == 2) {
                        Thread.sleep(3000);
                    }
                    String path = exchange.getRequestURI().getPath();
                    int status = mode.get() == 1 && path.startsWith("/api/users/") ? 404 : 200;
                    String body;
                    if (path.startsWith("/api/users/")) {
                        body = "{\"id\":2,\"enabled\":true}";
                    } else if (path.startsWith("/api/textbooks/")) {
                        body = "{\"id\":1,\"sellerId\":1,\"title\":\"Java教材\",\"stock\":3,\"listed\":true,\"price\":25.50}";
                    } else {
                        String orderNo = path.substring(path.lastIndexOf('/') + 1);
                        body = """
                                {"orderNo":"%s","textbookId":1,"buyerId":2,"sellerId":1,
                                "textbookTitle":"Java教材","quantity":1,"unitPrice":25.50}
                                """.formatted(orderNo);
                    }
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (IOException ignored) {
                    // 读取超时测试中，客户端会先关闭连接。
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            downstream.start();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + downstream.getAddress().getPort();
        registry.add("clients.user.base-url", () -> url);
        registry.add("clients.textbook.base-url", () -> url);
        registry.add("clients.connect-timeout-ms", () -> 500);
        registry.add("clients.read-timeout-ms", () -> 1500);
    }

    @Autowired OrderService service;
    @Autowired OrderRepository orders;

    @BeforeEach void before() { orders.deleteAll(); mode.set(0); requests.set(0); }
    @AfterAll static void stop() { downstream.stop(0); executor.shutdownNow(); }

    OrderDtos.Create request() {
        return new OrderDtos.Create(UUID.randomUUID().toString(), 2L, 1L, 1);
    }

    @Test void restTemplateMakesThreeRealHttpCalls() {
        var result = service.create(request());
        assertEquals("PENDING_PAYMENT", result.status());
        assertEquals(3, requests.get());
        assertEquals(1, orders.count());
    }

    @Test void realHttp404DoesNotWriteOrder() {
        mode.set(1);
        ApiException ex = assertThrows(ApiException.class, () -> service.create(request()));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.status());
        assertEquals(0, orders.count());
    }

    @Test void realReadTimeoutDoesNotWriteOrder() {
        mode.set(2);
        ApiException ex = assertThrows(ApiException.class, () -> service.create(request()));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.status());
        assertEquals("DEPENDENCY_UNAVAILABLE", ex.code());
        assertEquals(0, orders.count());
    }
}

