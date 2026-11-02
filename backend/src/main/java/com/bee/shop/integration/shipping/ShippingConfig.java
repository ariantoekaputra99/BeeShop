package com.bee.shop.integration.shipping;

import com.bee.shop.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Configuration
public class ShippingConfig {

    @Bean
    ShippingProvider shippingProvider(AppProperties props, RestClient.Builder builder) {
        AppProperties.Shipping s = props.shipping();
        String provider = s.provider() == null ? "mock" : s.provider().trim().toLowerCase();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));

        ShippingProvider p = switch (provider) {
            case "mock" -> new MockShippingProvider(s.originCity());
            case "rajaongkir" -> new RajaOngkirShippingProvider(
                    builder.clone().requestFactory(factory).baseUrl(RajaOngkirShippingProvider.BASE_URL),
                    s.apiKey(), s.originId(), s.couriers());
            case "biteship" -> new BiteshipShippingProvider(
                    builder.clone().requestFactory(factory).baseUrl(BiteshipShippingProvider.BASE_URL),
                    s.apiKey(), s.originPostalCode(), s.couriers());
            default -> throw new IllegalStateException(
                    "SHIPPING_PROVIDER '" + s.provider() + "' tidak dikenal. Pilihan: mock, rajaongkir, biteship");
        };
        log.info("Shipping provider: {} (kurir: {})", p.name(), provider.equals("mock") ? "simulasi" : s.couriers());
        return p;
    }
}
