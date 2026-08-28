package com.invoice.controller;

import com.invoice.dto.ApiKeyResponse;
import com.invoice.dto.ApiResponse;
import com.invoice.exception.BusinessException;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/users/api-key")
public class UserApiKeyController {

    private final UserService userService;
    private final RateLimitService rateLimitService;

    public UserApiKeyController(UserService userService, RateLimitService rateLimitService) {
        this.userService = userService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public ApiResponse<ApiKeyResponse> getMyApiKey(@AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("get", principal.userId(), 60);
        return ApiResponse.success(userService.getApiKey(principal.userId()));
    }

    @PostMapping("/generate")
    public ApiResponse<ApiKeyResponse> generateMyApiKey(@AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("generate", principal.userId(), 10);
        String apiKey = userService.generateOrResetApiKey(principal.userId(), principal.userId());
        return ApiResponse.success("API Key 生成成功", new ApiKeyResponse(apiKey, true));
    }

    @PutMapping("/status")
    public ApiResponse<ApiKeyResponse> updateMyApiKeyStatus(
            @RequestParam boolean enabled,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("status", principal.userId(), 20);
        userService.updateApiKeyStatus(principal.userId(), enabled, principal.userId());
        return ApiResponse.success("API Key 状态更新成功", userService.getApiKey(principal.userId()));
    }

    private void enforceRateLimit(String operation, Long userId, int limit) {
        RateLimitService.RateLimitResult result = rateLimitService.tryAcquire(
                "user-api-key:" + operation + ":" + userId, limit, Duration.ofMinutes(1));
        if (!result.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42907,
                    "操作过于频繁，请稍后再试", result.retryAfterSeconds());
        }
    }
}