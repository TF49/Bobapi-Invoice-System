package com.invoice.dto;

import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 供应商结算请求 DTO
 */
@Data
public class SupplierSettlementRequest {

    @NotNull(message = "结算金额不能为空")
    @DecimalMin(value = "0.01", message = "结算金额必须大于等于 0.01")
    @Digits(integer = 10, fraction = 2, message = "结算金额最多 10 位整数和 2 位小数")
    private BigDecimal amount;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
