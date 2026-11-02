package com.bee.shop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("http://localhost:5173") List<String> corsOrigins,
        @DefaultValue("http://localhost:5173") String storefrontUrl,
        String adminApiKey,
        String integrationApiKey,
        @DefaultValue("false") boolean seedDemoData,
        @DefaultValue Order order,
        @DefaultValue Payment payment,
        @DefaultValue Shipping shipping
) {
    public record Order(
            @DefaultValue("60") int paymentTimeoutMinutes,
            @DefaultValue("60000") long expiryCheckIntervalMs
    ) {}

    public record Payment(
            String provider,
            String serverKey,
            String clientKey,
            @DefaultValue("true") boolean sandbox
    ) {
        public boolean useMidtrans() {
            if ("mock".equalsIgnoreCase(provider)) return false;
            return "midtrans".equalsIgnoreCase(provider) || (serverKey != null && !serverKey.isBlank());
        }
    }

    public record Shipping(
            @DefaultValue("mock") String provider,
            String apiKey,
            @DefaultValue("Jakarta") String originCity
    ) {}
}
