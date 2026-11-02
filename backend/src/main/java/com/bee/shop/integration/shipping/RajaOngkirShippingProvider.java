package com.bee.shop.integration.shipping;

import com.bee.shop.dto.ShippingOption;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter RajaOngkir versi Komerce (https://rajaongkir.komerce.id/api/v1).
 *
 * <ol>
 *   <li>ID tujuan dicari dari kode pos lewat {@code GET destination/domestic-destination?search=}
 *       lalu di-cache di memori (kuota API terbatas per hari).</li>
 *   <li>Ongkir dihitung lewat {@code POST calculate/domestic-cost} (form-urlencoded, header {@code key}).</li>
 * </ol>
 * ID asal toko diisi lewat {@code SHIPPING_ORIGIN_ID} (cari sekali di endpoint destination yang sama).
 */
@Slf4j
public class RajaOngkirShippingProvider implements ShippingProvider {

    public static final String BASE_URL = "https://rajaongkir.komerce.id/api/v1";

    private final RestClient http;
    private final String originId;
    private final String couriers;
    private final Map<String, String> destinationIdByPostalCode = new ConcurrentHashMap<>();

    public RajaOngkirShippingProvider(RestClient.Builder builder, String apiKey, String originId, List<String> couriers) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("SHIPPING_API_KEY wajib diisi untuk RajaOngkir");
        if (originId == null || originId.isBlank()) throw new IllegalStateException("SHIPPING_ORIGIN_ID wajib diisi untuk RajaOngkir");
        this.http = builder.defaultHeader("key", apiKey).defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE).build();
        this.originId = originId.trim();
        this.couriers = String.join(":", couriers);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Meta(String message, Integer code, String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Destination(Long id, String label, @JsonProperty("zip_code") String zipCode) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DestinationResponse(Meta meta, List<Destination> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Cost(String name, String code, String service, String description, BigDecimal cost, String etd) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CostResponse(Meta meta, List<Cost> data) {}

    @Override
    public String name() {
        return "rajaongkir";
    }

    @Override
    public List<ShippingOption> quote(ShipmentRequest request) {
        String destinationId = resolveDestination(request.destinationPostalCode());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("origin", originId);
        form.add("destination", destinationId);
        form.add("weight", String.valueOf(Math.max(1, request.weightGram())));
        form.add("courier", couriers);
        form.add("price", "lowest");

        CostResponse res;
        try {
            res = http.post().uri("/calculate/domestic-cost")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(CostResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            // RajaOngkir mengembalikan 404/400 bila tidak ada layanan untuk rute ini
            return List.of();
        } catch (HttpClientErrorException.BadRequest e) {
            log.info("RajaOngkir: tidak ada layanan ke {} ({})", request.destinationPostalCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (RestClientException e) {
            log.error("RajaOngkir calculate gagal", e);
            throw new ShippingProviderException("Layanan cek ongkir sedang tidak tersedia");
        }
        if (res == null || res.data() == null) return List.of();

        return res.data().stream()
                .filter(c -> c.cost() != null && c.cost().signum() > 0 && c.code() != null && c.service() != null)
                .map(c -> new ShippingOption(
                        c.code().toLowerCase() + ":" + c.service(),
                        displayName(c),
                        c.service(),
                        formatEtd(c.etd()),
                        c.cost()))
                .sorted(Comparator.comparing(ShippingOption::fee))
                .toList();
    }

    String resolveDestination(String postalCode) {
        String cached = destinationIdByPostalCode.get(postalCode);
        if (cached != null) return cached;

        DestinationResponse res;
        try {
            res = http.get()
                    .uri(u -> u.path("/destination/domestic-destination")
                            .queryParam("search", postalCode).queryParam("limit", 20).queryParam("offset", 0).build())
                    .retrieve()
                    .body(DestinationResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            res = null;
        } catch (RestClientException e) {
            log.error("RajaOngkir destination search gagal", e);
            throw new ShippingProviderException("Layanan cek ongkir sedang tidak tersedia");
        }
        List<Destination> found = res == null || res.data() == null ? List.of() : res.data();
        String id = found.stream()
                .filter(d -> postalCode.equals(d.zipCode()))
                .map(d -> String.valueOf(d.id()))
                .findFirst()
                .or(() -> found.stream().map(Destination::id).filter(Objects::nonNull).map(String::valueOf).findFirst())
                .orElseThrow(() -> new ShippingProviderException("Kode pos " + postalCode + " tidak dikenali layanan ongkir"));
        destinationIdByPostalCode.put(postalCode, id);
        return id;
    }

    private static String displayName(Cost c) {
        String n = c.name() == null || c.name().isBlank() ? c.code().toUpperCase() : c.name();
        // Nama dari API kadang panjang, contoh "Jalur Nugraha Ekakurir (JNE)"
        int open = n.lastIndexOf('(');
        if (open > 0 && n.endsWith(")")) return n.substring(open + 1, n.length() - 1);
        return n;
    }

    static String formatEtd(String etd) {
        if (etd == null || etd.isBlank()) return "-";
        String e = etd.trim().toLowerCase().replace("hari", "").replace("day", "").replace("s", "").trim();
        return e.isEmpty() ? "-" : e + " hari";
    }
}
