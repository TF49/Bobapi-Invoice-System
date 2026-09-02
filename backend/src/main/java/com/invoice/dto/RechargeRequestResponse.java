package com.invoice.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值申请响应
 */
@Data
public class RechargeRequestResponse {
    
    private Long id;
    private Long userId;
    private String username;
    private BigDecimal feeAmount;
    private BigDecimal amount;
    private String screenshotUrl;
    private String status;
    private String remark;
    private String adminRemark;
    private Long reviewedBy;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
