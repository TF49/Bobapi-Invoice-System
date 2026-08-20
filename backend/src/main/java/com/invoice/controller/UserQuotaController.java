package com.invoice.controller;

import com.invoice.dto.ApiResponse;
import com.invoice.dto.UserQuotaResponse;
import com.invoice.dto.UserQuotaTransactionResponse;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.UserQuotaService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户额度查询控制器
 */
@RestController
@RequestMapping("/users/quota")
public class UserQuotaController {

    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final UserQuotaService userQuotaService;
    private final RateLimitService rateLimitService;

    public UserQuotaController(UserQuotaService userQuotaService, RateLimitService rateLimitService) {
        this.userQuotaService = userQuotaService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public ApiResponse<UserQuotaResponse> getMyQuota(@AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("view", principal.userId(), 60, 42907, "额度查询过于频繁，请稍后再试");
        return ApiResponse.success(UserQuotaResponse.from(userQuotaService.getUserQuota(principal.userId())));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<UserQuotaTransactionResponse>> getMyTransactions(
            @RequestParam(required = false) String transactionType,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("view", principal.userId(), 60, 42907, "额度查询过于频繁，请稍后再试");
        List<UserQuotaTransactionResponse> transactions = userQuotaService.getTransactionHistory(
                principal.userId(), transactionType)
                .stream()
                .map(UserQuotaTransactionResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.success(transactions);
    }

    private void enforceRateLimit(String operation, Long userId, int limit, int code, String message) {
        RateLimitService.RateLimitResult result = rateLimitService.tryAcquire(
                "user-quota:" + operation + ":" + userId, limit, RATE_WINDOW);
        if (!result.allowed()) {
            throw new com.invoice.exception.BusinessException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, code, message, result.retryAfterSeconds());
        }
    }
}