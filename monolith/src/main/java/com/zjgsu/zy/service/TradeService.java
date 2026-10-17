package com.zjgsu.zy.service;

import com.zjgsu.zy.dto.CreateOrderRequest;
import com.zjgsu.zy.dto.CreateTextbookRequest;
import com.zjgsu.zy.dto.OrderResponse;
import com.zjgsu.zy.dto.TextbookResponse;
import com.zjgsu.zy.model.OrderStatus;
import com.zjgsu.zy.model.Textbook;
import com.zjgsu.zy.model.TradeOrder;
import com.zjgsu.zy.repository.TextbookRepository;
import com.zjgsu.zy.repository.TradeOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class TradeService {

    private final TextbookRepository textbookRepository;
    private final TradeOrderRepository tradeOrderRepository;

    public TradeService(
            TextbookRepository textbookRepository,
            TradeOrderRepository tradeOrderRepository
    ) {
        this.textbookRepository = textbookRepository;
        this.tradeOrderRepository = tradeOrderRepository;
    }

    @Transactional
    public TextbookResponse createTextbook(CreateTextbookRequest request) {
        require(request != null, "请求体不能为空");
        require(request.title() != null && !request.title().isBlank(), "书名不能为空");
        require(request.isbn() != null && !request.isbn().isBlank(), "ISBN 不能为空");
        require(request.author() != null && !request.author().isBlank(), "作者不能为空");
        require(request.conditionDescription() != null
                && !request.conditionDescription().isBlank(), "教材成色不能为空");
        require(request.price() != null
                && request.price().compareTo(BigDecimal.ZERO) > 0, "价格必须大于 0");
        require(request.stock() != null && request.stock() >= 0, "库存不能小于 0");
        require(request.sellerId() != null && request.sellerId() > 0, "卖家 ID 必须大于 0");

        Textbook textbook = new Textbook();
        textbook.setTitle(request.title().trim());
        textbook.setIsbn(request.isbn().trim());
        textbook.setAuthor(request.author().trim());
        textbook.setPublisher(request.publisher());
        textbook.setConditionDescription(request.conditionDescription().trim());
        textbook.setPrice(request.price());
        textbook.setStock(request.stock());
        textbook.setSellerId(request.sellerId());
        textbook.setListed(true);

        return toTextbookResponse(textbookRepository.save(textbook));
    }

    @Transactional(readOnly = true)
    public List<TextbookResponse> listTextbooks(String keyword) {
        String actualKeyword = keyword == null ? "" : keyword.trim();

        return textbookRepository.searchListed(actualKeyword)
                .stream()
                .map(this::toTextbookResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TextbookResponse getTextbook(Long id) {
        Textbook textbook = textbookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("教材不存在：" + id));

        return toTextbookResponse(textbook);
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        require(request != null, "请求体不能为空");
        require(request.buyerId() != null && request.buyerId() > 0, "买家 ID 必须大于 0");
        require(request.textbookId() != null && request.textbookId() > 0, "教材 ID 无效");
        require(request.quantity() != null && request.quantity() > 0, "购买数量必须大于 0");

        Textbook textbook = textbookRepository.findById(request.textbookId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("教材不存在：" + request.textbookId()));

        require(Boolean.TRUE.equals(textbook.getListed()), "教材当前未上架");
        require(textbook.getStock() >= request.quantity(),
                "库存不足，当前库存为：" + textbook.getStock());

        textbook.setStock(textbook.getStock() - request.quantity());
        textbookRepository.save(textbook);

        TradeOrder order = new TradeOrder();
        order.setOrderNo(generateOrderNo());
        order.setBuyerId(request.buyerId());
        order.setSellerId(textbook.getSellerId());
        order.setTextbook(textbook);
        order.setTextbookTitle(textbook.getTitle());
        order.setQuantity(request.quantity());
        order.setUnitPrice(textbook.getPrice());
        order.setTotalAmount(
                textbook.getPrice()
                        .multiply(BigDecimal.valueOf(request.quantity()))
        );
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        return toOrderResponse(tradeOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        TradeOrder order = tradeOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("订单不存在：" + id));

        return toOrderResponse(order);
    }

    private String generateOrderNo() {
        return "ORD" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(message);
        }
    }

    private TextbookResponse toTextbookResponse(Textbook textbook) {
        return new TextbookResponse(
                textbook.getId(),
                textbook.getTitle(),
                textbook.getIsbn(),
                textbook.getAuthor(),
                textbook.getPublisher(),
                textbook.getConditionDescription(),
                textbook.getPrice(),
                textbook.getStock(),
                textbook.getSellerId(),
                textbook.getListed(),
                textbook.getCreatedAt()
        );
    }

    private OrderResponse toOrderResponse(TradeOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getBuyerId(),
                order.getSellerId(),
                order.getTextbook().getId(),
                order.getTextbookTitle(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}