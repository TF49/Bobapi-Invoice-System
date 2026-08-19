package com.invoice.dto;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 批量发票申请请求 DTO
 */
@Data
public class BatchInvoiceRequest {
    
    @NotEmpty(message = "申请列表不能为空")
    @Size(min = 1, max = 100, message = "单次批量申请数量为 1～100 条")
    @Valid
    private List<BatchInvoiceItemRequest> items;
}