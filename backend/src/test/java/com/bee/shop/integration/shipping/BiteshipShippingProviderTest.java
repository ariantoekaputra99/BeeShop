package com.bee.shop.integration.shipping;

import com.bee.shop.dto.ShippingOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BiteshipShippingProviderTest {

    MockRestServiceServer server;
    BiteshipShippingProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BiteshipShippingProvider.BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new BiteshipShippingProvider(builder, "biteship_test.abc", "12440", List.of("jne", "sicepat", "grab"));
    }

    @Test
    void quotesByPostalCode() {
        server.expect(requestTo(BiteshipShippingProvider.BASE_URL + "/v1/rates/couriers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "biteship_test.abc"))
                .andExpect(content().json("""
                        {"origin_postal_code":12440,"destination_postal_code":40115,"couriers":"jne,sicepat,grab",
                         "items":[{"name":"Paket BeeShop","value":170000,"weight":1200,"quantity":1}]}
                        """, true))
                .andRespond(withSuccess("""
                        {"success":true,"object":"courier_pricing","pricing":[
                          {"courier_name":"JNE","courier_code":"jne","courier_service_name":"Reguler","courier_service_code":"reg",
                           "duration":"2 - 3 days","shipment_duration_range":"2 - 3","shipment_duration_unit":"days","price":22000},
                          {"courier_name":"Grab","courier_code":"grab","courier_service_name":"Instant","courier_service_code":"instant",
                           "duration":"1 - 3 hours","shipment_duration_range":"1 - 3","shipment_duration_unit":"hours","price":45000},
                          {"courier_name":"SiCepat","courier_code":"sicepat","courier_service_name":"Reguler","courier_service_code":"reg",
                           "duration":"1 - 2 days","price":19000}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<ShippingOption> options = provider.quote(new ShipmentRequest("Bandung", "40115", 1200, new BigDecimal("170000.00")));

        assertThat(options).extracting(ShippingOption::code).containsExactly("sicepat:reg", "jne:reg", "grab:instant");
        assertThat(options.get(1).etd()).isEqualTo("2-3 hari");
        assertThat(options.get(2).etd()).isEqualTo("1-3 jam");
        assertThat(options.get(0).etd()).isEqualTo("1 - 2 hari");
        server.verify();
    }

    @Test
    void unservedRouteReturnsEmptyButAuthErrorFails() {
        server.expect(requestTo(org.hamcrest.Matchers.endsWith("/v1/rates/couriers")))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"success\":false,\"error\":\"No courier available for requested route\",\"code\":40001010}"));
        assertThat(provider.quote(new ShipmentRequest("X", "99999", 1000, null))).isEmpty();

        server.reset();
        server.expect(requestTo(org.hamcrest.Matchers.endsWith("/v1/rates/couriers")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"success\":false}"));
        assertThatThrownBy(() -> provider.quote(new ShipmentRequest("X", "40115", 1000, null)))
                .isInstanceOf(ShippingProviderException.class);
    }

    @Test
    void requiresKeyAndOriginPostalCode() {
        assertThatThrownBy(() -> new BiteshipShippingProvider(RestClient.builder(), "", "12440", List.of("jne")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BiteshipShippingProvider(RestClient.builder(), "k", "1244", List.of("jne")))
                .isInstanceOf(IllegalStateException.class);
    }
}
