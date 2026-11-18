package com.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 供应商结算作废请求 DTO
 */
@Data
public class SupplierSettlementVoidRequest {

    @NotBlank(message = "作废原因不能为空")
    @Size(max = 200, message = "作废原因不能超过 200 个字符")
    private String reason;
}
