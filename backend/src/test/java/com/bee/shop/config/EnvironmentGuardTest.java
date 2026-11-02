package com.bee.shop.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentGuardTest {

    static final String KEY = "a".repeat(64);

    static AppProperties props(String paymentProvider, String serverKey, boolean sandbox, String shipping,
                               String cors, boolean seed) {
        return new AppProperties(
                List.of(cors), "https://shop.example.com", KEY,
                new AppProperties.Admin(null, null, 12, 5, 15, 20),
                seed,
                new AppProperties.Order(60, 60000),
                new AppProperties.Payment(paymentProvider, serverKey, "client-key", sandbox),
                new AppProperties.Shipping(shipping, "ship-key", "Jakarta", "12440", null, List.of("jne"), 10));
    }

    @Test
    void validProdPasses() {
        var p = props("midtrans", "Mid-server-abc", false, "biteship", "https://shop.example.com", false);
        assertThat(EnvironmentGuard.validate(p, "s3cure-db-password!", true)).isEmpty();
    }

    @Test
    void validUatPasses() {
        var p = props("midtrans", "SB-Mid-server-abc", true, "rajaongkir", "https://uat.shop.example.com", false);
        assertThat(EnvironmentGuard.validate(p, "s3cure-db-password!", false)).isEmpty();
    }

    @Test
    void prodRejectsLocalStyleConfig() {
        var p = props("mock", "SB-Mid-server-abc", true, "mock", "http://localhost:5173", true);
        List<String> errors = EnvironmentGuard.validate(p, "bee123", true);
        assertThat(String.join("\n", errors))
                .contains("PAYMENT_PROVIDER", "key sandbox", "sandbox Midtrans", "SHIPPING_PROVIDER",
                        "SEED_DEMO_DATA", "SPRING_DATASOURCE_PASSWORD", "CORS_ORIGINS");
    }

    @Test
    void uatRejectsProductionMidtransKey() {
        var p = props("midtrans", "Mid-server-live", true, "biteship", "https://uat.shop.example.com", false);
        assertThat(EnvironmentGuard.validate(p, "s3cure-db-password!", false))
                .anyMatch(e -> e.contains("sandbox key"));
    }
}
