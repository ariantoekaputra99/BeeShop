package com.bee.shop.repository;

import com.bee.shop.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface OrderRepository extends JpaRepository<Order,Long>{Optional<Order> findByOrderNumber(String orderNumber);}
