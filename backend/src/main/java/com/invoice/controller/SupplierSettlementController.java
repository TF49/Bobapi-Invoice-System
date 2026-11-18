package com.invoice.controller;

import com.invoice.dto.ApiResponse;
import com.invoice.dto.SupplierSettlementRequest;
import com.invoice.dto.SupplierSettlementResponse;
import com.invoice.dto.SupplierSettlementVoidRequest;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.service.SupplierSettlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应商结算控制器（仅管理员）
 */
@RestController
@RequestMapping("/admin/supplier-settlement")
@PreAuthorize("hasRole('ADMIN')")
public class SupplierSettlementController {

    private final SupplierSettlementService supplierSettlementService;

    public SupplierSettlementController(SupplierSettlementService supplierSettlementService) {
        this.supplierSettlementService = supplierSettlementService;
    }

    /**
     * 创建结算记录
     */
    @PostMapping
    public ApiResponse<SupplierSettlementResponse> createSettlement(
            @Valid @RequestBody SupplierSettlementRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Pattern(regexp = "^[A-Za-z0-9._:-]{16,64}$", message = "Idempotency-Key 格式不正确")
            String idempotencyKey,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        SupplierSettlementResponse response = supplierSettlementService.createSettlement(
                request.getAmount(),
                request.getSellerCompany(),
                request.getInvoiceCategory(),
                request.getRemark(),
                principal.userId(),
                idempotencyKey
        );
        return ApiResponse.success(response);
    }

    /**
     * 作废结算记录（纠错：选错通道 / 填错金额后作废并重新登记）
     */
    @PostMapping("/{id}/void")
    public ApiResponse<SupplierSettlementResponse> voidSettlement(
            @PathVariable Long id,
            @Valid @RequestBody SupplierSettlementVoidRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(supplierSettlementService.voidSettlement(id, request.getReason(), principal.userId()));
    }

    /**
     * 获取结算历史记录列表
     */
    @GetMapping
    public ApiResponse<List<SupplierSettlementResponse>> getSettlementHistory() {
        List<SupplierSettlementResponse> history = supplierSettlementService.getSettlementHistory();
        return ApiResponse.success(history);
    }
}
