package com.bee.shop.service;

import com.bee.shop.dto.*;
import com.bee.shop.entity.*;
import com.bee.shop.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.OffsetDateTime;

@Service @RequiredArgsConstructor public class OrderService { private final ProductRepository products; private final OrderRepository orders; private final PaymentRepository payments; private final ShippingService shipping;
 @Transactional public PaymentSession checkout(CheckoutRequest request){ ShippingOption option=shipping.options(new ShippingRateRequest("store",request.address(),"JNE",1000)).stream().filter(x->x.id().equals(request.shippingOptionId())).findFirst().orElseThrow(()->new IllegalArgumentException("Shipping option not found")); Order order=Order.builder().orderNumber("BEE-"+System.currentTimeMillis()).customerName(request.customerName()).customerEmail(request.customerEmail()).address(request.address()).status(OrderStatus.PENDING_PAYMENT).shippingFee(option.fee()).courier(option.courier()+" "+option.service()).createdAt(OffsetDateTime.now()).subtotal(BigDecimal.ZERO).total(BigDecimal.ZERO).build(); BigDecimal subtotal=BigDecimal.ZERO; for(var line:request.items()){Product p=products.findById(line.productId()).orElseThrow(); if(p.getStock()<line.quantity())throw new IllegalArgumentException("Stock tidak cukup: "+p.getName()); BigDecimal price=p.getPrice(); order.addItem(OrderItem.builder().product(p).quantity(line.quantity()).unitPrice(price).lineTotal(price.multiply(BigDecimal.valueOf(line.quantity()))).build()); subtotal=subtotal.add(price.multiply(BigDecimal.valueOf(line.quantity()))); p.setStock(p.getStock()-line.quantity());} order.setSubtotal(subtotal);order.setTotal(subtotal.add(option.fee())); orders.save(order); Payment payment=payments.save(Payment.builder().order(order).provider("midtrans").amount(order.getTotal()).status(PaymentStatus.PENDING).build()); return new PaymentSession(order.getOrderNumber(),payment.getProvider(),"PAY-"+payment.getId(),"https://app.sandbox.midtrans.com/snap/v4/redirection/demo-"+payment.getId(),order.getTotal()); }
}
