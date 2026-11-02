package com.bee.shop.integration.shipping;

import com.bee.shop.dto.ShippingOption;

import java.util.List;

/**
 * Abstraksi penyedia ongkir. Implementasi tersedia: mock, rajaongkir (Komerce), biteship.
 * Pilih lewat env {@code SHIPPING_PROVIDER}, lihat {@link ShippingConfig}.
 *
 * <p>{@link ShippingOption#code()} wajib stabil untuk kombinasi kurir+layanan yang sama,
 * karena checkout mencocokkan ulang kode ini dengan hasil quote terbaru.
 */
public interface ShippingProvider {
    String name();

    List<ShippingOption> quote(ShipmentRequest request);
}
