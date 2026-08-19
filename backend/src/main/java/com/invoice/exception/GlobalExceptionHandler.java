package com.invoice.exception;

import com.invoice.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BatchValidationException.class)
    public ResponseEntity<ApiResponse<java.util.List<com.invoice.dto.BatchInvoiceRowError>>>
            handleBatchValidation(BatchValidationException exception) {
        return ResponseEntity.unprocessableEntity().body(
                ApiResponse.error(exception.getCode(), exception.getMessage(), exception.getErrors()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        HttpHeaders headers = new HttpHeaders();
        if (exception.getRetryAfterSeconds() != null) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()));
        }
        return new ResponseEntity<>(
                ApiResponse.error(exception.getCode(), exception.getMessage()),
                headers,
                exception.getStatus()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "请求参数不正确" : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(ApiResponse.error(40001, message));
    }

    @ExceptionHandler({ConstraintViolationException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ApiResponse<Void>> handleConstraint(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(40001, exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(40001, "请求体格式不正确"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(41300, "文件大小不能超过 10MB"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(40900, "数据冲突，请刷新后重试"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled request error, traceId={}", MDC.get("traceId"), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(50000, "系统繁忙，请稍后重试"));
    }
}
