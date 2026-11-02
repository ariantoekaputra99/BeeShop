package com.bee.shop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAndIntegrationTest extends TestSupport {

    static final String ADMIN = "X-Admin-Key";

    @Test
    void adminApiRequiresKey() throws Exception {
        mvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/orders").header(ADMIN, "salah")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/orders").header(ADMIN, "test-admin-key")).andExpect(status().isOk());
    }

    @Test
    void adminManagesProducts() throws Exception {
        String body = json.writeValueAsString(Map.of("name", "Gula 1kg", "sku", "GP-001", "price", 17500,
                "stock", 20, "weightGram", 1000, "category", "Sembako"));
        String id = json.readTree(mvc.perform(post("/api/admin/products").header(ADMIN, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/admin/products").header(ADMIN, "test-admin-key")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());

        mvc.perform(delete("/api/admin/products/" + id).header(ADMIN, "test-admin-key")).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/products/" + id).header(ADMIN, "test-admin-key"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void adminDrivesOrderLifecycle() throws Exception {
        String n = read(postJson("/api/checkout", checkoutBody("sicepat:BEST",
                List.of(Map.of("productId", kopi.getId(), "quantity", 1))))).get("orderNumber").asText();

        // Belum dibayar -> tidak bisa diproses, dan admin tidak boleh set PAID
        patchStatus(n, Map.of("status", "PROCESSING")).andExpect(status().isUnprocessableEntity());
        patchStatus(n, Map.of("status", "PAID")).andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/api/payments/mock/" + n)).andExpect(status().isOk());
        patchStatus(n, Map.of("status", "PROCESSING")).andExpect(status().isOk());
        patchStatus(n, Map.of("status", "SHIPPED")).andExpect(status().isUnprocessableEntity());
        patchStatus(n, Map.of("status", "SHIPPED", "trackingNumber", "SCP123456"))
                .andExpect(jsonPath("$.trackingNumber").value("SCP123456"));
        patchStatus(n, Map.of("status", "DELIVERED")).andExpect(jsonPath("$.status").value("DELIVERED"));

        mvc.perform(get("/api/admin/orders").param("status", "DELIVERED").header(ADMIN, "test-admin-key"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/admin/dashboard").header(ADMIN, "test-admin-key"))
                .andExpect(jsonPath("$.ordersByStatus.DELIVERED").value(1))
                .andExpect(jsonPath("$.lowStockProducts").value(1));
    }

    @Test
    void refundRestoresStock() throws Exception {
        String n = read(postJson("/api/checkout", checkoutBody("jne:REG",
                List.of(Map.of("productId", kopi.getId(), "quantity", 2))))).get("orderNumber").asText();
        mvc.perform(post("/api/payments/mock/" + n)).andExpect(status().isOk());
        patchStatus(n, Map.of("status", "REFUNDED", "note", "Barang rusak")).andExpect(status().isOk());
        assertThat(stockOf(kopi)).isEqualTo(3);
    }

    @Test
    void beePosSyncsStockAndPullsSales() throws Exception {
        String payload = json.writeValueAsString(Map.of("items", List.of(
                Map.of("sku", "BR-005", "stock", 50), Map.of("sku", "TIDAK-ADA", "stock", 1))));
        mvc.perform(put("/api/integrations/beepos/stock").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        // Admin key tidak berlaku untuk integrasi
        mvc.perform(put("/api/integrations/beepos/stock").header(ADMIN, "test-admin-key")
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/integrations/beepos/stock").header("X-Integration-Key", "test-integration-key")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.unknownSkus[0]").value("TIDAK-ADA"));
        assertThat(stockOf(beras)).isEqualTo(50);

        String n = read(postJson("/api/checkout", checkoutBody("jne:REG",
                List.of(Map.of("productId", beras.getId(), "quantity", 3))))).get("orderNumber").asText();
        mvc.perform(post("/api/payments/mock/" + n)).andExpect(status().isOk());
        mvc.perform(get("/api/integrations/beepos/sales").param("since", "2000-01-01T00:00:00Z")
                        .header("X-Integration-Key", "test-integration-key"))
                .andExpect(jsonPath("$[0].orderNumber").value(n))
                .andExpect(jsonPath("$[0].lines[0].sku").value("BR-005"))
                .andExpect(jsonPath("$[0].lines[0].quantity").value(3));
    }

    private org.springframework.test.web.servlet.ResultActions patchStatus(String n, Map<String, ?> body) throws Exception {
        return mvc.perform(patch("/api/admin/orders/" + n + "/status").header(ADMIN, "test-admin-key")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
}
