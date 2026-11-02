package com.bee.shop.controller;

import com.bee.shop.config.AppProperties;
import com.bee.shop.dto.MidtransNotification;
import com.bee.shop.integration.payment.PaymentGateway;
import com.bee.shop.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService payments;
    private final PaymentGateway gateway;
    private final AppProperties props;

    /** Konfigurasi publik untuk frontend. Server key TIDAK pernah dikirim. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        String clientKey = props.payment().clientKey();
        return Map.of(
                "provider", gateway.name(),
                "sandbox", props.payment().sandbox(),
                "clientKey", clientKey == null ? "" : clientKey);
    }

    /**
     * URL ini didaftarkan di dashboard Midtrans sebagai "Payment Notification URL".
     * Diverifikasi dengan signature_key, jadi aman walau publik.
     */
    @PostMapping("/midtrans/notification")
    public ResponseEntity<Map<String, String>> midtrans(@RequestBody MidtransNotification notification) {
        payments.handleMidtrans(notification);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /** Hanya aktif di mode mock (development) untuk mensimulasikan hasil pembayaran. */
    @PostMapping("/mock/{orderNumber}")
    public ResponseEntity<Map<String, String>> mock(@PathVariable String orderNumber,
                                                    @RequestParam(defaultValue = "success") String result) {
        payments.handleMock(orderNumber, "success".equalsIgnoreCase(result));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
