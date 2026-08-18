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
 * 发票申请请求 DTO
 */
@Data
public class InvoiceRequest {
    
    @NotBlank(message = "公司名称不能为空")
    @Size(max = 200, message = "公司名称不能超过 200 个字符")
    private String companyName;
    
    @NotBlank(message = "税号不能为空")
    @Pattern(regexp = "^[A-Z0-9]{15,20}$", message = "税号格式不正确")
    private String taxNumber;
    
    @NotNull(message = "开票金额不能为空")
    @DecimalMin(value = "0.01", message = "开票金额必须大于等于 0.01")
    @Digits(integer = 10, fraction = 2, message = "开票金额最多 10 位整数和 2 位小数")
    private BigDecimal amount;
}
