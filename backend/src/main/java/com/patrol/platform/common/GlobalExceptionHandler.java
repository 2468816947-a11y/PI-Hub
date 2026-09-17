package com.patrol.platform.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理: 保证任何异常都以统一信封返回, 不暴露堆栈。
 * <p>
 * 错误码映射(与接口文档 §1.3 一致):
 * <ul>
 *   <li>BusinessException → 自定义错误码</li>
 *   <li>MethodArgumentNotValid → 422</li>
 *   <li>MissingServletRequestParameter → 422</li>
 *   <li>HttpMessageNotReadable → 422</li>
 *   <li>MethodArgumentTypeMismatch → 422</li>
 *   <li>MaxUploadSizeExceeded → 422</li>
 *   <li>其他 → 500</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity.status(e.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    /** @Valid @RequestBody 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, "参数校验失败: " + detail));
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED,
                        "参数校验失败: 缺少必填参数 " + e.getParameterName()));
    }

    /** 请求体无法解析(JSON 格式错误) */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, "参数校验失败: 请求体格式非法"));
    }

    /** 请求参数类型不匹配(如枚举非法) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED,
                        "参数校验失败: 参数 " + e.getName() + " 值非法"));
    }

    /** 文件超过上传大小限制 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, "参数校验失败: 文件超过大小上限"));
    }

    /** 兜底异常: 记录完整堆栈, 对外只返回通用信息 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "服务器内部错误"));
    }
}
