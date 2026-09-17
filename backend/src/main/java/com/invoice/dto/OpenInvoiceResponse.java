package com.invoice.dto;

import com.invoice.entity.Invoice;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;

public record OpenInvoiceResponse(
        Long id,
        String outTradeNo,
        String companyName,
        String taxNumber,
        BigDecimal amount,
        String invoiceType,
        String invoiceCategory,
        String remark,
        String status,
        String submissionType,
        String redFlushStatus,
        String redFlushReason,
        String redFlushRemark,
        LocalDateTime redFlushApplyTime,
        LocalDateTime redFlushCompleteTime,
        boolean downloadable,
        String fileName,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
    public static OpenInvoiceResponse from(Invoice invoice) {
        return from(invoice, null);
    }

    public static OpenInvoiceResponse from(Invoice invoice, Path uploadRoot) {
        boolean exists = false;
        if (invoice.getFilePath() != null && uploadRoot != null) {
            Path normalizedRoot = uploadRoot.toAbsolutePath().normalize();
            Path resolved = normalizedRoot.resolve(invoice.getFilePath()).normalize();
            exists = resolved.startsWith(normalizedRoot) && Files.isRegularFile(resolved);
        }
        boolean downloadable = exists && isSupportedImage(invoice.getFileName());
        String submissionType = invoice.getSubmissionType() != null ? invoice.getSubmissionType() : "UNKNOWN";
        String redFlushStatus = invoice.getRedFlushStatus() != null ? invoice.getRedFlushStatus() : "NONE";
        String invoiceCategory = invoice.getInvoiceCategory() != null ? invoice.getInvoiceCategory() : "NORMAL";
        return new OpenInvoiceResponse(
                invoice.getId(),
                invoice.getOutTradeNo(),
                invoice.getCompanyName(),
                invoice.getTaxNumber(),
                invoice.getAmount(),
                invoice.getInvoiceType(),
                invoiceCategory,
                invoice.getRemark(),
                invoice.getStatus(),
                submissionType,
                redFlushStatus,
                invoice.getRedFlushReason(),
                invoice.getRedFlushRemark(),
                invoice.getRedFlushApplyTime(),
                invoice.getRedFlushCompleteTime(),
                downloadable,
                invoice.getFileName(),
                invoice.getCreatedAt(),
                invoice.getCompletedAt()
        );
    }

    private static boolean isSupportedImage(String fileName) {
        if (fileName == null) {
            return false;
        }
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".jpg") || normalized.endsWith(".jpeg") || normalized.endsWith(".png");
    }
}
