package com.zjgsu.zy.service;

import com.zjgsu.zy.client.TextbookClient;
import com.zjgsu.zy.dto.OrderDtos;
import com.zjgsu.zy.model.TradeOrder;
import com.zjgsu.zy.repository.OrderRepository;
import java.math.BigDecimal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OrderWriter {
    private final OrderRepository orders;
    public OrderWriter(OrderRepository orders) { this.orders = orders; }

    public TradeOrder save(OrderDtos.Create request, String requestId,
                           TextbookClient.Deduction result) {
        TradeOrder order = new TradeOrder();
        order.requestId = requestId;
        order.orderNo = result.orderNo();
        order.buyerId = request.buyerId();
        order.sellerId = result.sellerId();
        order.textbookId = result.textbookId();
        order.textbookTitle = result.textbookTitle();
        order.quantity = result.quantity();
        order.unitPrice = result.unitPrice();
        order.totalAmount = result.unitPrice().multiply(BigDecimal.valueOf(result.quantity()));
        order.status = "PENDING_PAYMENT";

        // 此处不跨远程请求持有本地事务。Repository 的 saveAndFlush 自带本地事务。
        try {
            return orders.saveAndFlush(order);
        } catch (DataIntegrityViolationException ex) {
            var existing = orders.findByRequestId(requestId);
            if (existing.isPresent()) {
                requireSame(existing.get(), request);
                return existing.get();
            }
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_PERSIST_FAILED",
                    "订单数据写入冲突，请保留原requestId，不要生成新编号重试");
        }
    }

    public void requireSame(TradeOrder existing, OrderDtos.Create request) {
        if (!existing.buyerId.equals(request.buyerId())
                || !existing.textbookId.equals(request.textbookId())
                || !existing.quantity.equals(request.quantity())) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "同一requestId不能对应不同的买家、教材或数量");
        }
    }
}

