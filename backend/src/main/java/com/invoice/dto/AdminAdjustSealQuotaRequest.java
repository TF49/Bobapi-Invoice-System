package com.invoice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 管理员调整盖章额度请求
 */
@Data
public class AdminAdjustSealQuotaRequest {
    
    @NotNull(message = "调整金额不能为空")
    @DecimalMin(value = "-999999.99", message = "调整金额不能低于 -999999.99 元")
    @DecimalMax(value = "999999.99", message = "调整金额不能超过 999999.99 元")
    @Digits(integer = 6, fraction = 2, message = "金额最多保留 2 位小数")
    private BigDecimal amount;
    
    @Size(max = 200, message = "备注不能超过 200 个字符")
    private String remark;
}
