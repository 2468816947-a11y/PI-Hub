package com.patrol.platform.common;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码, 与接口文档 1.3 节错误码表一致
 */
public enum ErrorCode {

    SUCCESS(0, HttpStatus.OK),
    UNAUTHORIZED(401, HttpStatus.UNAUTHORIZED),
    FORBIDDEN(403, HttpStatus.FORBIDDEN),
    NOT_FOUND(404, HttpStatus.NOT_FOUND),
    CONFLICT(409, HttpStatus.CONFLICT),
    VALIDATION_FAILED(422, HttpStatus.UNPROCESSABLE_ENTITY),
    TOO_MANY_REQUESTS(429, HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(500, HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final HttpStatus httpStatus;

    ErrorCode(int code, HttpStatus httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
