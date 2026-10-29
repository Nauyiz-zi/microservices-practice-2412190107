package com.zjgsu.zy.service;

import com.zjgsu.zy.dto.TextbookDtos;
import com.zjgsu.zy.model.StockOperation;
import com.zjgsu.zy.model.Textbook;
import com.zjgsu.zy.repository.StockOperationRepository;
import com.zjgsu.zy.repository.TextbookRepository;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {
    private final TextbookRepository books;
    private final StockOperationRepository operations;

    public StockService(TextbookRepository books, StockOperationRepository operations) {
        this.books = books;
        this.operations = operations;
    }

    @Transactional
    public TextbookDtos.Deduction deduct(Long id, String orderNo, TextbookDtos.Deduct request) {
        if (!orderNo.matches("ORD[0-9a-f]{32}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_NO", "订单编号格式无效");
        }
        // 先锁教材，再检查幂等记录，避免相同教材的并发重复扣减。
        Textbook t = books.findLockedById(id).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "TEXTBOOK_NOT_FOUND", "教材不存在"));
        var previous = operations.findByOrderNo(orderNo);
        if (previous.isPresent()) {
            StockOperation op = previous.get();
            if (!op.textbookId.equals(id) || !op.buyerId.equals(request.buyerId())
                    || !op.quantity.equals(request.quantity())) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "IDEMPOTENCY_CONFLICT", "同一订单编号不能对应不同的请求数据");
            }
            return view(op);
        }
        if (!Boolean.TRUE.equals(t.listed)) {
            throw new ApiException(HttpStatus.CONFLICT, "TEXTBOOK_UNLISTED", "教材未上架");
        }
        if (t.stock < request.quantity()) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "库存不足");
        }
        if (t.price.multiply(BigDecimal.valueOf(request.quantity()))
                .compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNT_TOO_LARGE", "订单金额超出范围");
        }
        t.stock -= request.quantity();
        StockOperation op = new StockOperation();
        op.orderNo = orderNo;
        op.textbookId = t.id;
        op.buyerId = request.buyerId();
        op.quantity = request.quantity();
        op.unitPrice = t.price;
        op.textbookTitle = t.title;
        op.sellerId = t.sellerId;
        operations.saveAndFlush(op);
        return view(op);
    }

    private TextbookDtos.Deduction view(StockOperation op) {
        return new TextbookDtos.Deduction(op.orderNo, op.textbookId, op.buyerId,
                op.sellerId, op.textbookTitle, op.quantity, op.unitPrice);
    }
}

