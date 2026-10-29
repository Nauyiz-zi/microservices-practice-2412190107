package com.zjgsu.zy;

import com.zjgsu.zy.dto.TextbookDtos;
import com.zjgsu.zy.model.Textbook;
import com.zjgsu.zy.repository.*;
import com.zjgsu.zy.service.*;
import java.math.BigDecimal;
import java.net.ConnectException;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TextbookApiTest {
    @Autowired MockMvc mvc;
    @Autowired RestTemplate rest;
    @Autowired TextbookRepository books;
    @Autowired StockOperationRepository operations;
    @Autowired StockService stock;
    @Autowired TextbookService service;
    MockRestServiceServer server;

    @BeforeEach void before() {
        operations.deleteAll();
        books.deleteAll();
        server = MockRestServiceServer.bindTo(rest).build();
    }
    @AfterEach void verify() { server.verify(); }

    String createJson() {
        return """
                {"title":"Java教材","isbn":"9787300000001","author":"字远",
                "publisher":"教材出版社","conditionDescription":"九成新",
                "price":25.50,"stock":3,"sellerId":1}
                """;
    }

    Textbook fixture() {
        Textbook t = new Textbook();
        t.title = "Java教材"; t.isbn = "9787300000001"; t.author = "字远";
        t.conditionDescription = "九成新"; t.price = new BigDecimal("25.50");
        t.stock = 3; t.sellerId = 1L; t.listed = true;
        return books.saveAndFlush(t);
    }

    @Test void sellerVerifiedBeforeInsert() throws Exception {
        server.expect(requestTo("http://localhost:8081/api/users/1"))
                .andRespond(withSuccess("{\"id\":1,\"enabled\":true}", MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/textbooks").contentType(MediaType.APPLICATION_JSON).content(createJson()))
                .andExpect(status().isCreated());
        Long id = books.findAll().getFirst().id;
        mvc.perform(get("/api/textbooks/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Java教材"));
    }

    @Test void missingSellerReturns422AndDoesNotInsert() throws Exception {
        server.expect(requestTo("http://localhost:8081/api/users/1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        mvc.perform(post("/api/textbooks").contentType(MediaType.APPLICATION_JSON).content(createJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_NOT_FOUND"));
        assertEquals(0, books.count());
    }

    @Test void unavailableSellerServiceReturns503AndDoesNotInsert() throws Exception {
        server.expect(requestTo("http://localhost:8081/api/users/1"))
                .andRespond(withException(new ConnectException("connection refused")));
        mvc.perform(post("/api/textbooks").contentType(MediaType.APPLICATION_JSON).content(createJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
        assertEquals(0, books.count());
    }

    @Test void repeatedDeductionDoesNotDeductAgain() {
        Textbook t = fixture();
        String no = "ORD" + UUID.randomUUID().toString().replace("-", "");
        var request = new TextbookDtos.Deduct(2L, 1);
        var first = stock.deduct(t.id, no, request);
        var repeated = stock.deduct(t.id, no, request);
        assertEquals(first, repeated);
        assertEquals(2, books.findById(t.id).orElseThrow().stock);
        assertEquals(1, operations.count());
    }

    @Test void insufficientStockDoesNotChangeDatabase() {
        Textbook t = fixture();
        String no = "ORD" + UUID.randomUUID().toString().replace("-", "");
        ApiException ex = assertThrows(ApiException.class,
                () -> stock.deduct(t.id, no, new TextbookDtos.Deduct(2L, 4)));
        assertEquals("INSUFFICIENT_STOCK", ex.code());
        assertEquals(3, books.findById(t.id).orElseThrow().stock);
        assertEquals(0, operations.count());
    }

    @Test void conflictingRetryIsRejected() {
        Textbook t = fixture();
        String no = "ORD" + UUID.randomUUID().toString().replace("-", "");
        stock.deduct(t.id, no, new TextbookDtos.Deduct(2L, 1));
        assertThrows(ApiException.class,
                () -> stock.deduct(t.id, no, new TextbookDtos.Deduct(3L, 1)));
        assertEquals(2, books.findById(t.id).orElseThrow().stock);
    }

    @Test void health() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}

