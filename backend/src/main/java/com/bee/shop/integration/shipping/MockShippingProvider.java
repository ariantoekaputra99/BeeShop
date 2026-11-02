package com.bee.shop.integration.shipping;

import com.bee.shop.dto.ShippingOption;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tarif simulasi untuk development: deterministik, dihitung per kg (dibulatkan ke atas),
 * lebih mahal untuk pengiriman antar kota.
 */
public class MockShippingProvider implements ShippingProvider {

    private record Rate(String code, String courier, String service, String etdSameCity, String etdOther,
                        long perKgSameCity, long perKgOther) {}

    private static final List<Rate> RATES = List.of(
            new Rate("jne:REG", "JNE", "REG", "1-2 hari", "2-3 hari", 10_000, 18_000),
            new Rate("sicepat:BEST", "SiCepat", "BEST", "1 hari", "1-2 hari", 14_000, 25_000),
            new Rate("anteraja:REG", "AnterAja", "REG", "1-3 hari", "2-4 hari", 9_000, 16_000)
    );

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public List<ShippingOption> quote(String originCity, String destinationCity, String postalCode, int weightGram) {
        boolean sameCity = originCity.trim().equalsIgnoreCase(destinationCity.trim());
        long kg = Math.max(1, (weightGram + 999) / 1000);
        return RATES.stream()
                .map(r -> new ShippingOption(r.code(), r.courier(), r.service(),
                        sameCity ? r.etdSameCity() : r.etdOther(),
                        BigDecimal.valueOf((sameCity ? r.perKgSameCity() : r.perKgOther()) * kg)))
                .toList();
    }
}
