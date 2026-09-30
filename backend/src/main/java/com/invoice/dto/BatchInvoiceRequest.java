package com.invoice.dto;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量发票申请请求 DTO
 */
@Data
public class BatchInvoiceRequest {
    
    @NotEmpty(message = "申请列表不能为空")
    @Valid
    private List<BatchInvoiceItemRequest> items;

    /**
     * 是否为同一抬头/金额的多开发票请求。仅控制批次内重复明细校验，不影响额度和幂等校验。
     */
    private Boolean duplicateInvoiceRequest = false;
}
