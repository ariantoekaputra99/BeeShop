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
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RajaOngkirShippingProviderTest {

    MockRestServiceServer server;
    RajaOngkirShippingProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(RajaOngkirShippingProvider.BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new RajaOngkirShippingProvider(builder, "secret-key", "31555", List.of("jne", "sicepat"));
    }

    @Test
    void resolvesDestinationByPostalCodeThenCalculatesCost() {
        server.expect(once(), requestTo(RajaOngkirShippingProvider.BASE_URL + "/destination/domestic-destination?search=40115&limit=20&offset=0"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("key", "secret-key"))
                .andRespond(withSuccess("""
                        {"meta":{"message":"Success Get Domestic Destinations","code":200,"status":"success"},
                         "data":[{"id":99,"label":"Lain, Bandung 40111","zip_code":"40111"},
                                 {"id":17473,"label":"Citarum, Bandung Wetan, Kota Bandung, Jawa Barat, 40115","zip_code":"40115"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(RajaOngkirShippingProvider.BASE_URL + "/calculate/domestic-cost"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("key", "secret-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "origin", "31555", "destination", "17473", "weight", "2500", "courier", "jne:sicepat", "price", "lowest")))
                .andRespond(withSuccess("""
                        {"meta":{"message":"Success Calculate Domestic Shipping cost","code":200,"status":"success"},
                         "data":[{"name":"Jalur Nugraha Ekakurir (JNE)","code":"jne","service":"REG","description":"Layanan Reguler","cost":30000,"etd":"2-3 day"},
                                 {"name":"SiCepat Express","code":"sicepat","service":"GOKIL","description":"Cargo","cost":0,"etd":""},
                                 {"name":"SiCepat Express","code":"sicepat","service":"REG","description":"Reguler","cost":24000,"etd":"1-2"}]}
                        """, MediaType.APPLICATION_JSON));

        List<ShippingOption> options = provider.quote(new ShipmentRequest("Bandung", "40115", 2500, BigDecimal.TEN));

        assertThat(options).extracting(ShippingOption::code).containsExactly("sicepat:REG", "jne:REG");
        assertThat(options.get(1).courier()).isEqualTo("JNE");
        assertThat(options.get(1).etd()).isEqualTo("2-3 hari");
        assertThat(options.get(0).fee()).isEqualByComparingTo("24000");
        server.verify();
    }

    @Test
    void destinationIdIsCachedPerPostalCode() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/destination/domestic-destination")))
                .andRespond(withSuccess("{\"data\":[{\"id\":5,\"zip_code\":\"10110\"}]}", MediaType.APPLICATION_JSON));
        assertThat(provider.resolveDestination("10110")).isEqualTo("5");
        assertThat(provider.resolveDestination("10110")).isEqualTo("5");
        server.verify();
    }

    @Test
    void unknownPostalCodeAndNoRouteAreHandled() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("search=99999")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"meta\":{\"code\":404,\"status\":\"error\"},\"data\":null}"));
        assertThatThrownBy(() -> provider.quote(new ShipmentRequest("X", "99999", 1000, null)))
                .isInstanceOf(ShippingProviderException.class).hasMessageContaining("99999");

        server.reset();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("search=10110")))
                .andRespond(withSuccess("{\"data\":[{\"id\":5,\"zip_code\":\"10110\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.endsWith("/calculate/domestic-cost")))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"meta\":{\"message\":\"Calculate Domestic Shipping Cost not found\",\"code\":400}}"));
        assertThat(provider.quote(new ShipmentRequest("Jakarta", "10110", 1000, null))).isEmpty();
    }

    @Test
    void serverErrorBecomesProviderException() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("search=")))
                .andRespond(withSuccess("{\"data\":[{\"id\":5,\"zip_code\":\"10110\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.endsWith("/calculate/domestic-cost"))).andRespond(withServerError());
        assertThatThrownBy(() -> provider.quote(new ShipmentRequest("Jakarta", "10110", 1000, null)))
                .isInstanceOf(ShippingProviderException.class);
    }

    @Test
    void requiresKeyAndOrigin() {
        assertThatThrownBy(() -> new RajaOngkirShippingProvider(RestClient.builder(), "", "1", List.of("jne")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RajaOngkirShippingProvider(RestClient.builder(), "k", "", List.of("jne")))
                .isInstanceOf(IllegalStateException.class);
    }
}
