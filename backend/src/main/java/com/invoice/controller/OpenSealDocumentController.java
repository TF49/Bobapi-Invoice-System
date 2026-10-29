package com.invoice.controller;

import com.invoice.dto.ApiResponse;
import com.invoice.dto.OpenSealDocumentResponse;
import com.invoice.exception.BusinessException;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.SealDocumentService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * OpenAPI 盖章文件接口。
 * 只操作调用方自己的文件（不论账号角色），且不提供盖章额度查询。
 */
@RestController
@Validated
@RequestMapping("/open/v1/documents")
public class OpenSealDocumentController {

    /** UserSealQuotaService 余额不足时的错误码，原消息带有当前余额 */
    private static final int CODE_SEAL_QUOTA_INSUFFICIENT = 40002;

    private final SealDocumentService sealDocumentService;
    private final RateLimitService rateLimitService;

    public OpenSealDocumentController(SealDocumentService sealDocumentService,
                                      RateLimitService rateLimitService) {
        this.sealDocumentService = sealDocumentService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * 上传待盖章文件（扣除单次盖章服务费）
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OpenSealDocumentResponse> uploadSourceDocument(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = "^[A-Za-z0-9._:-]{16,64}$", message = "Idempotency-Key 格式不正确")
            String idempotencyKey,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("open-doc-upload:user:" + principal.userId(),
                30, Duration.ofMinutes(1), 42901, "上传过于频繁，请稍后再试");
        try {
            SealDocumentService.UploadResult result =
                    sealDocumentService.uploadSourceDocument(principal.userId(), file, idempotencyKey);
            String message = result.duplicate()
                    ? "重复请求，已返回首次上传的记录，未重复扣费"
                    : "文件上传成功，等待盖章处理";
            return ApiResponse.success(message, OpenSealDocumentResponse.from(result.document()));
        } catch (BusinessException exception) {
            if (exception.getCode() == CODE_SEAL_QUOTA_INSUFFICIENT) {
                // 不通过错误消息暴露盖章额度余额
                throw new BusinessException(exception.getStatus(), exception.getCode(), "盖章额度不足，请先充值");
            }
            throw exception;
        }
    }

    /**
     * 查询我的盖章文件列表，可按状态过滤：PENDING / COMPLETED / REJECTED / CANCELLED / ALL
     */
    @GetMapping
    public ApiResponse<List<OpenSealDocumentResponse>> listMyDocuments(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("open-doc-query:user:" + principal.userId(),
                120, Duration.ofMinutes(1), 42905, "查询过于频繁，请稍后再试");
        List<OpenSealDocumentResponse> list = sealDocumentService.getMyDocuments(principal.userId(), status)
                .stream()
                .map(OpenSealDocumentResponse::from)
                .toList();
        return ApiResponse.success(list);
    }

    /**
     * 撤销待盖章的申请（自动退还服务费）
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<OpenSealDocumentResponse> cancelDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("open-doc-cancel:user:" + principal.userId(),
                30, Duration.ofMinutes(1), 42905, "撤销操作过于频繁，请稍后再试");
        return ApiResponse.success("申请已撤销，服务费已退回",
                OpenSealDocumentResponse.from(
                        sealDocumentService.cancelDocument(id, principal.userId(), false)));
    }

    /**
     * 下载盖章完成的交付文件
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadSealedFile(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("open-doc-download:user:" + principal.userId(),
                120, Duration.ofMinutes(1), 42904, "文件下载过于频繁，请稍后再试");
        SealDocumentService.FileDownload download =
                sealDocumentService.downloadSealedFile(id, principal.userId(), false);

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

    private void enforceRateLimit(String key, int limit, Duration window, int code, String message) {
        RateLimitService.RateLimitResult result = rateLimitService.tryAcquire(key, limit, window);
        if (!result.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, code, message, result.retryAfterSeconds());
        }
    }
}
