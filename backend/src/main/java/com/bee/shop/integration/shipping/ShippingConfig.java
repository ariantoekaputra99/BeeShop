package com.bee.shop.integration.shipping;

import com.bee.shop.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShippingConfig {

    @Bean
    ShippingProvider shippingProvider(AppProperties props) {
        String provider = props.shipping().provider();
        return switch (provider == null ? "mock" : provider.toLowerCase()) {
            case "mock" -> new MockShippingProvider();
            default -> throw new IllegalStateException(
                    "SHIPPING_PROVIDER '" + provider + "' belum diimplementasikan. Gunakan 'mock' atau tambahkan adapter ShippingProvider.");
        };
    }
}
