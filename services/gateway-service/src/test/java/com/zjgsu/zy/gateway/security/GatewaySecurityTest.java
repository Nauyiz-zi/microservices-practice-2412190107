package com.zjgsu.zy.gateway.security;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(GatewayTestKeys.class)
class GatewaySecurityTest {
    static final AtomicInteger calls = new AtomicInteger();
    static final HttpServer downstream;
    static final ExecutorService executor = Executors.newCachedThreadPool();
    static {
        try {
            downstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            downstream.setExecutor(executor);
            downstream.createContext("/", exchange -> {
                calls.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                var headers = exchange.getRequestHeaders();
                String user = headers.getFirst("X-Authenticated-User");
                String roles = headers.getFirst("X-Authenticated-Roles");
                String legacy = headers.getFirst("X-User-Id");
                String body = "user=" + user + ";roles=" + roles + ";legacy=" + legacy;
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                int status = "POST".equals(exchange.getRequestMethod()) ? 201 : 200;
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            downstream.start();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void testRoutes(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.nacos.discovery.enabled", () -> false);
        registry.add("spring.cloud.nacos.discovery.password", () -> "test-only");
        String url = "http://127.0.0.1:" + downstream.getAddress().getPort();
        String prefix = "spring.cloud.gateway.server.webflux.routes[0]";
        registry.add(prefix + ".id", () -> "test-user-route");
        registry.add(prefix + ".uri", () -> url);
        registry.add(prefix + ".predicates[0]", () -> "Path=/api/**");
    }

    @LocalServerPort int port;
    final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach void reset() { calls.set(0); }
    @AfterAll static void stop() { downstream.stop(0); executor.shutdownNow(); }

    HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }
    HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpRequest.Builder authorized(String path, String role) {
        return request(path).header("Authorization", "Bearer " + GatewayTestKeys.token(role));
    }

    @Test void anonymousBusinessRequestIsStoppedAtGateway() throws Exception {
        var response = send(request("/api/textbooks"));
        assertEquals(401, response.statusCode());
        assertEquals("gateway", response.headers().firstValue("X-Auth-Enforcer").orElseThrow());
        assertTrue(response.body().contains("UNAUTHORIZED"));
        assertEquals(0, calls.get());
    }

    @Test void spoofedIdentityWithoutJwtCannotLogin() throws Exception {
        var response = send(request("/api/textbooks")
                .header("X-User-Id", "admin").header("X-Role", "ADMIN")
                .header("X-Authenticated-User", "demo-admin")
                .header("X-Authenticated-Roles", "ADMIN"));
        assertEquals(401, response.statusCode());
        assertEquals(0, calls.get());
    }

    @Test void loginAndHealthRemainPublic() throws Exception {
        assertEquals(201, send(request("/api/auth/login")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))).statusCode());
        assertEquals(200, send(request("/actuator/health")).statusCode());
    }

    @Test void validUserCanAccessBusinessRoute() throws Exception {
        var response = send(authorized("/api/textbooks", "USER"));
        assertEquals(200, response.statusCode());
        assertEquals(1, calls.get());
    }

    @Test void userCannotCreateProfileEvenWithForgedAdminHeaders() throws Exception {
        var response = send(authorized("/api/users", "USER")
                .header("X-Role", "ADMIN")
                .header("X-Authenticated-Roles", "ADMIN")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
        assertEquals(403, response.statusCode());
        assertEquals("gateway", response.headers().firstValue("X-Auth-Enforcer").orElseThrow());
        assertEquals(0, calls.get());
    }

    @Test void adminCanCreateProfile() throws Exception {
        assertEquals(201, send(authorized("/api/users", "ADMIN")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))).statusCode());
        assertEquals(1, calls.get());
    }

    @Test void duplicateForgedHeadersAreRemovedAndReplaced() throws Exception {
        var response = send(authorized("/api/auth/request-context", "USER")
                .header("X-Authenticated-User", "attacker")
                .header("x-authenticated-user", "another-attacker")
                .header("X-Authenticated-Roles", "ADMIN")
                .header("X-User-Id", "9999"));
        assertEquals(200, response.statusCode());
        assertEquals("user=demo-user;roles=USER;legacy=null", response.body());
    }

    @Test void badSignatureIs401() throws Exception {
        String[] parts = GatewayTestKeys.token("USER").split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        assertEquals(401, send(request("/api/textbooks")
                .header("Authorization", "Bearer " + String.join(".", parts))).statusCode());
        assertEquals(0, calls.get());
    }

    @Test void expiredJwtIs401() throws Exception {
        String jwt = GatewayTestKeys.token("USER", "campus-textbook-auth", "campus-api", true);
        assertEquals(401, send(request("/api/textbooks")
                .header("Authorization", "Bearer " + jwt)).statusCode());
        assertEquals(0, calls.get());
    }

    @Test void wrongIssuerAndAudienceAre401() throws Exception {
        for (String jwt : new String[] {
                GatewayTestKeys.token("USER", "wrong", "campus-api", false),
                GatewayTestKeys.token("USER", "campus-textbook-auth", "wrong", false)}) {
            assertEquals(401, send(request("/api/textbooks")
                    .header("Authorization", "Bearer " + jwt)).statusCode());
        }
        assertEquals(0, calls.get());
    }

}