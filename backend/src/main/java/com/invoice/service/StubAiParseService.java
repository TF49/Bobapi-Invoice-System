package com.invoice.service;

import com.invoice.dto.AiParseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * AI 识别服务——占位实现（Stub）
 * <p>
 * 当配置文件中 {@code ai.enabled=false}（或未配置）时自动激活。
 * 不调用任何外部接口，直接返回提示用户配置 AI 服务的响应。
 * </p>
 * <p>
 * 接入真实 AI 模型时，请：
 * <ol>
 *   <li>在 application.yml 中设置 {@code ai.enabled=true}</li>
 *   <li>配置 {@code ai.provider} / {@code ai.api-key} / {@code ai.model} 等参数</li>
 *   <li>系统将自动加载 {@link OpenAiCompatibleAiParseService}，本类将被自动跳过，无需删除或添加 @Primary</li>
 * </ol>
 * </p>
 */
@Service
@ConditionalOnProperty(name = "ai.enabled", havingValue = "false", matchIfMissing = true)
public class StubAiParseService implements AiParseService {

    private static final Logger log = LoggerFactory.getLogger(StubAiParseService.class);

    @Override
    public AiParseResponse parse(String text) {
        log.warn("AI 识别服务尚未配置，返回占位响应。请在 application.yml 中设置 ai.enabled=true 并配置 api-key。");
        return new AiParseResponse(
                null,
                null,
                null,
                null,
                "LOW",
                "AI 识别服务暂未开通，请手动填写发票信息"
        );
    }

    @Override
    public AiParseResponse verify(String text, String companyName, String taxNumber, BigDecimal amount, String invoiceType) {
        log.warn("AI 核查服务尚未配置，直接透传初步提取结果。");
        // Stub 模式：直接将第一阶段的提取结果原样返回，UI 进度条仍可正常走完
        return new AiParseResponse(
                companyName,
                taxNumber,
                amount,
                invoiceType,
                "LOW",
                "AI 核查服务暂未开通，已直接使用初步识别结果，请人工核实"
        );
    }
}
