package com.bee.shop.integration.payment;

import com.bee.shop.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class PaymentGatewayException extends BusinessException {
    public PaymentGatewayException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }
}
