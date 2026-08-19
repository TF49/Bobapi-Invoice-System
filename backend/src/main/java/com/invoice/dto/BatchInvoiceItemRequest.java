package com.invoice.dto;

import lombok.Data;

/**
 * 批量发票申请单项请求 DTO
 */
@Data
public class BatchInvoiceItemRequest {

    /**
     * 源文件中的原始行号。未提供时按请求顺序从第 2 行推导。
     */
    private Integer rowNumber;

    private String companyName;

    private String taxNumber;

    /**
     * 保留客户端的十进制文本，服务层按行校验并转换为 BigDecimal。
     */
    private String amount;
}
