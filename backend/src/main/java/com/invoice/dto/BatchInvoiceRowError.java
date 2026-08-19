package com.invoice.dto;

/**
 * 批量申请中的单个字段错误。
 */
public record BatchInvoiceRowError(
        Integer rowNumber,
        String field,
        Integer code,
        String message
) {
}
