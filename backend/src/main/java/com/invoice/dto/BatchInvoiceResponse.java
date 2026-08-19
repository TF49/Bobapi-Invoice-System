package com.invoice.dto;

import lombok.Data;
import java.util.List;

/**
 * 批量发票申请响应 DTO
 */
@Data
public class BatchInvoiceResponse {
    
    /**
     * 批次ID
     */
    private Long batchId;
    
    /**
     * 总条数
     */
    private Integer total;
    
    /**
     * 成功条数
     */
    private Integer successCount;
    
    /**
     * 失败条数
     */
    private Integer failureCount;
    
    /**
     * 总金额
     */
    private String totalAmount;
    
    /**
     * 逐行结果
     */
    private List<BatchInvoiceItemResult> items;
}