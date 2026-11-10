package com.invoice.controller;

import com.invoice.dto.AnnouncementMarkReadRequest;
import com.invoice.dto.AnnouncementPageResponse;
import com.invoice.dto.AnnouncementResponse;
import com.invoice.dto.ApiResponse;
import com.invoice.exception.BusinessException;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.AnnouncementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * 登录用户查看系统公告（弹窗 + 历史列表）
 */
@Validated
@RestController
@RequestMapping("/announcements")
public class AnnouncementController {

    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final AnnouncementService announcementService;
    private final RateLimitService rateLimitService;

    public AnnouncementController(AnnouncementService announcementService, RateLimitService rateLimitService) {
        this.announcementService = announcementService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/unread")
    public ApiResponse<List<AnnouncementResponse>> unread(@AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("unread", principal.userId(), 60);
        return ApiResponse.success(announcementService.getUnread(principal.userId()));
    }

    @PostMapping("/read")
    public ApiResponse<Void> markRead(
            @Valid @RequestBody AnnouncementMarkReadRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("read", principal.userId(), 60);
        announcementService.markRead(principal.userId(), request.getIds());
        return ApiResponse.success(null);
    }

    @GetMapping
    public ApiResponse<AnnouncementPageResponse> history(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于 0") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量必须大于 0")
            @Max(value = 50, message = "每页最多查询 50 条") int pageSize,
            @RequestParam(required = false)
            @Pattern(regexp = "^(FEATURE|MAINTENANCE|IMPORTANT|GENERAL)$", message = "公告分类不正确") String category,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("history", principal.userId(), 60);
        return ApiResponse.success(announcementService.getPublishedPage(page, pageSize, category));
    }

    private void enforceRateLimit(String operation, Long userId, int limit) {
        RateLimitService.RateLimitResult result = rateLimitService.tryAcquire(
                "announcements:" + operation + ":" + userId, limit, RATE_WINDOW);
        if (!result.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42921,
                    "公告请求过于频繁，请稍后再试", result.retryAfterSeconds());
        }
    }
}
