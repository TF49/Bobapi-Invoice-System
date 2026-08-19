package com.invoice.dto;

import lombok.Data;

/**
 * 批量发票申请单项结果 DTO
 */
@Data
public class BatchInvoiceItemResult {
    
    /**
     * 源文件行号
     */
    private Integer rowNumber;
    
    /**
     * 发票ID
     */
    private Long invoiceId;
    
    /**
     * 状态：SUCCESS-成功，FAILURE-失败
     */
    private String status;
    
    /**
     * 消息
     */
    private String message;

    public BatchInvoiceItemResult() {
    }

    public BatchInvoiceItemResult(Integer rowNumber, Long invoiceId, String status, String message) {
        this.rowNumber = rowNumber;
        this.invoiceId = invoiceId;
        this.status = status;
        this.message = message;
    }
}