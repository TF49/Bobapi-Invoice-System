package com.invoice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 供应商结算响应 DTO
 * 修复 #6：使用 OffsetDateTime 代替 LocalDateTime，序列化时携带时区信息（如 +08:00），
 *          避免前端 new Date() 因无时区信息而在不同浏览器/时区环境下解析错误。
 */
public record SupplierSettlementResponse(
        Long id,
        BigDecimal settlementAmount,
        String remark,
        String operatorName,
        OffsetDateTime createdAt
) {
}
