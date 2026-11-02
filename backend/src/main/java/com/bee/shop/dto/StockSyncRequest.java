package com.bee.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Payload dari BeePos-App: stok terkini per SKU (stok absolut, bukan delta). */
public record StockSyncRequest(@NotEmpty @Size(max = 1000) List<@Valid Item> items) {
    public record Item(@NotBlank String sku, @Min(0) int stock) {}
}
