package com.zjgsu.zy.repository;

import com.zjgsu.zy.model.TradeOrder;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<TradeOrder, Long> {
    Optional<TradeOrder> findByRequestId(String requestId);
}

