package com.bee.shop.service;

import com.bee.shop.entity.*;
import com.bee.shop.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor public class PaymentService { private final OrderRepository orders; private final PaymentRepository payments; public void callback(String orderNumber,String transactionId,String status){Order order=orders.findByOrderNumber(orderNumber).orElseThrow(); Payment payment=payments.findAll().stream().filter(x->x.getOrder().getId().equals(order.getId())).findFirst().orElseThrow(); boolean paid="settlement".equalsIgnoreCase(status)||"capture".equalsIgnoreCase(status);payment.setTransactionId(transactionId);payment.setStatus(paid?PaymentStatus.PAID:PaymentStatus.FAILED);payment.setPaidAt(paid?java.time.OffsetDateTime.now():null);order.setStatus(paid?OrderStatus.PAID:OrderStatus.CANCELLED);payments.save(payment);orders.save(order);} }
