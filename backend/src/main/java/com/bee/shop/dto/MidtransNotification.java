package com.bee.shop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Payload HTTP notification Midtrans (subset field yang dipakai). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MidtransNotification(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("status_code") String statusCode,
        @JsonProperty("gross_amount") String grossAmount,
        @JsonProperty("signature_key") String signatureKey,
        @JsonProperty("transaction_id") String transactionId,
        @JsonProperty("transaction_status") String transactionStatus,
        @JsonProperty("fraud_status") String fraudStatus,
        @JsonProperty("payment_type") String paymentType
) {}
