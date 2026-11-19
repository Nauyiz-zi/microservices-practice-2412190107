package com.zjgsu.zy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zjgsu.zy.dto.OrderDtos;
import com.zjgsu.zy.repository.OrderRepository;
import com.zjgsu.zy.service.ApiException;
import com.zjgsu.zy.service.OrderService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    // 0：正常；1：用户404；2：用户读取超时；
    // 3：库存先扣减再延迟响应；4：用户503。
    static final AtomicInteger mode = new AtomicInteger();
    static final AtomicInteger requests = new AtomicInteger();
    static final AtomicInteger stock = new AtomicInteger(3);

    // 模拟远端库存操作记录，以 orderNo 保证幂等。
    static final Map<String, String> deductions =
            new ConcurrentHashMap<>();

    static final ExecutorService executor =
            Executors.newCachedThreadPool();

    static final HttpServer downstream;

    static {
        try {
            downstream = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0);
            downstream.setExecutor(executor);
            downstream.createContext("/", RealHttpClientTest::handle);
            downstream.start();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    static void handle(HttpExchange exchange) {
        requests.incrementAndGet();

        try {
            exchange.getRequestBody().readAllBytes();

            String path = exchange.getRequestURI().getPath();
            int currentMode = mode.get();
            int status = 200;
            String body;

            if (path.startsWith("/api/users/")) {
                if (currentMode == 2) {
                    Thread.sleep(2500);
                }

                if (currentMode == 1) {
                    status = 404;
                    body = """
                            {"code":"USER_NOT_FOUND","message":"test user missing"}
                            """;
                } else if (currentMode == 4) {
                    status = 503;
                    body = """
                            {"code":"UNAVAILABLE","message":"test dependency unavailable"}
                            """;
                } else {
                    body = """
                            {"id":2,"enabled":true}
                            """;
                }
            } else if (path.equals("/api/textbooks/1")) {
                body = """
                        {"id":1,"sellerId":1,"title":"Test Book",
                        "stock":%d,"listed":true,"price":25.50}
                        """.formatted(stock.get());
            } else if (path.startsWith(
                    "/internal/textbooks/1/stock-deductions/")
                    && "PUT".equals(exchange.getRequestMethod())) {

                String orderNo = path.substring(path.lastIndexOf('/') + 1);

                body = deductions.computeIfAbsent(orderNo, key -> {
                    stock.decrementAndGet();
                    return """
                            {"orderNo":"%s","textbookId":1,"buyerId":2,
                            "sellerId":1,"textbookTitle":"Test Book",
                            "quantity":1,"unitPrice":25.50}
                            """.formatted(key);
                });

                // 远端状态已经改变，随后延迟响应，让调用方读取超时。
                if (currentMode == 3) {
                    Thread.sleep(2500);
                }
            } else {
                status = 404;
                body = """
                        {"code":"NOT_FOUND","message":"unknown test endpoint"}
                        """;
            }

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException ignored) {
            // 超时后客户端可能先关闭连接。
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:"
                + downstream.getAddress().getPort();

        registry.add(
                "spring.cloud.openfeign.client.config.user-service.url",
                () -> url);
        registry.add(
                "spring.cloud.openfeign.client.config.user-service.connectTimeout",
                () -> 500);
        registry.add(
                "spring.cloud.openfeign.client.config.user-service.readTimeout",
                () -> 500);

        registry.add("clients.textbook.base-url", () -> url);
        registry.add("clients.textbook.discovery-enabled", () -> false);
        registry.add("clients.connect-timeout-ms", () -> 500);
        registry.add("clients.read-timeout-ms", () -> 500);
    }

    @Autowired
    OrderService service;

    @Autowired
    OrderRepository orders;

    @BeforeEach
    void before() {
        orders.deleteAll();
        mode.set(0);
        requests.set(0);
        stock.set(3);
        deductions.clear();
    }

    @AfterAll
    static void stop() {
        downstream.stop(0);
        executor.shutdownNow();
    }

    OrderDtos.Create request() {
        return new OrderDtos.Create(
                UUID.randomUUID().toString(), 2L, 1L, 1);
    }

    @Test
    void feignAndRestTemplateMakeThreeRealHttpCalls() {
        var result = service.create(request());

        assertEquals("PENDING_PAYMENT", result.status());
        assertEquals(3, requests.get());
        assertEquals(1, orders.count());
        assertEquals(2, stock.get());
        assertEquals(1, deductions.size());
    }

    @Test
    void realHttp404DoesNotWriteOrder() {
        mode.set(1);

        ApiException ex = assertThrows(
                ApiException.class, () -> service.create(request()));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.status());
        assertEquals("DEPENDENCY_NOT_FOUND", ex.code());
        assertEquals(0, orders.count());
        assertEquals(3, stock.get());
    }

    @Test
    void realHttp503DoesNotWriteOrder() {
        mode.set(4);

        ApiException ex = assertThrows(
                ApiException.class, () -> service.create(request()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.status());
        assertEquals("DEPENDENCY_UNAVAILABLE", ex.code());
        assertEquals(0, orders.count());
        assertEquals(3, stock.get());
        assertEquals(0, deductions.size());
    }

    @Test
    void realReadTimeoutDoesNotWriteOrder() {
        mode.set(2);

        ApiException ex = assertThrows(
                ApiException.class, () -> service.create(request()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.status());
        assertEquals("DEPENDENCY_UNAVAILABLE", ex.code());
        assertEquals(0, orders.count());
        assertEquals(3, stock.get());
    }

    @Test
    void writeTimeoutMayHaveCommittedAndSameRequestCanRecover() {
        OrderDtos.Create request = request();
        mode.set(3);

        ApiException ex = assertThrows(
                ApiException.class, () -> service.create(request));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.status());
        assertEquals("DEPENDENCY_UNAVAILABLE", ex.code());
        assertTrue(ex.getMessage().contains("库存请求结果可能未知"));
        assertTrue(ex.getMessage().contains("原requestId"));

        // 本地未保存订单，但模拟远端已扣减一次库存。
        assertEquals(0, orders.count());
        assertEquals(2, stock.get());
        assertEquals(1, deductions.size());

        System.out.println(
                "[CHECK] WRITE_TIMEOUT: remoteStock=2, localOrders=0");

        // 保留原请求编号，远端返回原库存操作结果。
        mode.set(0);
        var recovered = service.create(request);

        assertEquals("PENDING_PAYMENT", recovered.status());
        assertEquals(request.requestId(), recovered.requestId());
        assertEquals(1, orders.count());
        assertEquals(2, stock.get());
        assertEquals(1, deductions.size());

        // 再次重复请求也不能创建第二笔订单或重复扣减。
        var repeated = service.create(request);
        assertEquals(recovered.id(), repeated.id());
        assertEquals(1, orders.count());
        assertEquals(2, stock.get());

        System.out.println(
                "[PASS] SAME_REQUEST_RECOVERY: remoteStock=2, localOrders=1");
    }
}