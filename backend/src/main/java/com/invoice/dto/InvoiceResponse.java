package com.invoice.dto;

import com.invoice.entity.Invoice;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
                invoice.getFilePath() != null,
                invoice.getFileName()
        );
    }
}
