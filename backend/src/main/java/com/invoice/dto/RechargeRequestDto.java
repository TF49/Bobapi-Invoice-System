package com.invoice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 充值申请请求
 */
@Data
public class RechargeRequestDto {
    
    /**
     * 用户支付的手续费，最终申请额度由服务端按固定比例计算
     */
    @NotNull(message = "手续费不能为空")
    @DecimalMin(value = "0.01", message = "手续费不能小于 0.01 元")
    @DecimalMax(value = "29999.99", message = "手续费不能超过 29999.99 元")
    @Digits(integer = 5, fraction = 2, message = "手续费最多保留 2 位小数")
    private BigDecimal feeAmount;
    
    /**
     * 充值截图URL
     */
    @NotBlank(message = "请提供充值凭证截图")
    @Size(max = 512, message = "截图地址不能超过 512 个字符")
    private String screenshotUrl;
    
    /**
     * 用户备注
     */
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
