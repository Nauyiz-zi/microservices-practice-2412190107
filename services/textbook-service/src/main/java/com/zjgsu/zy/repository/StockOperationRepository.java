package com.zjgsu.zy.repository;

import com.zjgsu.zy.model.StockOperation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockOperationRepository extends JpaRepository<StockOperation, Long> {
    Optional<StockOperation> findByOrderNo(String orderNo);
}

