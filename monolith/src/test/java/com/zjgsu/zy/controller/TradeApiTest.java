package com.zjgsu.zy.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.zjgsu.zy.repository.TextbookRepository;
import com.zjgsu.zy.repository.TradeOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TradeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TextbookRepository textbookRepository;

    @Autowired
    private TradeOrderRepository tradeOrderRepository;

    @BeforeEach
    void setUp() {
        tradeOrderRepository.deleteAll();
        textbookRepository.deleteAll();
    }

    @Test
    void shouldCreateAndQueryTextbook() throws Exception {
        String response = mockMvc.perform(
                        post("/api/textbooks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "Spring Boot 实战",
                                          "isbn": "9787300000004",
                                          "author": "赵六",
                                          "publisher": "人民邮电出版社",
                                          "conditionDescription": "九成新",
                                          "price": 45.50,
                                          "stock": 2,
                                          "sellerId": 1004
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        Long textbookId = jsonNode.get("id").asLong();

        mockMvc.perform(
                        get("/api/textbooks")
                                .param("keyword", "Spring")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(textbookId))
                .andExpect(jsonPath("$[0].title").value("Spring Boot 实战"));
    }

    @Test
    void shouldReturnBadRequestWhenStockIsInsufficient() throws Exception {
        String response = mockMvc.perform(
                        post("/api/textbooks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "计算机网络",
                                          "isbn": "9787300000005",
                                          "author": "钱七",
                                          "publisher": "电子工业出版社",
                                          "conditionDescription": "八成新",
                                          "price": 38.00,
                                          "stock": 1,
                                          "sellerId": 1005
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long textbookId =
                objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "buyerId": 2005,
                                          "textbookId": %d,
                                          "quantity": 2
                                        }
                                        """.formatted(textbookId))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"));
    }
}