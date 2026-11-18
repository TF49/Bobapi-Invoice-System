package com.invoice.dto;

import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 供应商结算请求 DTO（按通道分类型结算：出票主体 × 票种）
 */
@Data
public class SupplierSettlementRequest {

    @NotNull(message = "结算金额不能为空")
    @DecimalMin(value = "0.01", message = "结算金额必须大于等于 0.01")
    @Digits(integer = 10, fraction = 2, message = "结算金额最多 10 位整数和 2 位小数")
    private BigDecimal amount;

    @NotBlank(message = "结算通道出票主体不能为空")
    @Pattern(regexp = "^(DEFAULT|TECH)$", message = "出票主体必须为 DEFAULT 或 TECH")
    private String sellerCompany;

    @NotBlank(message = "结算通道票种不能为空")
    @Pattern(regexp = "^(NORMAL|VAT_SPECIAL)$", message = "票种必须为 NORMAL 或 VAT_SPECIAL")
    private String invoiceCategory;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
