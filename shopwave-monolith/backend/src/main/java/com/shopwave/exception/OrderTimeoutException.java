package com.shopwave.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
public class OrderTimeoutException extends RuntimeException {
    public OrderTimeoutException(String message) {
        super(message);
    }
}
