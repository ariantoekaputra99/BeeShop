package com.bee.shop.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Pelanggaran aturan bisnis (stok habis, transisi status tidak valid, dsb). */
@Getter
public class BusinessException extends RuntimeException {
    private final HttpStatus status;

    public BusinessException(String message) {
        this(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
