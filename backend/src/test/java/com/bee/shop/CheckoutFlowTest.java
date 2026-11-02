package com.bee.shop;

import com.bee.shop.entity.Order;
import com.bee.shop.entity.OrderStatus;
import com.bee.shop.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutFlowTest extends TestSupport {

    @Autowired OrderService orderService;

    @Test
    void catalogSupportsSearchCategoryAndDetail() throws Exception {
        mvc.perform(get("/api/products").param("q", "kopi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("KP-200"));
        mvc.perform(get("/api/products").param("category", "Sembako"))
                .andExpect(jsonPath("$.content[0].sku").value("BR-005"));
        mvc.perform(get("/api/categories")).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/products/" + beras.getId())).andExpect(jsonPath("$.name").value("Beras 5kg"));
        mvc.perform(get("/api/products/999999")).andExpect(status().isNotFound());
    }

    @Test
    void shippingRatesUseProductWeight() throws Exception {
        JsonNode quote = read(postJson("/api/shipping/rates", Map.of(
                "destinationCity", "Bandung", "postalCode", "40111",
                "items", List.of(Map.of("productId", beras.getId(), "quantity", 2))))
                .andExpect(status().isOk()));
        assertThat(quote.get("totalWeightGram").asInt()).isEqualTo(10000);
        // jne:REG antar kota = 18.000/kg x 10kg
        assertThat(quote.get("options").get(0).get("fee").asInt()).isEqualTo(180000);
    }

    @Test
    void checkoutCreatesOrderReservesStockAndMockPaymentMarksPaid() throws Exception {
        JsonNode res = read(postJson("/api/checkout", checkoutBody("jne:REG", List.of(
                Map.of("productId", beras.getId(), "quantity", 1),
                Map.of("productId", beras.getId(), "quantity", 1),
                Map.of("productId", kopi.getId(), "quantity", 1))))
                .andExpect(status().isCreated()));

        String orderNumber = res.get("orderNumber").asText();
        assertThat(orderNumber).matches("BEE-\\d{8}-[A-Z0-9]{6}");
        // subtotal 2x72000 + 26000 = 170000; berat 10.2kg -> 11kg x 10.000 (dalam kota)
        assertThat(res.get("total").asInt()).isEqualTo(170000 + 110000);
        assertThat(res.get("redirectUrl").asText()).endsWith("/#/payment/" + orderNumber);
        assertThat(stockOf(beras)).isEqualTo(8);
        assertThat(stockOf(kopi)).isEqualTo(2);

        mvc.perform(post("/api/payments/mock/" + orderNumber).param("result", "success")).andExpect(status().isOk());
        // Notifikasi ganda tidak boleh mengubah apa pun
        mvc.perform(post("/api/payments/mock/" + orderNumber).param("result", "success")).andExpect(status().isOk());

        postJson("/api/orders/" + orderNumber + "/lookup", Map.of("email", "budi@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.history.length()").value(2));
    }

    @Test
    void lookupWithWrongEmailIsNotFound() throws Exception {
        String orderNumber = read(postJson("/api/checkout", checkoutBody("jne:REG",
                List.of(Map.of("productId", kopi.getId(), "quantity", 1))))).get("orderNumber").asText();
        postJson("/api/orders/" + orderNumber + "/lookup", Map.of("email", "orang.lain@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void insufficientStockIsRejectedWithoutSideEffects() throws Exception {
        postJson("/api/checkout", checkoutBody("jne:REG", List.of(
                Map.of("productId", beras.getId(), "quantity", 1),
                Map.of("productId", kopi.getId(), "quantity", 4))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Stok tidak cukup untuk Kopi 200g (tersisa 3)"));
        assertThat(stockOf(beras)).isEqualTo(10);
        assertThat(orders.count()).isZero();
    }

    @Test
    void unknownShippingCodeAndInvalidInputAreRejected() throws Exception {
        postJson("/api/checkout", checkoutBody("kurir:palsu", List.of(Map.of("productId", kopi.getId(), "quantity", 1))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(stockOf(kopi)).isEqualTo(3);

        postJson("/api/checkout", Map.of("customerName", "", "items", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.customerEmail").exists());
    }

    @Test
    void failedPaymentAndCustomerCancelRestoreStock() throws Exception {
        String a = read(postJson("/api/checkout", checkoutBody("jne:REG",
                List.of(Map.of("productId", kopi.getId(), "quantity", 2))))).get("orderNumber").asText();
        assertThat(stockOf(kopi)).isEqualTo(1);
        mvc.perform(post("/api/payments/mock/" + a).param("result", "failed")).andExpect(status().isOk());
        assertThat(stockOf(kopi)).isEqualTo(3);
        assertThat(orders.findByOrderNumber(a).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);

        String b = read(postJson("/api/checkout", checkoutBody("jne:REG",
                List.of(Map.of("productId", kopi.getId(), "quantity", 3))))).get("orderNumber").asText();
        postJson("/api/orders/" + b + "/cancel", Map.of("email", "budi@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(stockOf(kopi)).isEqualTo(3);
    }

    @Test
    void overdueUnpaidOrdersExpireAndReleaseStock() throws Exception {
        String n = read(postJson("/api/checkout", checkoutBody("jne:REG",
                List.of(Map.of("productId", beras.getId(), "quantity", 4))))).get("orderNumber").asText();
        Order o = orders.findByOrderNumber(n).orElseThrow();
        o.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        orders.save(o);

        assertThat(orderService.expireOverdueOrders()).isEqualTo(1);
        assertThat(orders.findByOrderNumber(n).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(beras)).isEqualTo(10);
        assertThat(orderService.expireOverdueOrders()).isZero();
    }
}
