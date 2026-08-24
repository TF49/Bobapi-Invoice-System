package com.invoice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * AI 智能识别发票信息响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiParseResponse {

    /**
     * 识别到的公司名称，未识别到则为 null
     */
    private String companyName;

    /**
     * 识别到的税号（15-20位大写字母或数字），未识别到则为 null
     */
    private String taxNumber;

    /**
     * 识别到的开票金额，未识别到则为 null
     */
    private BigDecimal amount;

    /**
     * 识别到的开票类型（技术服务费 / AI订阅服务费 / 计算服务费），未识别或不在白名单内则为 null
     */
    private String invoiceType;

    /**
     * 置信度：HIGH（公司名称、税号、开票金额三个核心字段全部识别到）/ LOW（部分核心字段未识别到）
     * <p>开票类型（invoiceType）为辅助字段，不影响置信度计算。</p>
     */
    private String confidence;

    /**
     * 提示信息，用于告知用户哪些字段未能识别
     */
    private String hint;
}
