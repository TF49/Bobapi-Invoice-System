package com.invoice.service;

import com.invoice.dto.AiParseResponse;

import java.math.BigDecimal;

/**
 * AI 发票信息识别服务接口
 * <p>
 * 实现类负责调用具体的大模型 API（DeepSeek / Gemini / 通义千问等），
 * 从自由文本中提取公司名称、税号、开票金额。
 * 当 AI 功能未配置时，使用 {@link StubAiParseService} 作为占位实现。
 * </p>
 */
public interface AiParseService {

    /**
     * 从用户输入的自由文本中识别发票关键信息（第一阶段：提取）
     *
     * @param text 用户粘贴的原始文本（已在 Controller 层做长度校验）
     * @return 识别结果，未识别到的字段为 null
     */
    AiParseResponse parse(String text);

    /**
     * 对初步提取结果进行二次核查（第二阶段：审核）
     * <p>
     * 将原始文本与第一阶段提取的字段一起送给 AI，要求其核查准确性并修正错误。
     * </p>
     *
     * @param text        用户粘贴的原始文本
     * @param companyName 第一阶段提取的公司名称（可为 null）
     * @param taxNumber   第一阶段提取的税号（可为 null）
     * @param amount      第一阶段提取的金额（可为 null）
     * @return 核查并修正后的识别结果，未识别到的字段为 null
     */
    AiParseResponse verify(String text, String companyName, String taxNumber, BigDecimal amount);
}

