package com.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OpenAPI 盖章文件响应。
 * 不返回内部用户 ID、处理人信息和盖章额度余额。
 */
public record OpenSealDocumentResponse(
        Long id,
        String sourceFileName,
        Long sourceFileSize,
        String sourceFileExt,
        BigDecimal feeAmount,
        String status,
        String rejectReason,
        boolean downloadable,
        String sealedFileName,
        Long sealedFileSize,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime updatedAt
) {
    public static OpenSealDocumentResponse from(SealDocumentResponse doc) {
        boolean downloadable = "COMPLETED".equals(doc.getStatus())
                && doc.getSealedFileName() != null && !doc.getSealedFileName().isBlank();
        return new OpenSealDocumentResponse(
                doc.getId(),
                doc.getSourceFileName(),
                doc.getSourceFileSize(),
                doc.getSourceFileExt(),
                doc.getFeeAmount(),
                doc.getStatus(),
                doc.getRejectReason(),
                downloadable,
                doc.getSealedFileName(),
                doc.getSealedFileSize(),
                doc.getCreatedAt(),
                doc.getCompletedAt(),
                doc.getUpdatedAt()
        );
    }
}
