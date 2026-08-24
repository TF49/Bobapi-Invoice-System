package com.invoice.controller;

import com.invoice.dto.AiParseRequest;
import com.invoice.dto.AiParseResponse;
import com.invoice.dto.AiVerifyRequest;
import com.invoice.dto.ApiResponse;
import com.invoice.exception.BusinessException;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.AiParseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * AI 智能识别发票信息接口
 */
@RestController
@Validated
@RequestMapping("/ai")
public class AiParseController {

    private final AiParseService aiParseService;
    private final RateLimitService rateLimitService;

    public AiParseController(AiParseService aiParseService, RateLimitService rateLimitService) {
        this.aiParseService = aiParseService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * POST /api/ai/parse-invoice
     * <p>
     * 接收用户粘贴的自由文本，调用 AI 服务提取发票关键信息（第一阶段：提取）：
     * 公司名称、税号、开票金额。开票类型固定由前端保持不变。
     * </p>
     *
     * @param request   包含用户输入文本（最大 2000 字符）
     * @param principal 当前登录用户（用于限流）
     * @return 识别结果，未识别到的字段为 null
     */
    @PostMapping("/parse-invoice")
    public ApiResponse<AiParseResponse> parseInvoice(
            @Valid @RequestBody AiParseRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal) {

        // 限流：每用户每分钟最多 20 次 AI 识别请求，防止滥用
        RateLimitService.RateLimitResult rateLimit = rateLimitService.tryAcquire(
                "ai-parse:user:" + principal.userId(), 20, Duration.ofMinutes(1));
        if (!rateLimit.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42905,
                    "AI 识别请求过于频繁，请稍后再试", rateLimit.retryAfterSeconds());
        }

        AiParseResponse result = aiParseService.parse(request.getText());
        return ApiResponse.success("识别完成", result);
    }

    /**
     * POST /api/ai/verify-invoice
     * <p>
     * 接收原始文本与初步提取结果，调用 AI 服务进行二次核查（第二阶段：审核）。
     * AI 将对比原文与提取字段，修正可能存在的错误。
     * </p>
     *
     * @param request   包含原始文本及初步提取的公司名称、税号、金额
     * @param principal 当前登录用户（用于限流）
     * @return 核查并修正后的识别结果
     */
    @PostMapping("/verify-invoice")
    public ApiResponse<AiParseResponse> verifyInvoice(
            @Valid @RequestBody AiVerifyRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal) {

        // 限流策略与 parse-invoice 保持一致
        RateLimitService.RateLimitResult rateLimit = rateLimitService.tryAcquire(
                "ai-parse:user:" + principal.userId(), 20, Duration.ofMinutes(1));
        if (!rateLimit.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42905,
                    "AI 识别请求过于频繁，请稍后再试", rateLimit.retryAfterSeconds());
        }

        AiParseResponse result = aiParseService.verify(
                request.getText(),
                request.getCompanyName(),
                request.getTaxNumber(),
                request.getAmount(),
                request.getInvoiceType()
        );
        return ApiResponse.success("核查完成", result);
    }
}
