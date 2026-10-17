package com.zjgsu.zy.repository;

import com.zjgsu.zy.model.Textbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class TextbookRepositoryTest {

    @Autowired
    private TextbookRepository textbookRepository;

    @BeforeEach
    void setUp() {
        textbookRepository.deleteAll();
    }

    @Test
    void shouldSaveAndSearchTextbook() {
        Textbook textbook = new Textbook();
        textbook.setTitle("Java 程序设计");
        textbook.setIsbn("9787300000001");
        textbook.setAuthor("张三");
        textbook.setPublisher("高等教育出版社");
        textbook.setConditionDescription("九成新");
        textbook.setPrice(new BigDecimal("29.90"));
        textbook.setStock(3);
        textbook.setSellerId(1001L);
        textbook.setListed(true);

        textbookRepository.save(textbook);

        List<Textbook> result =
                textbookRepository.searchListed("Java");

        assertEquals(1, result.size());
    }
}