package com.invoice.controller;

import com.invoice.dto.AdminAnnouncementPageResponse;
import com.invoice.dto.AdminAnnouncementResponse;
import com.invoice.dto.AnnouncementSaveRequest;
import com.invoice.dto.ApiResponse;
import com.invoice.exception.BusinessException;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.AnnouncementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 管理员公告管理
 */
@Validated
@RestController
@RequestMapping("/announcements/admin")
public class AnnouncementAdminController {

    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final AnnouncementService announcementService;
    private final RateLimitService rateLimitService;

    public AnnouncementAdminController(AnnouncementService announcementService, RateLimitService rateLimitService) {
        this.announcementService = announcementService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public ApiResponse<AdminAnnouncementPageResponse> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于 0") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页数量必须大于 0")
            @Max(value = 100, message = "每页最多查询 100 条") int pageSize,
            @RequestParam(required = false) @Size(max = 50, message = "搜索关键词不能超过 50 个字符") String keyword,
            @RequestParam(required = false)
            @Pattern(regexp = "^(FEATURE|MAINTENANCE|IMPORTANT|GENERAL)$", message = "公告分类不正确") String category,
            @RequestParam(required = false)
            @Pattern(regexp = "^(DRAFT|PUBLISHED|OFFLINE)$", message = "公告状态不正确") String status,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("list", principal.userId(), 120, "公告列表刷新过于频繁，请稍后再试");
        return ApiResponse.success(announcementService.getAdminPage(page, pageSize, keyword, category, status));
    }

    @PostMapping
    public ApiResponse<AdminAnnouncementResponse> create(
            @Valid @RequestBody AnnouncementSaveRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceWriteLimit(principal);
        return ApiResponse.success("公告已保存为草稿", announcementService.create(request, principal.userId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminAnnouncementResponse> update(
            @PathVariable @Positive(message = "公告 ID 必须大于 0") Long id,
            @Valid @RequestBody AnnouncementSaveRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceWriteLimit(principal);
        return ApiResponse.success("公告已更新", announcementService.update(id, request));
    }

    @PutMapping("/{id}/publish")
    public ApiResponse<AdminAnnouncementResponse> publish(
            @PathVariable @Positive(message = "公告 ID 必须大于 0") Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceWriteLimit(principal);
        return ApiResponse.success("公告已发布", announcementService.publish(id));
    }

    @PutMapping("/{id}/offline")
    public ApiResponse<AdminAnnouncementResponse> offline(
            @PathVariable @Positive(message = "公告 ID 必须大于 0") Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceWriteLimit(principal);
        return ApiResponse.success("公告已下线", announcementService.offline(id));
    }

    @PostMapping("/{id}/repush")
    public ApiResponse<AdminAnnouncementResponse> repush(
            @PathVariable @Positive(message = "公告 ID 必须大于 0") Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceWriteLimit(principal);
        return ApiResponse.success("公告已重新推送", announcementService.repush(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable @Positive(message = "公告 ID 必须大于 0") Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceWriteLimit(principal);
        announcementService.delete(id);
        return ApiResponse.success("公告已删除", null);
    }

    private void enforceWriteLimit(JwtUserPrincipal principal) {
        enforceRateLimit("write", principal.userId(), 30, "公告管理操作过于频繁，请稍后再试");
    }

    private void enforceRateLimit(String operation, Long userId, int limit, String message) {
        RateLimitService.RateLimitResult result = rateLimitService.tryAcquire(
                "admin-announcements:" + operation + ":" + userId, limit, RATE_WINDOW);
        if (!result.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42920, message, result.retryAfterSeconds());
        }
    }
}
