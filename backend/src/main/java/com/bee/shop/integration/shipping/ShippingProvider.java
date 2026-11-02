package com.bee.shop.integration.shipping;

import com.bee.shop.dto.ShippingOption;

import java.util.List;

/**
 * Abstraksi penyedia ongkir. Implementasi production (RajaOngkir / Biteship) cukup
 * mengimplementasikan interface ini dan didaftarkan di {@link ShippingConfig}.
 */
public interface ShippingProvider {
    String name();

    List<ShippingOption> quote(String originCity, String destinationCity, String postalCode, int weightGram);
}
