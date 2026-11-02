package com.bee.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record CheckoutRequest(
        @NotBlank @Size(max = 120) String customerName,
        @NotBlank @Email @Size(max = 160) String customerEmail,
        @NotBlank @Pattern(regexp = "^(\\+62|62|0)8\\d{7,12}$", message = "nomor HP Indonesia tidak valid") String customerPhone,
        @NotBlank @Size(max = 1000) String address,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Pattern(regexp = "\\d{5}", message = "kode pos 5 digit") String postalCode,
        @Size(max = 500) String notes,
        @NotBlank String shippingCode,
        @NotEmpty @Size(max = 50) List<@Valid CartItem> items
) {}
