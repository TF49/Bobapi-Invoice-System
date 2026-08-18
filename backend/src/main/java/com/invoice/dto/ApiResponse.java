package com.invoice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

/**
 * 统一响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    
    private Integer code;
    private String message;
    private T data;
    private String traceId;
    
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data, MDC.get("traceId"));
    }
    
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data, MDC.get("traceId"));
    }
    
    public static <T> ApiResponse<T> error(String message) {
        return error(50000, message);
    }
    
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return new ApiResponse<>(code, message, null, MDC.get("traceId"));
    }
}
