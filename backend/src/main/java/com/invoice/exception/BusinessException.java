package com.invoice.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final int code;
    private final Long retryAfterSeconds;

    public BusinessException(HttpStatus status, int code, String message) {
        this(status, code, message, null);
    }

    public BusinessException(HttpStatus status, int code, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public int getCode() {
        return code;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
