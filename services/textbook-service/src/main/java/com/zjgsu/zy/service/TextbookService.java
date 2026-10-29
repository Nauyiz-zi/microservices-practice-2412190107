package com.zjgsu.zy.service;

import com.zjgsu.zy.client.UserClient;
import com.zjgsu.zy.dto.TextbookDtos;
import com.zjgsu.zy.model.Textbook;
import com.zjgsu.zy.repository.TextbookRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TextbookService {
    private final TextbookRepository books;
    private final UserClient users;

    public TextbookService(TextbookRepository books, UserClient users) {
        this.books = books;
        this.users = users;
    }

    public TextbookDtos.View create(TextbookDtos.Create request) {
        // 先完成真实 HTTP 校验；失败时不调用本地 save。
        users.requireEnabled(request.sellerId());
        if (books.existsBySellerIdAndIsbn(request.sellerId(), request.isbn().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "TEXTBOOK_EXISTS", "该卖家已发布相同ISBN");
        }
        Textbook t = new Textbook();
        t.title = request.title().trim();
        t.isbn = request.isbn().trim();
        t.author = request.author().trim();
        t.publisher = request.publisher();
        t.conditionDescription = request.conditionDescription().trim();
        t.price = request.price();
        t.stock = request.stock();
        t.sellerId = request.sellerId();
        t.listed = true;
        return view(books.saveAndFlush(t));
    }

    public TextbookDtos.View get(Long id) {
        return view(books.findById(id).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "TEXTBOOK_NOT_FOUND", "教材不存在")));
    }

    public List<TextbookDtos.View> list(String keyword) {
        return books.search(keyword.trim()).stream().map(this::view).toList();
    }

    private TextbookDtos.View view(Textbook t) {
        return new TextbookDtos.View(t.id, t.title, t.isbn, t.author, t.publisher,
                t.conditionDescription, t.price, t.stock, t.sellerId, t.listed, t.createdAt);
    }
}

