package com.bee.shop.integration.payment;

import com.bee.shop.dto.MidtransNotification;
import com.bee.shop.entity.Order;
import com.bee.shop.entity.OrderItem;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client Midtrans Snap. Dokumentasi: https://docs.midtrans.com/reference/snap-api
 * Server key hanya dibaca dari environment dan tidak pernah dikirim ke frontend.
 */
@Slf4j
public class MidtransSnapGateway implements PaymentGateway {

    static final String SANDBOX_URL = "https://app.sandbox.midtrans.com";
    static final String PRODUCTION_URL = "https://app.midtrans.com";

    private final String serverKey;
    private final String finishUrl;
    private final RestClient http;

    public MidtransSnapGateway(String serverKey, boolean sandbox, String storefrontUrl, RestClient.Builder builder) {
        this(serverKey, storefrontUrl, builder.baseUrl(sandbox ? SANDBOX_URL : PRODUCTION_URL));
    }

    MidtransSnapGateway(String serverKey, String storefrontUrl, RestClient.Builder configuredBuilder) {
        if (serverKey == null || serverKey.isBlank()) {
            throw new IllegalStateException("MIDTRANS_SERVER_KEY wajib diisi untuk provider midtrans");
        }
        this.serverKey = serverKey;
        this.finishUrl = storefrontUrl.replaceAll("/+$", "") + "/#/orders";
        String basic = Base64.getEncoder().encodeToString((serverKey + ":").getBytes(StandardCharsets.UTF_8));
        this.http = configuredBuilder
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    record SnapResponse(String token, @JsonProperty("redirect_url") String redirectUrl) {}

    @Override
    public String name() {
        return "midtrans";
    }

    @Override
    public PaymentInit createPayment(Order order, int expiryMinutes) {
        try {
            SnapResponse res = http.post()
                    .uri("/snap/v1/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequest(order, expiryMinutes))
                    .retrieve()
                    .body(SnapResponse.class);
            if (res == null || res.redirectUrl() == null) {
                throw new PaymentGatewayException("Response Midtrans kosong");
            }
            return new PaymentInit(res.redirectUrl(), res.token());
        } catch (RestClientException e) {
            log.error("Gagal membuat transaksi Midtrans untuk {}", order.getOrderNumber(), e);
            throw new PaymentGatewayException("Payment gateway tidak dapat dihubungi");
        }
    }

    Map<String, Object> buildRequest(Order order, int expiryMinutes) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (OrderItem i : order.getItems()) {
            items.add(Map.of(
                    "id", i.getSku(),
                    "price", rupiah(i.getUnitPrice()),
                    "quantity", i.getQuantity(),
                    "name", truncate(i.getProductName(), 50)));
        }
        if (order.getShippingFee().signum() > 0) {
            items.add(Map.of("id", "SHIPPING", "price", rupiah(order.getShippingFee()), "quantity", 1,
                    "name", truncate("Ongkir " + order.getCourier() + " " + order.getCourierService(), 50)));
        }

        Map<String, Object> address = Map.of(
                "first_name", truncate(order.getCustomerName(), 50),
                "phone", order.getCustomerPhone(),
                "address", truncate(order.getAddress(), 200),
                "city", order.getCity(),
                "postal_code", order.getPostalCode(),
                "country_code", "IDN");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transaction_details", Map.of("order_id", order.getOrderNumber(), "gross_amount", rupiah(order.getTotal())));
        body.put("item_details", items);
        body.put("customer_details", Map.of(
                "first_name", truncate(order.getCustomerName(), 50),
                "email", order.getCustomerEmail(),
                "phone", order.getCustomerPhone(),
                "shipping_address", address));
        body.put("expiry", Map.of("unit", "minute", "duration", expiryMinutes));
        body.put("callbacks", Map.of("finish", finishUrl));
        return body;
    }

    /**
     * Verifikasi signature notifikasi: SHA512(order_id + status_code + gross_amount + server_key).
     */
    public boolean isValidSignature(MidtransNotification n) {
        if (n.signatureKey() == null || n.orderId() == null || n.statusCode() == null || n.grossAmount() == null) {
            return false;
        }
        String expected = sha512Hex(n.orderId() + n.statusCode() + n.grossAmount() + serverKey);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                n.signatureKey().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    static String sha512Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-512").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long rupiah(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
