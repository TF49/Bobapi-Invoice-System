package com.invoice.controller;

import com.invoice.dto.ApiResponse;
import com.invoice.dto.BatchInvoiceRequest;
import com.invoice.dto.BatchInvoiceResponse;
import com.invoice.dto.InvoiceRequest;
import com.invoice.dto.InvoiceResponse;
import com.invoice.exception.BusinessException;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.InvoiceService;
import com.invoice.utils.WebUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@RestController
@Validated
@RequestMapping("/invoices")
public class InvoiceController {

    @Value("${app.invoice.batch.user-limit:3}")
    private int batchUserLimit = 3;

    @Value("${app.invoice.batch.user-window-minutes:1}")
    private int batchUserWindowMinutes = 1;

    @Value("${app.invoice.batch.ip-limit:20}")
    private int batchIpLimit = 20;

    @Value("${app.invoice.batch.ip-window-minutes:5}")
    private int batchIpWindowMinutes = 5;

    private final InvoiceService invoiceService;
    private final RateLimitService rateLimitService;

    public InvoiceController(InvoiceService invoiceService, RateLimitService rateLimitService) {
        this.invoiceService = invoiceService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    public ApiResponse<InvoiceResponse> createInvoice(
            @Valid @RequestBody InvoiceRequest request,
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = "^[A-Za-z0-9._:-]{16,64}$", message = "Idempotency-Key 格式不正确")
            String idempotencyKey,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        RateLimitService.RateLimitResult rateLimit = rateLimitService.tryAcquire(
                "invoice-create:" + principal.userId(), 10, Duration.ofMinutes(1));
        if (!rateLimit.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42902,
                    "发票申请提交过于频繁，请稍后再试", rateLimit.retryAfterSeconds());
        }

        InvoiceResponse invoice = invoiceService.createInvoice(
                principal.userId(), idempotencyKey, request.getCompanyName(),
                request.getTaxNumber(), request.getAmount());
        return ApiResponse.success("申请成功", invoice);
    }

    @PostMapping("/batch")
    public ApiResponse<BatchInvoiceResponse> createInvoicesBatch(
            @Valid @RequestBody BatchInvoiceRequest request,
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = "^[A-Za-z0-9._:-]{16,64}$", message = "Idempotency-Key 格式不正确")
            String idempotencyKey,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            HttpServletRequest httpRequest) {
        RateLimitService.RateLimitResult rateLimit = rateLimitService.tryAcquire(
                "invoice-batch:user:" + principal.userId(), batchUserLimit,
                Duration.ofMinutes(batchUserWindowMinutes));
        if (!rateLimit.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42902,
                    "批量申请提交过于频繁，请稍后再试", rateLimit.retryAfterSeconds());
        }

        RateLimitService.RateLimitResult ipRateLimit = rateLimitService.tryAcquire(
                "invoice-batch:ip:" + WebUtils.extractClientIp(httpRequest), batchIpLimit,
                Duration.ofMinutes(batchIpWindowMinutes));
        if (!ipRateLimit.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 42902,
                    "当前网络批量申请过于频繁，请稍后再试", ipRateLimit.retryAfterSeconds());
        }

        BatchInvoiceResponse response = invoiceService.createInvoicesBatch(
                principal.userId(), idempotencyKey, request.getItems());
        return ApiResponse.success("批量申请成功", response);
    }

    @GetMapping("/my")
    public ApiResponse<List<InvoiceResponse>> getMyInvoices(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(invoiceService.getInvoicesByUserId(principal.userId()));
    }

    @GetMapping("/admin/all")
    public ApiResponse<List<InvoiceResponse>> getAllInvoices() {
        return ApiResponse.success(invoiceService.getAllInvoices());
    }

    @GetMapping("/admin/dashboard")
    public ApiResponse<com.invoice.dto.DashboardStats> getDashboardStats() {
        return ApiResponse.success(invoiceService.getDashboardStats());
    }

    @PostMapping("/admin/{id}/upload")
    public ApiResponse<InvoiceResponse> uploadInvoice(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            HttpServletRequest request) {
        enforceRateLimit(
                "invoice-upload:user:" + principal.userId(),
                20, Duration.ofMinutes(5), 42903, "发票上传过于频繁，请稍后再试");
        enforceRateLimit(
                "invoice-upload:ip:" + WebUtils.extractClientIp(request),
                40, Duration.ofMinutes(5), 42903, "当前网络上传过于频繁，请稍后再试");
        return ApiResponse.success("上传成功", invoiceService.uploadInvoiceFile(id, file));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> previewInvoice(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            HttpServletRequest request) {
        enforceFileReadLimits(id, principal.userId(), WebUtils.extractClientIp(request));
        InvoiceService.InvoiceDownload download = invoiceService.previewInvoiceFile(
                id, principal.userId(), "ADMIN".equals(principal.role()));

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

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadInvoice(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            HttpServletRequest request) {
        enforceFileReadLimits(id, principal.userId(), WebUtils.extractClientIp(request));
        InvoiceService.InvoiceDownload download = invoiceService.downloadInvoiceFile(
                id, principal.userId(), "ADMIN".equals(principal.role()));

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

    private void enforceFileReadLimits(Long invoiceId, Long userId, String remoteAddress) {
        enforceRateLimit(
                "invoice-file:user:" + userId,
                120, Duration.ofMinutes(1), 42904, "发票文件访问过于频繁，请稍后再试");
        enforceRateLimit(
                "invoice-file:ip:" + remoteAddress,
                240, Duration.ofMinutes(1), 42904, "当前网络访问发票文件过于频繁，请稍后再试");
        enforceRateLimit(
                "invoice-file:item:" + userId + ":" + invoiceId,
                30, Duration.ofMinutes(1), 42904, "该发票访问过于频繁，请稍后再试");
    }

    private void enforceRateLimit(String key, int limit, Duration window, int code, String message) {
        RateLimitService.RateLimitResult result = rateLimitService.tryAcquire(key, limit, window);
        if (!result.allowed()) {
            throw new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS, code, message, result.retryAfterSeconds());
        }
    }
}
