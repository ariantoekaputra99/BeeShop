package com.bee.shop;

import com.bee.shop.entity.Order;
import com.bee.shop.entity.OrderStatus;
import com.bee.shop.integration.payment.MidtransSnapGateway;
import com.bee.shop.integration.payment.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MidtransNotificationTest extends TestSupport {

    static final String SERVER_KEY = "SB-Mid-server-TEST";

    /** Gateway Midtrans asli untuk verifikasi signature, tanpa memanggil API Snap. */
    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        PaymentGateway midtransForTest() {
            return new MidtransSnapGateway(SERVER_KEY, true, "http://localhost:5173", RestClient.builder()) {
                @Override
                public PaymentInit createPayment(Order order, int expiryMinutes) {
                    return new PaymentInit("https://app.sandbox.midtrans.com/snap/v4/redirection/fake", "fake-token");
                }
            };
        }
    }

    @Test
    void validSettlementMarksOrderPaid() throws Exception {
        String n = checkout();
        String gross = grossOf(n);
        notify(n, "200", gross, "settlement", sign(n, "200", gross)).andExpect(status().isOk());
        assertThat(orders.findByOrderNumber(n).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void forgedSignatureIsRejected() throws Exception {
        String n = checkout();
        notify(n, "200", grossOf(n), "settlement", "deadbeef").andExpect(status().isUnauthorized());
        assertThat(orders.findByOrderNumber(n).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void amountMismatchIsRejectedEvenWithValidSignature() throws Exception {
        String n = checkout();
        notify(n, "200", "1000.00", "settlement", sign(n, "200", "1000.00")).andExpect(status().isUnprocessableEntity());
        assertThat(orders.findByOrderNumber(n).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void expireCancelsOrderAndRestoresStock() throws Exception {
        String n = checkout();
        assertThat(stockOf(kopi)).isEqualTo(2);
        String gross = grossOf(n);
        notify(n, "407", gross, "expire", sign(n, "407", gross)).andExpect(status().isOk());
        assertThat(orders.findByOrderNumber(n).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(kopi)).isEqualTo(3);
    }

    @Test
    void mockEndpointIsDisabledWhenMidtransActive() throws Exception {
        String n = checkout();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payments/mock/" + n))
                .andExpect(status().isNotFound());
    }

    private String checkout() throws Exception {
        return read(postJson("/api/checkout", checkoutBody("jne:REG",
                List.of(Map.of("productId", kopi.getId(), "quantity", 1))))
                .andExpect(status().isCreated())).get("orderNumber").asText();
    }

    private String grossOf(String n) {
        return orders.findByOrderNumber(n).orElseThrow().getTotal().setScale(2).toPlainString();
    }

    private org.springframework.test.web.servlet.ResultActions notify(String orderId, String code, String gross,
                                                                      String txStatus, String signature) throws Exception {
        return postJson("/api/payments/midtrans/notification", Map.of(
                "order_id", orderId, "status_code", code, "gross_amount", gross,
                "transaction_status", txStatus, "transaction_id", "tx-1", "payment_type", "bank_transfer",
                "signature_key", signature));
    }

    private static String sign(String orderId, String code, String gross) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-512")
                .digest((orderId + code + gross + SERVER_KEY).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(d);
    }
}
