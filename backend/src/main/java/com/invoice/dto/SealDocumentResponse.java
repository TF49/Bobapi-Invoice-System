package com.invoice.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 盖章文件响应
 */
@Data
public class SealDocumentResponse {
    
    private Long id;
    private Long userId;
    private String username;
    private String sourceFileName;
    private Long sourceFileSize;
    private String sourceFileExt;
    private String sealedFileName;
    private Long sealedFileSize;
    private BigDecimal feeAmount;
    private String status;
    private String rejectReason;
    private Long operatorId;
    private String operatorName;
    private Boolean isProcessed;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
