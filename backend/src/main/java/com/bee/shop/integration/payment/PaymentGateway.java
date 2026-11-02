package com.bee.shop.integration.payment;

import com.bee.shop.entity.Order;

public interface PaymentGateway {

    record PaymentInit(String redirectUrl, String snapToken) {}

    String name();

    /** Membuat sesi pembayaran di provider untuk order yang baru dibuat. */
    PaymentInit createPayment(Order order, int expiryMinutes);
}
