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
        String status,
        Long userId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean downloadable,
        boolean fileExists,
        String fileName
) {
    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getCompanyName(),
                invoice.getTaxNumber(),
                invoice.getAmount(),
                invoice.getStatus(),
                invoice.getUserId(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt(),
                false,
                false,
                invoice.getFileName()
        );
    }

    public static InvoiceResponse from(Invoice invoice, Path uploadRoot) {
        boolean exists = false;
        if (invoice.getFilePath() != null && uploadRoot != null) {
            Path normalizedRoot = uploadRoot.toAbsolutePath().normalize();
            Path resolved = normalizedRoot.resolve(invoice.getFilePath()).normalize();
            exists = resolved.startsWith(normalizedRoot) && Files.isRegularFile(resolved);
        }
        boolean downloadable = exists && isSupportedImage(invoice.getFileName());
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getCompanyName(),
                invoice.getTaxNumber(),
                invoice.getAmount(),
                invoice.getStatus(),
                invoice.getUserId(),
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
