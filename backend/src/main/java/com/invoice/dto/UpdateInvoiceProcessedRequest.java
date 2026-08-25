package com.invoice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateInvoiceProcessedRequest {

    @NotNull(message = "isProcessed 不能为空")
    private Boolean isProcessed;
}
