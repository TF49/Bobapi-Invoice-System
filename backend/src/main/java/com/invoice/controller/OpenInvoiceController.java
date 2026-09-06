package com.invoice.controller;

import com.invoice.dto.ApiResponse;
import com.invoice.dto.BatchInvoiceRequest;
import com.invoice.dto.BatchInvoiceResponse;
import com.invoice.dto.OpenInvoiceCreateRequest;
import com.invoice.dto.OpenInvoiceResponse;
import com.invoice.exception.BusinessException;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.security.RateLimitService;
import com.invoice.service.InvoiceService;
import com.invoice.utils.WebUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/open/v1/invoices")
public class OpenInvoiceController {

    private final InvoiceService invoiceService;
    private final RateLimitService rateLimitService;

    public OpenInvoiceController(InvoiceService invoiceService, RateLimitService rateLimitService) {
        this.invoiceService = invoiceService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * 提交发票申请（OpenAPI）
     */
    @PostMapping
    public ApiResponse<OpenInvoiceResponse> createInvoice(
            @Valid @RequestBody OpenInvoiceCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Pattern(regexp = "^[A-Za-z0-9._:-]{16,64}$", message = "Idempotency-Key 格式不正确")
            String idempotencyKey,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("open-invoice-create:user:" + principal.userId(),
                60, Duration.ofMinutes(1), 42902, "发票申请提交过于频繁，请稍后再试");

        OpenInvoiceResponse response = invoiceService.createOpenInvoice(
                principal.userId(),
                request.getOutTradeNo(),
                idempotencyKey,
                request.getCompanyName(),
                request.getTaxNumber(),
                request.getAmount(),
                request.getInvoiceType(),
                request.getRemark()
        );
        return ApiResponse.success("申请成功", response);
    }

    /**
     * 批量提交发票申请（OpenAPI）
     */
    @PostMapping("/batch")
    public ApiResponse<BatchInvoiceResponse> createInvoicesBatch(
            @Valid @RequestBody BatchInvoiceRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Pattern(regexp = "^[A-Za-z0-9._:-]{16,64}$", message = "Idempotency-Key 格式不正确")
            String idempotencyKey,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("open-invoice-batch:user:" + principal.userId(),
                20, Duration.ofMinutes(1), 42902, "批量发票申请提交过于频繁，请稍后再试");

        String finalKey = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey.trim()
                : ("open_batch_" + UUID.randomUUID().toString().replace("-", ""));

        BatchInvoiceResponse response = invoiceService.createInvoicesBatch(
                principal.userId(), finalKey, request.getItems(), "API");
        return ApiResponse.success("批量申请成功", response);
    }

    /**
     * 按内部发票 ID 查询发票状态与信息
     */
    @GetMapping("/{id}")
    public ApiResponse<OpenInvoiceResponse> getInvoiceById(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("open-invoice-query:user:" + principal.userId(),
                120, Duration.ofMinutes(1), 42905, "查询过于频繁，请稍后再试");
        return ApiResponse.success(invoiceService.getOpenInvoiceById(principal.userId(), id));
    }

    /**
     * 按外部商户订单号查询发票状态与信息
     */
    @GetMapping("/by-out-trade-no/{outTradeNo}")
    public ApiResponse<OpenInvoiceResponse> getInvoiceByOutTradeNo(
            @PathVariable String outTradeNo,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("open-invoice-query:user:" + principal.userId(),
                120, Duration.ofMinutes(1), 42905, "查询过于频繁，请稍后再试");
        return ApiResponse.success(invoiceService.getOpenInvoiceByOutTradeNo(principal.userId(), outTradeNo));
    }

    /**
     * 按内部发票 ID 鉴权下载已开票文件
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadInvoiceById(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            HttpServletRequest request) {
        enforceRateLimit("open-invoice-download:user:" + principal.userId(),
                120, Duration.ofMinutes(1), 42904, "发票文件下载过于频繁，请稍后再试");

        InvoiceService.InvoiceDownload download = invoiceService.downloadInvoiceFile(
                id, principal.userId(), false);

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
     * 按外部商户订单号鉴权下载已开票文件
     */
    @GetMapping("/by-out-trade-no/{outTradeNo}/download")
    public ResponseEntity<Resource> downloadInvoiceByOutTradeNo(
            @PathVariable String outTradeNo,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            HttpServletRequest request) {
        OpenInvoiceResponse invoice = invoiceService.getOpenInvoiceByOutTradeNo(principal.userId(), outTradeNo);
        return downloadInvoiceById(invoice.id(), principal, request);
    }

    /**
     * 按内部发票 ID 取消未开票的发票申请（OpenAPI）
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<OpenInvoiceResponse> cancelInvoiceById(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("open-invoice-cancel:user:" + principal.userId(),
                30, Duration.ofMinutes(1), 42905, "取消操作过于频繁，请稍后再试");
        return ApiResponse.success("取消成功", invoiceService.cancelOpenInvoice(principal.userId(), id));
    }

    /**
     * 按外部商户订单号取消未开票的发票申请（OpenAPI）
     */
    @PostMapping("/by-out-trade-no/{outTradeNo}/cancel")
    public ApiResponse<OpenInvoiceResponse> cancelInvoiceByOutTradeNo(
            @PathVariable String outTradeNo,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        enforceRateLimit("open-invoice-cancel:user:" + principal.userId(),
                30, Duration.ofMinutes(1), 42905, "取消操作过于频繁，请稍后再试");
        return ApiResponse.success("取消成功", invoiceService.cancelOpenInvoiceByOutTradeNo(principal.userId(), outTradeNo));
    }

    private void enforceRateLimit(String key, int limit, Duration window, int code, String message) {
        RateLimitService.RateLimitResult result = rateLimitService.tryAcquire(key, limit, window);
        if (!result.allowed()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, code, message, result.retryAfterSeconds());
        }
    }
}
