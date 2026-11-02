package com.bee.shop;

import com.bee.shop.entity.Product;
import com.bee.shop.repository.OrderRepository;
import com.bee.shop.repository.PaymentRepository;
import com.bee.shop.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public abstract class TestSupport {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected ProductRepository products;
    @Autowired protected OrderRepository orders;
    @Autowired protected PaymentRepository payments;

    protected Product beras;
    protected Product kopi;

    @BeforeEach
    void resetData() {
        payments.deleteAll();
        orders.deleteAll();
        products.deleteAll();
        beras = products.save(Product.builder().name("Beras 5kg").sku("BR-005").price(new BigDecimal("72000"))
                .stock(10).weightGram(5000).category("Sembako").active(true).build());
        kopi = products.save(Product.builder().name("Kopi 200g").sku("KP-200").price(new BigDecimal("26000"))
                .stock(3).weightGram(200).category("Minuman").active(true).build());
    }

    protected ResultActions postJson(String url, Object body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected Map<String, Object> checkoutBody(String shippingCode, Object items) {
        return Map.of(
                "customerName", "Budi Santoso",
                "customerEmail", "Budi@Example.com",
                "customerPhone", "081234567890",
                "address", "Jl. Melati No. 1",
                "city", "Jakarta",
                "postalCode", "12345",
                "shippingCode", shippingCode,
                "items", items);
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected String login(String username, String password) throws Exception {
        return read(postJson("/api/admin/auth/login", Map.of("username", username, "password", password))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()))
                .get("token").asText();
    }

    protected String ownerToken() throws Exception {
        return login("owner", "owner-password-123");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected int stockOf(Product p) {
        return products.findById(p.getId()).orElseThrow().getStock();
    }
}
