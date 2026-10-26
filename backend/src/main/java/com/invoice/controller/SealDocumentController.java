package com.invoice.controller;

import com.invoice.dto.AdminAdjustSealQuotaRequest;
import com.invoice.dto.ApiResponse;
import com.invoice.dto.SealDocumentRejectRequest;
import com.invoice.dto.SealDocumentResponse;
import com.invoice.dto.UserSealQuotaResponse;
import com.invoice.entity.UserSealQuota;
import com.invoice.exception.BusinessException;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.SealDocumentService;
import com.invoice.service.UserSealQuotaService;
import com.invoice.utils.WebUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 盖章文件控制器
 */
@RestController
@Validated
@RequestMapping("/documents")
public class SealDocumentController {

    private final SealDocumentService sealDocumentService;
    private final UserSealQuotaService userSealQuotaService;
    private final RateLimitService rateLimitService;

    public SealDocumentController(SealDocumentService sealDocumentService,
                                  UserSealQuotaService userSealQuotaService,
                                  RateLimitService rateLimitService) {
        this.sealDocumentService = sealDocumentService;
        this.userSealQuotaService = userSealQuotaService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * 获取当前用户的盖章额度详情
     */
    @GetMapping("/quota")
    public ApiResponse<UserSealQuotaResponse> getMySealQuota(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(userSealQuotaService.getUserQuotaResponse(principal.userId()));
    }

    /**
     * 用户直接上传待盖章文件（无需表单，扣除 10 元额度）
     */
    @PostMapping("/upload")
    public ApiResponse<SealDocumentResponse> uploadSourceDocument(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            HttpServletRequest request) {
        enforceRateLimit(
                "doc-upload:user:" + principal.userId(),
                30, Duration.ofMinutes(1), 42901, "上传过于频繁，请稍后再试");
        enforceRateLimit(
                "doc-upload:ip:" + WebUtils.extractClientIp(request),
                60, Duration.ofMinutes(1), 42901, "当前网络上传过于频繁，请稍后再试");

        SealDocumentResponse response = sealDocumentService.uploadSourceDocument(principal.userId(), file);
        return ApiResponse.success("文件上传成功，已扣除 10 元额度，等待盖章处理", response);
    }

    /**
     * 用户获取我的盖章文件列表
     */
    @GetMapping("/my")
    public ApiResponse<List<SealDocumentResponse>> getMyDocuments(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(sealDocumentService.getMyDocuments(principal.userId(), status));
    }

    /**
     * 用户撤销盖章申请（自动退还 10 元）
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<SealDocumentResponse> cancelDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        boolean isAdmin = "ADMIN".equals(principal.role());
        SealDocumentResponse response = sealDocumentService.cancelDocument(id, principal.userId(), isAdmin);
        return ApiResponse.success("申请已撤销，10 元服务费已退回您的盖章钱包", response);
    }

    /**
     * 用户标记处理状态
     */
    @PutMapping("/{id}/processed")
    public ApiResponse<Void> updateProcessed(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") Boolean isProcessed,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        boolean isAdminOrClerk = "ADMIN".equals(principal.role()) || "INVOICE_CLERK".equals(principal.role());
        sealDocumentService.updateProcessed(id, principal.userId(), isAdminOrClerk, isProcessed);
        return ApiResponse.success("更新成功", null);
    }

    /**
     * 下载原始待盖章文件
     */
    @GetMapping("/{id}/download-source")
    public ResponseEntity<Resource> downloadSourceFile(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        boolean isAdminOrClerk = "ADMIN".equals(principal.role()) || "INVOICE_CLERK".equals(principal.role());
        SealDocumentService.FileDownload download = sealDocumentService.downloadSourceFile(
                id, principal.userId(), isAdminOrClerk);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    /**
     * 在线预览盖章交付文件
     */
    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> previewSealedFile(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        boolean isAdminOrClerk = "ADMIN".equals(principal.role()) || "INVOICE_CLERK".equals(principal.role());
        SealDocumentService.FileDownload download = sealDocumentService.previewSealedFile(
                id, principal.userId(), isAdminOrClerk);

        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    /**
     * 下载盖章交付文件
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadSealedFile(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        boolean isAdminOrClerk = "ADMIN".equals(principal.role()) || "INVOICE_CLERK".equals(principal.role());
        SealDocumentService.FileDownload download = sealDocumentService.downloadSealedFile(
                id, principal.userId(), isAdminOrClerk);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    // =================================== 管理端接口 ===================================

    /**
     * 管理员/开票员获取全部盖章文件
     */
    @GetMapping("/admin/all")
    public ApiResponse<List<SealDocumentResponse>> getAllDocuments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String username,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(sealDocumentService.getAllDocuments(status, username));
    }

    /**
     * 获取待盖章待办数量（角标）
     */
    @GetMapping("/admin/pending-count")
    public ApiResponse<Long> getPendingCount(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(sealDocumentService.getPendingCount());
    }

    /**
     * 管理员/开票员上传盖章交付文件
     */
    @PostMapping("/admin/{id}/upload")
    public ApiResponse<SealDocumentResponse> uploadSealedDocument(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        SealDocumentResponse response = sealDocumentService.uploadSealedDocument(
                id, file, principal.userId());
        return ApiResponse.success("交付文件上传成功，工单已完成", response);
    }

    /**
     * 管理员/开票员驳回盖章文件（自动退款 10 元）
     */
    @PostMapping("/admin/{id}/reject")
    public ApiResponse<SealDocumentResponse> rejectDocument(
            @PathVariable Long id,
            @Valid @RequestBody SealDocumentRejectRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        SealDocumentResponse response = sealDocumentService.rejectDocument(
                id, principal.userId(), request.getRejectReason());
        return ApiResponse.success("已驳回申请，10 元服务费已退回用户盖章钱包", response);
    }

    /**
     * 管理员手动调整用户盖章额度
     */
    @PostMapping("/admin/quota/adjust")
    public ApiResponse<UserSealQuota> adjustUserSealQuota(
            @RequestParam Long userId,
            @Valid @RequestBody AdminAdjustSealQuotaRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        UserSealQuota quota = userSealQuotaService.adjustQuota(
                userId, request.getAmount(), principal.userId(), request.getRemark());
        return ApiResponse.success("调整盖章额度成功", quota);
    }

    private void enforceRateLimit(String key, int limit, Duration window, int code, String message) {
        RateLimitService.RateLimitResult result = rateLimitService.tryAcquire(key, limit, window);
        if (!result.allowed()) {
            throw new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS, code, message, result.retryAfterSeconds());
        }
    }
}
