package com.zjgsu.zy.service;

import com.zjgsu.zy.client.TextbookClient;
import com.zjgsu.zy.client.UserClient;
import com.zjgsu.zy.dto.OrderDtos;
import com.zjgsu.zy.model.TradeOrder;
import com.zjgsu.zy.repository.OrderRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final UserClient users;
    private final TextbookClient books;
    private final OrderWriter writer;

    public OrderService(OrderRepository orders, UserClient users,
                        TextbookClient books, OrderWriter writer) {
        this.orders = orders;
        this.users = users;
        this.books = books;
        this.writer = writer;
    }

    public OrderDtos.View create(OrderDtos.Create request) {
        String requestId = UUID.fromString(request.requestId()).toString();
        var existing = orders.findByRequestId(requestId);
        if (existing.isPresent()) {
            writer.requireSame(existing.get(), request);
            return view(existing.get());
        }

        // 所有远程校验及库存请求成功后，才写本地订单。
        users.requireEnabled(request.buyerId());
        books.get(request.textbookId());

        // 原requestId稳定映射为原orderNo，失败重试不更换库存幂等标识。
        String orderNo = "ORD" + requestId.replace("-", "");
        var result = books.deduct(request.textbookId(), request.buyerId(),
                request.quantity(), orderNo);
        return view(writer.save(request, requestId, result));
    }

    public OrderDtos.View get(Long id) {
        return view(orders.findById(id).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在")));
    }

    private OrderDtos.View view(TradeOrder order) {
        return new OrderDtos.View(order.id, order.orderNo, order.requestId, order.buyerId,
                order.sellerId, order.textbookId, order.textbookTitle, order.quantity,
                order.unitPrice, order.totalAmount, order.status, order.createdAt);
    }
}

