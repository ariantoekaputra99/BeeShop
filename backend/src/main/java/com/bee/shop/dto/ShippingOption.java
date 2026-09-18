package com.bee.shop.dto;

import java.math.BigDecimal;
public record ShippingOption(Long id,String courier,String service,String etd,BigDecimal fee){}
