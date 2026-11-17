package com.invoice.controller;

import com.invoice.dto.ApiResponse;
import com.invoice.dto.TaxFlowResponse;
import com.invoice.exception.BusinessException;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.TaxFlowService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * 税号数据流：用户端只能查询本人发票；/admin 路径由安全配置限定为管理员与开票员。
 */
@RestController
@RequestMapping("/invoices")
public class TaxFlowController {

    private final TaxFlowService taxFlowService;
    private final RateLimitService rateLimitService;

    public TaxFlowController(TaxFlowService taxFlowService, RateLimitService rateLimitService) {
        this.taxFlowService = taxFlowService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/flow/suggest")
    public ApiResponse<List<TaxFlowResponse.Suggestion>> suggest(
            @RequestParam("q") String query,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceSuggestLimit(principal);
        return ApiResponse.success(taxFlowService.suggest(query, principal.userId()));
    }

    @GetMapping("/flow")
    public ApiResponse<TaxFlowResponse> flow(
            @RequestParam("taxNumber") String taxNumber,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceFlowLimit(principal);
        return ApiResponse.success(taxFlowService.getFlow(taxNumber, principal.userId(), page, size, false, true));
    }

    @GetMapping("/admin/flow/suggest")
    public ApiResponse<List<TaxFlowResponse.Suggestion>> adminSuggest(
            @RequestParam("q") String query,
            @RequestParam(value = "userId", required = false) Long userId,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceSuggestLimit(principal);
        return ApiResponse.success(taxFlowService.suggest(query, userId));
    }

    @GetMapping("/admin/flow")
    public ApiResponse<TaxFlowResponse> adminFlow(
            @RequestParam("taxNumber") String taxNumber,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceFlowLimit(principal);
        boolean includeBalance = "ADMIN".equals(principal.role());
        return ApiResponse.success(taxFlowService.getFlow(taxNumber, userId, page, size, true, includeBalance));
    }

    private void enforceSuggestLimit(JwtUserPrincipal principal) {
        enforceRateLimit("tax-flow-suggest:user:" + principal.userId(), 120, "搜索过于频繁，请稍后再试");
    }

    private void enforceFlowLimit(JwtUserPrincipal principal) {
        enforceRateLimit("tax-flow:user:" + principal.userId(), 60, "查询过于频繁，请稍后再试");
    }

    private void enforceRateLimit(String key, int limit, String message) {
        RateLimitService.RateLimitResult result = rateLimitService.tryAcquire(key, limit, Duration.ofMinutes(1));
        if (!result.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42905, message, result.retryAfterSeconds());
        }
    }
}
