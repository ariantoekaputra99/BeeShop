package com.bee.shop.dto;

import com.bee.shop.entity.Order;
import com.bee.shop.entity.Payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        String orderNumber, String status,
        String customerName, String customerEmail, String customerPhone,
        String address, String city, String postalCode, String notes,
        BigDecimal subtotal, BigDecimal shippingFee, BigDecimal total, Integer totalWeightGram,
        String courier, String courierService, String shippingEtd, String trackingNumber,
        String paymentStatus, String paymentType, String paymentRedirectUrl,
        OffsetDateTime createdAt, OffsetDateTime expiresAt, OffsetDateTime paidAt,
        OffsetDateTime shippedAt, OffsetDateTime deliveredAt, OffsetDateTime cancelledAt,
        List<Item> items, List<History> history
) {
    public record Item(String sku, String productName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}

    public record History(String status, String note, OffsetDateTime at, String actor) {}

    /** Untuk pelanggan: nama akun admin tidak ditampilkan. */
    public static OrderResponse forCustomer(Order o, Payment p) {
        return build(o, p, false);
    }

    public static OrderResponse forAdmin(Order o, Payment p) {
        return build(o, p, true);
    }

    private static OrderResponse build(Order o, Payment p, boolean includeActor) {
        boolean showPaymentLink = p != null && "PENDING_PAYMENT".equals(o.getStatus().name());
        return new OrderResponse(
                o.getOrderNumber(), o.getStatus().name(),
                o.getCustomerName(), o.getCustomerEmail(), o.getCustomerPhone(),
                o.getAddress(), o.getCity(), o.getPostalCode(), o.getNotes(),
                o.getSubtotal(), o.getShippingFee(), o.getTotal(), o.getTotalWeightGram(),
                o.getCourier(), o.getCourierService(), o.getShippingEtd(), o.getTrackingNumber(),
                p == null ? null : p.getStatus().name(),
                p == null ? null : p.getPaymentType(),
                showPaymentLink ? p.getRedirectUrl() : null,
                o.getCreatedAt(), o.getExpiresAt(), o.getPaidAt(),
                o.getShippedAt(), o.getDeliveredAt(), o.getCancelledAt(),
                o.getItems().stream().map(i -> new Item(i.getSku(), i.getProductName(), i.getQuantity(),
                        i.getUnitPrice(), i.getLineTotal())).toList(),
                o.getHistory().stream().map(h -> new History(h.getStatus().name(), h.getNote(), h.getCreatedAt(),
                        includeActor ? h.getActor() : null)).toList()
        );
    }
}
