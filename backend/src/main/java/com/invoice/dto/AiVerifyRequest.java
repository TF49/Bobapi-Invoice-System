package com.invoice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI 发票信息二次核查请求 DTO
 * <p>
 * 携带原始文本与初步提取结果，AI 将对提取结果进行交叉核查并修正。
 * </p>
 */
@Data
public class AiVerifyRequest {

    /**
     * 用户粘贴的原始文本，与初次提取请求保持一致
     */
    @NotBlank(message = "核查文本不能为空")
    @Size(max = 2000, message = "核查文本不能超过 2000 个字符")
    private String text;

    /**
     * 初步提取的公司名称（可为 null）
     */
    private String companyName;

    /**
     * 初步提取的税号（可为 null）
     */
    private String taxNumber;

    /**
     * 初步提取的开票金额（可为 null）
     */
    @DecimalMin(value = "0.01", message = "金额不能小于 0.01")
    @DecimalMax(value = "9999999999.99", message = "金额超出有效范围")
    private BigDecimal amount;

    /**
     * 初步提取的开票类型（可为 null，若有则辅助 AI 核查；必须为允许的类型之一）
     */
    @Size(max = 50, message = "开票类型长度不能超过 50 个字符")
    private String invoiceType;
}
