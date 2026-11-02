package com.bee.shop.dto;

import java.util.List;

public record ShippingQuote(int totalWeightGram, List<ShippingOption> options) {}
