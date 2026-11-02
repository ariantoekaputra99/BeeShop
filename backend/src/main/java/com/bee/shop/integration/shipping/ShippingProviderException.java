package com.bee.shop.integration.shipping;

import com.bee.shop.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ShippingProviderException extends BusinessException {
    public ShippingProviderException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }
}
