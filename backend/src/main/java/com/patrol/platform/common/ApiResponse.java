package com.patrol.platform.common;

/**
 * 统一响应信封, 与接口文档 1.2 节一致: { "code": 0, "msg": "ok", "data": {} }
 */
public record ApiResponse<T>(int code, String msg, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), "ok", null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String msg) {
        return new ApiResponse<>(errorCode.getCode(), msg, null);
    }
}
