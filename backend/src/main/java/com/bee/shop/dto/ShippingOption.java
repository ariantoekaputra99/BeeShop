package com.bee.shop.dto;

import java.math.BigDecimal;

/** {@code code} unik per kurir+layanan, contoh {@code jne:REG}. Dipakai lagi saat checkout. */
public record ShippingOption(String code, String courier, String service, String etd, BigDecimal fee) {}
