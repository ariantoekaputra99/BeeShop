package com.bee.shop.dto;

import jakarta.validation.constraints.NotBlank;
public record ShippingRateRequest(@NotBlank String origin,@NotBlank String destination,@NotBlank String courier,int weightGram){}
