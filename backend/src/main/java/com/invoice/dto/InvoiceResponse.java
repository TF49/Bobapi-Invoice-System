package com.invoice.dto;

import com.invoice.entity.Invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;

public record InvoiceResponse(
        Long id,
        String companyName,
        String taxNumber,
        BigDecimal amount,
        String invoiceType,
        String invoiceCategory,
        String sellerCompany,
        BigDecimal feeRate,
        BigDecimal deductedQuota,
        String remark,
        String status,
        boolean isProcessed,
        String redFlushStatus,
        String redFlushReason,
        String redFlushRemark,
        LocalDateTime redFlushApplyTime,
        LocalDateTime redFlushCompleteTime,
        Long redFlushOperatorId,
        String submissionType,
        Long userId,
        String username,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean downloadable,
        boolean fileExists,
        String fileName
) {
    /**
     * 兼容旧版全参构造方法（23 参）
     */
    public InvoiceResponse(
            Long id,
            String companyName,
            String taxNumber,
            BigDecimal amount,
            String invoiceType,
            String invoiceCategory,
            String remark,
            String status,
            boolean isProcessed,
            String redFlushStatus,
            String redFlushReason,
            String redFlushRemark,
            LocalDateTime redFlushApplyTime,
            LocalDateTime redFlushCompleteTime,
            Long redFlushOperatorId,
            String submissionType,
            Long userId,
            String username,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean downloadable,
            boolean fileExists,
            String fileName
    ) {
        this(
                id,
                companyName,
                taxNumber,
                amount,
                invoiceType,
                invoiceCategory,
                "DEFAULT",
                "VAT_SPECIAL".equals(invoiceCategory) ? new BigDecimal("0.0900") : new BigDecimal("0.0300"),
                "VAT_SPECIAL".equals(invoiceCategory) ? (amount != null ? amount.multiply(new BigDecimal("3")) : null) : amount,
                remark,
                status,
                isProcessed,
                redFlushStatus,
                redFlushReason,
                redFlushRemark,
                redFlushApplyTime,
                redFlushCompleteTime,
                redFlushOperatorId,
                submissionType,
                userId,
                username,
                createdAt,
                updatedAt,
                downloadable,
                fileExists,
                fileName
        );
    }

    public static InvoiceResponse from(Invoice invoice) {
        return from(invoice, null, null);
    }

    public static InvoiceResponse from(Invoice invoice, Path uploadRoot) {
        return from(invoice, uploadRoot, null);
    }

    public static InvoiceResponse from(Invoice invoice, Path uploadRoot, String username) {
        boolean exists = false;
        if (invoice.getFilePath() != null && uploadRoot != null) {
            Path normalizedRoot = uploadRoot.toAbsolutePath().normalize();
            Path resolved = normalizedRoot.resolve(invoice.getFilePath()).normalize();
            exists = resolved.startsWith(normalizedRoot) && Files.isRegularFile(resolved);
        }
        boolean downloadable = exists && isSupportedImage(invoice.getFileName());
        boolean isProcessed = invoice.getIsProcessed() != null && invoice.getIsProcessed();
        String redFlushStatus = invoice.getRedFlushStatus() != null ? invoice.getRedFlushStatus() : "NONE";
        String submissionType = invoice.getSubmissionType() != null ? invoice.getSubmissionType() : "UNKNOWN";
        String invoiceCategory = invoice.getInvoiceCategory() != null ? invoice.getInvoiceCategory() : "NORMAL";
        String sellerCompany = invoice.getSellerCompany() != null ? invoice.getSellerCompany() : "DEFAULT";
        boolean isTech = "TECH".equalsIgnoreCase(sellerCompany);
        boolean isVatSpecial = "VAT_SPECIAL".equals(invoiceCategory);
        BigDecimal feeRate = invoice.getFeeRate() != null
                ? invoice.getFeeRate()
                : (isTech
                        ? (isVatSpecial ? new BigDecimal("0.1000") : new BigDecimal("0.0400"))
                        : (isVatSpecial ? new BigDecimal("0.0900") : new BigDecimal("0.0300")));
        BigDecimal deductedQuota = invoice.getDeductedQuota() != null
                ? invoice.getDeductedQuota()
                : (invoice.getAmount() != null
                        ? (isTech
                                ? (isVatSpecial
                                        ? invoice.getAmount().multiply(new BigDecimal("10")).divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP)
                                        : invoice.getAmount().multiply(new BigDecimal("4")).divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP))
                                : (isVatSpecial
                                        ? invoice.getAmount().multiply(new BigDecimal("3"))
                                        : invoice.getAmount()))
                        : null);
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getCompanyName(),
                invoice.getTaxNumber(),
                invoice.getAmount(),
                invoice.getInvoiceType(),
                invoiceCategory,
                sellerCompany,
                feeRate,
                deductedQuota,
                invoice.getRemark(),
                invoice.getStatus(),
                isProcessed,
                redFlushStatus,
                invoice.getRedFlushReason(),
                invoice.getRedFlushRemark(),
                invoice.getRedFlushApplyTime(),
                invoice.getRedFlushCompleteTime(),
                invoice.getRedFlushOperatorId(),
                submissionType,
                invoice.getUserId(),
                username,
                invoice.getCreatedAt(),
                invoice.getUpdatedAt(),
                downloadable,
                exists,
                invoice.getFileName()
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
