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
     * 识别到的税号，未识别到或个人/无税号则为 null
     */
    private String taxNumber;

    /**
     * 识别到的开票金额，未识别到则为 null
     */
    private BigDecimal amount;

    /**
     * 识别到的开票类型（技术服务费 / AI订阅服务费 / 计算服务费 / 研发和技术服务），未识别或不在白名单内则为 null
     */
    private String invoiceType;

    /**
     * 置信度：HIGH（公司名称、开票金额核心字段全部识别到）/ LOW（核心字段未识别到）
     * <p>税号（taxNumber）与开票类型（invoiceType）为可选/辅助字段，不强制影响置信度计算。</p>
     */
    private String confidence;

    /**
     * 提示信息，用于告知用户哪些字段未能识别
     */
    private String hint;
}
