package com.invoice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理员调整额度请求
 */
@Data
public class AdminAdjustQuotaRequest {
    
    @NotNull(message = "调整金额不能为空")
    @Digits(integer = 9, fraction = 2, message = "调整金额格式不正确")
    private BigDecimal amount;
    
    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark;
}