package com.bee.shop.service;

import com.bee.shop.dto.MidtransNotification;
import com.bee.shop.entity.*;
import com.bee.shop.exception.BusinessException;
import com.bee.shop.exception.NotFoundException;
import com.bee.shop.integration.payment.MidtransSnapGateway;
import com.bee.shop.integration.payment.PaymentGateway;
import com.bee.shop.repository.OrderRepository;
import com.bee.shop.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final OrderLifecycle lifecycle;

    enum Outcome { PAID, PENDING, FAILED, EXPIRED, IGNORED }

    /** Webhook Midtrans. Idempoten: notifikasi yang sama boleh datang berkali-kali. */
    @Transactional
    public void handleMidtrans(MidtransNotification n) {
        if (!(gateway instanceof MidtransSnapGateway midtrans)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Midtrans tidak aktif");
        }
        if (!midtrans.isValidSignature(n)) {
            log.warn("Signature Midtrans tidak valid untuk order {}", n.orderId());
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Signature tidak valid");
        }
        Order order = orders.findByOrderNumber(n.orderId()).orElseThrow(() -> new NotFoundException("Order tidak ditemukan"));
        Payment payment = payments.findByOrder_Id(order.getId()).orElseThrow(() -> new NotFoundException("Payment tidak ditemukan"));

        if (new BigDecimal(n.grossAmount()).compareTo(order.getTotal()) != 0) {
            log.error("Nominal notifikasi {} tidak sama dengan total order {} ({})", n.grossAmount(), order.getTotal(), n.orderId());
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Nominal tidak sesuai");
        }

        payment.setTransactionId(n.transactionId());
        payment.setPaymentType(n.paymentType());
        payment.setProviderStatus(n.transactionStatus());
        apply(order, payment, map(n.transactionStatus(), n.fraudStatus()), "Midtrans: " + n.transactionStatus());
    }

    /** Simulasi pembayaran, hanya tersedia saat gateway mock aktif (development). */
    @Transactional
    public void handleMock(String orderNumber, boolean success) {
        if (!"mock".equals(gateway.name())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Mock payment tidak aktif");
        }
        Order order = orders.findByOrderNumber(orderNumber).orElseThrow(() -> new NotFoundException("Order tidak ditemukan"));
        Payment payment = payments.findByOrder_Id(order.getId()).orElseThrow(() -> new NotFoundException("Payment tidak ditemukan"));
        payment.setTransactionId("MOCK-" + orderNumber);
        payment.setPaymentType("mock");
        payment.setProviderStatus(success ? "settlement" : "deny");
        apply(order, payment, success ? Outcome.PAID : Outcome.FAILED, "Pembayaran simulasi");
    }

    static Outcome map(String transactionStatus, String fraudStatus) {
        if (transactionStatus == null) return Outcome.IGNORED;
        return switch (transactionStatus) {
            case "capture" -> "accept".equals(fraudStatus) || fraudStatus == null ? Outcome.PAID : Outcome.PENDING;
            case "settlement" -> Outcome.PAID;
            case "pending", "authorize" -> Outcome.PENDING;
            case "deny", "cancel", "failure" -> Outcome.FAILED;
            case "expire" -> Outcome.EXPIRED;
            default -> Outcome.IGNORED; // refund, chargeback, dll ditangani manual oleh admin
        };
    }

    private void apply(Order order, Payment payment, Outcome outcome, String note) {
        switch (outcome) {
            case PAID -> {
                if (payment.getStatus() == PaymentStatus.PAID) return; // duplikat notifikasi
                payment.setStatus(PaymentStatus.PAID);
                payment.setPaidAt(OffsetDateTime.now());
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    lifecycle.transition(order, OrderStatus.PAID, note);
                } else {
                    // Dana masuk setelah order dibatalkan/expired: stok sudah dilepas, perlu refund manual.
                    log.error("Pembayaran diterima untuk order {} berstatus {}", order.getOrderNumber(), order.getStatus());
                    order.addHistory(order.getStatus(), "Pembayaran diterima setelah order " + order.getStatus()
                            + ". Perlu refund manual.");
                }
            }
            case FAILED, EXPIRED -> {
                if (payment.getStatus() == PaymentStatus.PAID) return;
                payment.setStatus(outcome == Outcome.EXPIRED ? PaymentStatus.EXPIRED : PaymentStatus.FAILED);
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    lifecycle.transition(order, OrderStatus.CANCELLED, note);
                }
            }
            case PENDING, IGNORED -> { }
        }
    }
}
