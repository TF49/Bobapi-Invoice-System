package com.invoice.controller;

import com.invoice.dto.ApiResponse;
import com.invoice.dto.SupplierSettlementRequest;
import com.invoice.dto.SupplierSettlementResponse;
import com.invoice.security.JwtUserPrincipal;
import com.invoice.service.SupplierSettlementService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应商结算控制器（仅管理员）
 */
@RestController
@RequestMapping("/api/admin/supplier-settlement")
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
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        SupplierSettlementResponse response = supplierSettlementService.createSettlement(
                request.getAmount(),
                request.getRemark(),
                principal.userId()
        );
        return ApiResponse.success(response);
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
