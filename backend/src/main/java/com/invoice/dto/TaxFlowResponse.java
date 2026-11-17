package com.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 税号数据流：按税号汇总的发票流转时间线
 */
public record TaxFlowResponse(
        String taxNumber,
        List<String> companyNames,
        Summary summary,
        int page,
        int size,
        long total,
        List<InvoiceFlow> invoices
) {

    public record Summary(
            long invoiceCount,
            BigDecimal totalAmount,
            long pendingCount,
            long completedCount,
            long cancelledCount,
            long redFlushedCount,
            BigDecimal activeDeducted
    ) {}

    public record InvoiceFlow(
            Long id,
            Long userId,
            String username,
            String companyName,
            BigDecimal amount,
            String invoiceType,
            String invoiceCategory,
            String sellerCompany,
            String taxRate,
            String status,
            String redFlushStatus,
            String submissionType,
            Long batchId,
            String outTradeNo,
            BigDecimal deductedQuota,
            LocalDateTime createdAt,
            List<Event> events
    ) {}

    public record Event(
            Long id,
            String eventType,
            String operatorType,
            BigDecimal quotaDelta,
            BigDecimal balanceAfter,
            Long quotaTransactionId,
            String detail,
            LocalDateTime createdAt
    ) {}

    public record Suggestion(
            String taxNumber,
            String companyName,
            long invoiceCount
    ) {}
}
