package com.bee.shop.integration.shipping;

import java.math.BigDecimal;

/**
 * Data pengiriman yang dikirim ke provider ongkir.
 *
 * @param itemValue nilai barang (subtotal), dipakai sebagian provider untuk asuransi/estimasi
 */
public record ShipmentRequest(String destinationCity, String destinationPostalCode, int weightGram, BigDecimal itemValue) {}
