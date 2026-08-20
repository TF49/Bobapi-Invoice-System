package com.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理员充值额度请求
 */
@Data
public class AdminRechargeQuotaRequest {
    
    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额必须大于0")
    @Digits(integer = 9, fraction = 2, message = "充值金额格式不正确")
    private BigDecimal amount;
    
    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark;
}