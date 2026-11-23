package com.tinytrail.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** One exception type carrying its HTTP status; the advice turns it into the JSON error body. */
@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
