package com.invoice.dto;

import lombok.Data;

/**
 * 批量发票申请单项请求 DTO
 */
@Data
public class BatchInvoiceItemRequest {

    /**
     * 客户端原始行号。网页端批量提交从 1 开始；Excel 导入可传入包含表头偏移的真实行号。
     */
    private Integer rowNumber;

    private String companyName;

    private String taxNumber;

    /**
     * 保留客户端的十进制文本，服务层按行校验并转换为 BigDecimal。
     */
    private String amount;

    /**
     * 开票类型
     */
    private String invoiceType;

    /**
     * 发票票种：NORMAL-普票（默认），VAT_SPECIAL-专票
     */
    private String invoiceCategory = "NORMAL";

    /**
     * 备注（可选）
     */
    private String remark;
}
