package com.bee.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CheckoutRequest(@NotBlank String customerName,@Email @NotBlank String customerEmail,@NotBlank String address,@NotNull Long shippingOptionId,@NotEmpty List<@Valid Item> items){ public record Item(@NotNull Long productId,@Min(1) int quantity){} }
