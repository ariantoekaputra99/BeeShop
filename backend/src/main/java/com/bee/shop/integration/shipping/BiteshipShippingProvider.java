package com.bee.shop.integration.shipping;

import com.bee.shop.dto.ShippingOption;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Adapter Biteship Rates API ({@code POST https://api.biteship.com/v1/rates/couriers}) berbasis kode pos.
 * Header {@code authorization} berisi API key (biteship_test.* untuk testing, biteship_live.* untuk production).
 */
@Slf4j
public class BiteshipShippingProvider implements ShippingProvider {

    public static final String BASE_URL = "https://api.biteship.com";

    private final RestClient http;
    private final String originPostalCode;
    private final String couriers;

    public BiteshipShippingProvider(RestClient.Builder builder, String apiKey, String originPostalCode, List<String> couriers) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("SHIPPING_API_KEY wajib diisi untuk Biteship");
        if (originPostalCode == null || !originPostalCode.matches("\\d{5}")) {
            throw new IllegalStateException("SHIPPING_ORIGIN_POSTAL_CODE (5 digit) wajib diisi untuk Biteship");
        }
        this.http = builder.defaultHeader(HttpHeaders.AUTHORIZATION, apiKey).build();
        this.originPostalCode = originPostalCode;
        this.couriers = String.join(",", couriers);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Pricing(@JsonProperty("courier_name") String courierName,
                   @JsonProperty("courier_code") String courierCode,
                   @JsonProperty("courier_service_name") String serviceName,
                   @JsonProperty("courier_service_code") String serviceCode,
                   String duration,
                   @JsonProperty("shipment_duration_range") String durationRange,
                   @JsonProperty("shipment_duration_unit") String durationUnit,
                   BigDecimal price) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RatesResponse(Boolean success, String error, List<Pricing> pricing) {}

    @Override
    public String name() {
        return "biteship";
    }

    @Override
    public List<ShippingOption> quote(ShipmentRequest request) {
        long value = request.itemValue() == null ? 0 : request.itemValue().setScale(0, RoundingMode.HALF_UP).longValue();
        Map<String, Object> body = Map.of(
                "origin_postal_code", Integer.parseInt(originPostalCode),
                "destination_postal_code", Integer.parseInt(request.destinationPostalCode()),
                "couriers", couriers,
                "items", List.of(Map.of(
                        "name", "Paket BeeShop",
                        "value", value,
                        "weight", Math.max(1, request.weightGram()),
                        "quantity", 1)));

        RatesResponse res;
        try {
            res = http.post().uri("/v1/rates/couriers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(RatesResponse.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                log.error("Biteship menolak API key: {}", e.getResponseBodyAsString());
                throw new ShippingProviderException("Layanan cek ongkir sedang tidak tersedia");
            }
            // 400: rute tidak terlayani / kode pos tidak dikenal
            log.info("Biteship: tidak ada tarif ke {} ({})", request.destinationPostalCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (RestClientException e) {
            log.error("Biteship rates gagal", e);
            throw new ShippingProviderException("Layanan cek ongkir sedang tidak tersedia");
        }
        if (res == null || res.pricing() == null) return List.of();

        return res.pricing().stream()
                .filter(p -> p.price() != null && p.price().signum() > 0 && p.courierCode() != null && p.serviceCode() != null)
                .map(p -> new ShippingOption(
                        p.courierCode().toLowerCase() + ":" + p.serviceCode(),
                        p.courierName() == null ? p.courierCode().toUpperCase() : p.courierName(),
                        p.serviceName() == null ? p.serviceCode() : p.serviceName(),
                        etd(p),
                        p.price()))
                .sorted(Comparator.comparing(ShippingOption::fee))
                .toList();
    }

    private static String etd(Pricing p) {
        if (p.durationRange() != null && !p.durationRange().isBlank()) {
            String unit = "hours".equalsIgnoreCase(p.durationUnit()) ? "jam" : "hari";
            return p.durationRange().replace(" ", "") + " " + unit;
        }
        return p.duration() == null || p.duration().isBlank() ? "-"
                : p.duration().replace("days", "hari").replace("day", "hari").replace("hours", "jam");
    }
}
