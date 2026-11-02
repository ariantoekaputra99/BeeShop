package com.bee.shop.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "hanya huruf, angka, titik, minus, underscore") String sku,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 15, fraction = 0, message = "harga dalam rupiah bulat") BigDecimal price,
        @NotNull @Min(0) Integer stock,
        @NotNull @Min(1) @Max(100_000) Integer weightGram,
        @Size(max = 100) String category,
        @Size(max = 1024) String imageUrl,
        @Size(max = 4000) String description,
        Boolean active
) {}
