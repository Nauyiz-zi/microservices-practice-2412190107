package com.zjgsu.zy.repository;

import com.zjgsu.zy.model.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TradeOrderRepository
        extends JpaRepository<TradeOrder, Long> {

    Optional<TradeOrder> findByOrderNo(String orderNo);
}