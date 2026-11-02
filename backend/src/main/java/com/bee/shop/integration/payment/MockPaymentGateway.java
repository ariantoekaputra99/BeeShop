package com.bee.shop.integration.payment;

import com.bee.shop.entity.Order;

/** Gateway simulasi: mengarahkan ke halaman pembayaran demo di storefront. */
public class MockPaymentGateway implements PaymentGateway {

    private final String storefrontUrl;

    public MockPaymentGateway(String storefrontUrl) {
        this.storefrontUrl = storefrontUrl.replaceAll("/+$", "");
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public PaymentInit createPayment(Order order, int expiryMinutes) {
        return new PaymentInit(storefrontUrl + "/#/payment/" + order.getOrderNumber(), null);
    }
}
