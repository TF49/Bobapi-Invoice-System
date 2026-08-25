package com.invoice.dto;

import com.invoice.entity.Invoice;

import java.math.BigDecimal;
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
        String remark,
        String status,
        boolean isProcessed,
        Long userId,
        String username,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean downloadable,
        boolean fileExists,
        String fileName
) {
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
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getCompanyName(),
                invoice.getTaxNumber(),
                invoice.getAmount(),
                invoice.getInvoiceType(),
                invoice.getRemark(),
                invoice.getStatus(),
                isProcessed,
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
