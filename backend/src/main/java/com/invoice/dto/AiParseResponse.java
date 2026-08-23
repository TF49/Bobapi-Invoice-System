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
     * 置信度：HIGH（三个字段全部识别到）/ LOW（部分字段未识别到）
     */
    private String confidence;

    /**
     * 提示信息，用于告知用户哪些字段未能识别
     */
    private String hint;
}
