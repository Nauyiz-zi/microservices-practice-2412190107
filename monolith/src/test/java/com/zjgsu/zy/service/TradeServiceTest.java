package com.zjgsu.zy.service;

import com.zjgsu.zy.dto.CreateOrderRequest;
import com.zjgsu.zy.dto.CreateTextbookRequest;
import com.zjgsu.zy.dto.OrderResponse;
import com.zjgsu.zy.model.Textbook;
import com.zjgsu.zy.repository.TextbookRepository;
import com.zjgsu.zy.repository.TradeOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TradeServiceTest {

    @Autowired
    private TradeService tradeService;

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
    void shouldCreateOrderAndDecreaseStock() {
        var textbook = tradeService.createTextbook(
                new CreateTextbookRequest(
                        "数据结构",
                        "9787300000002",
                        "李四",
                        "清华大学出版社",
                        "八成新",
                        new BigDecimal("35.00"),
                        2,
                        1002L
                )
        );

        OrderResponse order = tradeService.createOrder(
                new CreateOrderRequest(2001L, textbook.id(), 1)
        );

        assertNotNull(order.id());
        assertEquals(1, order.quantity());
        assertEquals(new BigDecimal("35.00"), order.totalAmount());

        Textbook saved = textbookRepository.findById(textbook.id()).orElseThrow();
        assertEquals(1, saved.getStock());
    }

    @Test
    void shouldRejectOrderWhenStockIsInsufficient() {
        var textbook = tradeService.createTextbook(
                new CreateTextbookRequest(
                        "操作系统",
                        "9787300000003",
                        "王五",
                        "机械工业出版社",
                        "九成新",
                        new BigDecimal("40.00"),
                        1,
                        1003L
                )
        );

        assertThrows(
                BusinessException.class,
                () -> tradeService.createOrder(
                        new CreateOrderRequest(2002L, textbook.id(), 2)
                )
        );
    }
}