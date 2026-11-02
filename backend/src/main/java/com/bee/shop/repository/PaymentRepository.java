package com.bee.shop.repository;

import com.bee.shop.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrder_Id(Long orderId);

    Optional<Payment> findByOrder_OrderNumber(String orderNumber);
}
