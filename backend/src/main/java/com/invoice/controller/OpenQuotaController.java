package com.invoice.controller;

import com.invoice.dto.ApiResponse;
import com.invoice.dto.OpenQuotaResponse;
import com.invoice.exception.BusinessException;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.UserQuotaService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/open/v1/quota")
public class OpenQuotaController {

    private final UserQuotaService userQuotaService;
    private final RateLimitService rateLimitService;

    public OpenQuotaController(UserQuotaService userQuotaService, RateLimitService rateLimitService) {
        this.userQuotaService = userQuotaService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public ApiResponse<OpenQuotaResponse> getMyQuota(@AuthenticationPrincipal JwtUserPrincipal principal) {
        RateLimitService.RateLimitResult result = rateLimitService.tryAcquire(
                "open-quota:" + principal.userId(), 120, Duration.ofMinutes(1));
        if (!result.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42907,
                    "额度查询过于频繁，请稍后再试", result.retryAfterSeconds());
        }
        return ApiResponse.success(OpenQuotaResponse.from(userQuotaService.getUserQuota(principal.userId())));
    }
}