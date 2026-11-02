package com.bee.shop.integration.payment;

import com.bee.shop.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Configuration
public class PaymentConfig {

    @Bean
    PaymentGateway paymentGateway(AppProperties props, RestClient.Builder builder) {
        AppProperties.Payment p = props.payment();
        if (p.useMidtrans()) {
            log.info("Payment gateway: Midtrans ({})", p.sandbox() ? "sandbox" : "PRODUCTION");
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(5));
            factory.setReadTimeout(Duration.ofSeconds(15));
            return new MidtransSnapGateway(p.serverKey(), p.sandbox(), props.storefrontUrl(),
                    builder.requestFactory(factory));
        }
        log.warn("Payment gateway: MOCK. Jangan gunakan mode ini di production.");
        return new MockPaymentGateway(props.storefrontUrl());
    }
}
