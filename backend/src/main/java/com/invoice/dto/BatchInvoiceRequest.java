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
}
