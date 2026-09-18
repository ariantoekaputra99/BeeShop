package com.bee.shop.repository;

import com.bee.shop.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRepository extends JpaRepository<Payment,Long>{}
