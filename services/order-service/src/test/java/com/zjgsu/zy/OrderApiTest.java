package com.zjgsu.zy;

import com.zjgsu.zy.dto.OrderDtos;
import com.zjgsu.zy.repository.OrderRepository;
import com.zjgsu.zy.service.OrderService;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;

import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.zjgsu.zy.client.UserClient;
import com.zjgsu.zy.client.UserFeignClient;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;

import com.zjgsu.zy.client.UserFeignClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiTest {
    @Autowired MockMvc mvc;
    @Autowired RestTemplate rest;
    @Autowired OrderRepository orders;
    @Autowired OrderService service;
    @MockitoBean
    UserFeignClient userFeign;
    MockRestServiceServer server;
    final String requestId = "7c8067da-3f0b-4dc4-badc-7ab86fa45721";
    final String orderNo = "ORD7c8067da3f0b4dc4badc7ab86fa45721";

    @BeforeEach void before() {
        orders.deleteAll();
        server = MockRestServiceServer.bindTo(rest).build();
    }
    @AfterEach void verify() { server.verify(); }

    String createJson() {
        return """
                {"requestId":"%s","buyerId":2,"textbookId":1,"quantity":1}
                """.formatted(requestId);
    }

    void buyerOk() {
        when(userFeign.get(2L)).thenReturn(
                new UserClient.UserInfo(2L, "test-002", "测试买家", true));
    }

    void bookOk() {
        server.expect(requestTo("http://localhost:8082/api/textbooks/1"))
                .andRespond(withSuccess("""
                        {"id":1,"sellerId":1,"title":"Java教材","price":25.50,"stock":3,"listed":true}
                        """, MediaType.APPLICATION_JSON));
    }

    void deductionOk() {
        server.expect(requestTo("http://localhost:8082/internal/textbooks/1/stock-deductions/" + orderNo))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"buyerId\":2,\"quantity\":1}"))
                .andRespond(withSuccess("""
                        {"orderNo":"%s","textbookId":1,"buyerId":2,"sellerId":1,
                        "textbookTitle":"Java教材","quantity":1,"unitPrice":25.50}
                        """.formatted(orderNo), MediaType.APPLICATION_JSON));
    }

    @Test void successfulOrderAndIdempotentRetry() throws Exception {
        buyerOk(); bookOk(); deductionOk();
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(createJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalAmount").value(25.50));
        Long id = orders.findAll().getFirst().id;
        mvc.perform(get("/api/orders/{id}", id)).andExpect(status().isOk());
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(createJson()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id));
        assertEquals(1, orders.count());
    }

    @Test
    void missingBookReturns422AndDoesNotInsert() throws Exception {
        buyerOk();

        server.expect(requestTo("http://localhost:8082/api/textbooks/1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_NOT_FOUND"));

        assertEquals(0, orders.count());
    }

    @Test
    void missingBuyerReturns422AndDoesNotInsert() throws Exception {
        when(userFeign.get(2L)).thenThrow(userError(404));

        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("用户不存在，无法创建订单"));

        assertEquals(0, orders.count());
    }

    @Test void unavailableBuyerServiceReturns503AndDoesNotInsert() throws Exception {
        when(userFeign.get(2L)).thenThrow(userError(503));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(createJson()))
                .andExpect(status().isServiceUnavailable());
        assertEquals(0, orders.count());
    }

    @Test void inventoryReadTimeoutReturns503AndDoesNotInsert() throws Exception {
        buyerOk(); bookOk();
        server.expect(requestTo("http://localhost:8082/internal/textbooks/1/stock-deductions/" + orderNo))
                .andRespond(withException(new SocketTimeoutException("read timed out")));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(createJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
        assertEquals(0, orders.count());
    }

    @Test void rejectedStockDoesNotInsert() throws Exception {
        buyerOk(); bookOk();
        server.expect(requestTo("http://localhost:8082/internal/textbooks/1/stock-deductions/" + orderNo))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(createJson()))
                .andExpect(status().isConflict());
        assertEquals(0, orders.count());
    }

    @Test void malformedRequestDoesNotInsert() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"not-a-uuid\",\"buyerId\":2,\"textbookId\":1,\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, orders.count());
    }

    @Test void health() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    FeignException userError(int status) {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://user-service/api/users/2",
                Map.of(),
                (byte[]) null,
                StandardCharsets.UTF_8,
                null);

        Response response = Response.builder()
                .status(status)
                .reason("test")
                .request(request)
                .headers(Map.of())
                .build();

        return FeignException.errorStatus("UserFeignClient#get", response);
    }
}

