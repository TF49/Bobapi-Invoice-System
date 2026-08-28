package com.invoice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchUpdateProcessedRequest {

    @NotEmpty(message = "发票ID列表不能为空")
    @Size(max = 500, message = "单次最多更新 500 条发票")
    private List<Long> invoiceIds;

    @NotNull(message = "isProcessed 不能为空")
    private Boolean isProcessed;
}
