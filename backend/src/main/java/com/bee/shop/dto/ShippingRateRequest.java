package com.bee.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ShippingRateRequest(
        @NotBlank @Size(max = 100) String destinationCity,
        @NotBlank @Pattern(regexp = "\\d{5}", message = "kode pos 5 digit") String postalCode,
        @NotEmpty @Size(max = 50) List<@Valid CartItem> items
) {}
