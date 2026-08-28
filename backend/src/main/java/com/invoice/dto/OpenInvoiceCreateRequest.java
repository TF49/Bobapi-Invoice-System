package com.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 开放接口发票申请请求
 */
@Data
public class OpenInvoiceCreateRequest {

    /**
     * 外部商户订单号（推荐传，用于幂等防重与状态查询）
     */
    @Size(max = 100, message = "外部订单号不能超过 100 个字符")
    private String outTradeNo;

    @NotBlank(message = "公司名称不能为空")
    @Size(max = 200, message = "公司名称不能超过 200 个字符")
    private String companyName;

    @Size(max = 100, message = "税号不能超过 100 个字符")
    private String taxNumber;

    @NotNull(message = "开票金额不能为空")
    @DecimalMin(value = "0.01", message = "开票金额必须大于等于 0.01")
    @Digits(integer = 10, fraction = 2, message = "开票金额最多 10 位整数和 2 位小数")
    private BigDecimal amount;

    @Size(max = 100, message = "开票类型不能超过 100 个字符")
    private String invoiceType = "技术服务费";

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}