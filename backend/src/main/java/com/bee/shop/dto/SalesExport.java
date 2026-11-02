package com.bee.shop.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Penjualan online yang sudah dibayar, untuk ditarik BeePos-App agar stok kasir ikut berkurang. */
public record SalesExport(String orderNumber, String status, OffsetDateTime paidAt, BigDecimal total, List<Line> lines) {
    public record Line(String sku, int quantity, BigDecimal unitPrice) {}
}
