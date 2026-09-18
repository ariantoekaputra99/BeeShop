package com.bee.shop.dto;

import java.math.BigDecimal;
public record PaymentSession(String orderNumber,String provider,String transactionId,String redirectUrl,BigDecimal amount){}
