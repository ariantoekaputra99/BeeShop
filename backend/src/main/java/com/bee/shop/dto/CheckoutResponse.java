package com.bee.shop.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CheckoutResponse(String orderNumber, String status, BigDecimal total, String paymentProvider,
                               String redirectUrl, String snapToken, OffsetDateTime expiresAt) {}
