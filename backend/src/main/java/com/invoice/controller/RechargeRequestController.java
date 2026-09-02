package com.invoice.controller;

import com.invoice.dto.ApiResponse;
import com.invoice.dto.RechargeRequestDto;
import com.invoice.dto.RechargeRequestResponse;
import com.invoice.dto.RechargeReviewDto;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.service.RechargeRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 充值申请控制器
 */
@RestController
@Validated
@RequestMapping("/recharge-requests")
public class RechargeRequestController {

    private final RechargeRequestService rechargeRequestService;

    public RechargeRequestController(RechargeRequestService rechargeRequestService) {
        this.rechargeRequestService = rechargeRequestService;
    }

    /**
     * 上传充值截图
     */
    @PostMapping("/upload-screenshot")
    public ApiResponse<String> uploadScreenshot(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        String url = rechargeRequestService.uploadScreenshot(file);
        return ApiResponse.success("上传成功", url);
    }

    /**
     * 读取充值截图文件
     */
    @GetMapping("/screenshot/{fileName:.+}")
    public ResponseEntity<Resource> getScreenshot(@PathVariable String fileName) {
        RechargeRequestService.ScreenshotResource screenshot = rechargeRequestService.loadScreenshotResource(fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(screenshot.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .header("X-Content-Type-Options", "nosniff")
                .body(screenshot.resource());
    }

    /**
     * 用户创建充值申请
     */
    @PostMapping
    public ApiResponse<RechargeRequestResponse> createRequest(
            @Valid @RequestBody RechargeRequestDto requestDto,
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = "^[A-Za-z0-9._:-]{16,64}$", message = "Idempotency-Key 格式不正确")
            String idempotencyKey,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        com.invoice.entity.RechargeRequest request = rechargeRequestService.createRequest(
                principal.userId(), requestDto, idempotencyKey);
        return ApiResponse.success(
            "充值申请提交成功，请等待管理员审核",
            rechargeRequestService.toResponse(request)
        );
    }

    /**
     * 获取当前用户的充值申请列表
     */
    @GetMapping("/my")
    public ApiResponse<List<RechargeRequestResponse>> getMyRequests(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(rechargeRequestService.getUserRequests(principal.userId()));
    }

    /**
     * 获取待审核的申请列表（管理员）
     */
    @GetMapping("/admin/pending")
    public ApiResponse<List<RechargeRequestResponse>> getPendingRequests(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        if (!"ADMIN".equals(principal.role())) {
            throw new com.invoice.exception.BusinessException(
                org.springframework.http.HttpStatus.FORBIDDEN, 40301, "只有管理员可以查看待审核申请"
            );
        }
        return ApiResponse.success(rechargeRequestService.getPendingRequests());
    }

    /**
     * 获取所有申请列表（管理员）
     */
    @GetMapping("/admin/all")
    public ApiResponse<List<RechargeRequestResponse>> getAllRequests(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        if (!"ADMIN".equals(principal.role())) {
            throw new com.invoice.exception.BusinessException(
                org.springframework.http.HttpStatus.FORBIDDEN, 40301, "只有管理员可以查看所有申请"
            );
        }
        return ApiResponse.success(rechargeRequestService.getAllRequests(status));
    }

    /**
     * 管理员审核充值申请
     */
    @PutMapping("/admin/{id}/review")
    public ApiResponse<Void> reviewRequest(
            @PathVariable Long id,
            @Valid @RequestBody RechargeReviewDto reviewDto,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        if (!"ADMIN".equals(principal.role())) {
            throw new com.invoice.exception.BusinessException(
                org.springframework.http.HttpStatus.FORBIDDEN, 40301, "只有管理员可以审核充值申请"
            );
        }
        rechargeRequestService.reviewRequest(id, reviewDto, principal.userId());
        return ApiResponse.success("审核完成", null);
    }

    /**
     * 获取待审核申请数量（管理员）
     */
    @GetMapping("/admin/pending-count")
    public ApiResponse<Long> getPendingCount(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        if (!"ADMIN".equals(principal.role())) {
            throw new com.invoice.exception.BusinessException(
                org.springframework.http.HttpStatus.FORBIDDEN, 40301, "只有管理员可以查看待审核数量"
            );
        }
        return ApiResponse.success(rechargeRequestService.getPendingCount());
    }
}
