package com.invoice.service;

import com.invoice.entity.Invoice;
import com.invoice.entity.InvoiceEvent;
import com.invoice.entity.UserQuotaTransaction;
import com.invoice.mapper.InvoiceEventMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发票流转事件记录：在发票状态变更的同一事务中写入，作为税号数据流时间线的数据来源。
 */
@Service
public class InvoiceEventService {

    static final int MAX_DETAIL_LENGTH = 500;

    private final InvoiceEventMapper invoiceEventMapper;

    public InvoiceEventService(InvoiceEventMapper invoiceEventMapper) {
        this.invoiceEventMapper = invoiceEventMapper;
    }

    public void record(Invoice invoice, String eventType, String operatorType, Long operatorId, String detail) {
        record(invoice, eventType, operatorType, operatorId, null, null, detail);
    }

    /**
     * @param quotaDelta  本事件对该发票的额度影响（负数扣除，正数退还），无额度变化传 null
     * @param transaction 关联的额度流水，可为 null；非空时记录流水号与变动后余额
     */
    public void record(Invoice invoice, String eventType, String operatorType, Long operatorId,
                       BigDecimal quotaDelta, UserQuotaTransaction transaction, String detail) {
        InvoiceEvent event = build(invoice, eventType, operatorType, operatorId, quotaDelta, detail);
        if (transaction != null) {
            event.setQuotaTransactionId(transaction.getId());
            event.setBalanceAfter(transaction.getBalanceAfter());
        }
        invoiceEventMapper.insert(event);
    }

    public void recordAll(List<InvoiceEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        invoiceEventMapper.insertBatch(events);
    }

    public static InvoiceEvent build(Invoice invoice, String eventType, String operatorType, Long operatorId,
                                     BigDecimal quotaDelta, String detail) {
        InvoiceEvent event = new InvoiceEvent();
        event.setInvoiceId(invoice.getId());
        event.setUserId(invoice.getUserId());
        event.setEventType(eventType);
        event.setOperatorType(operatorType != null ? operatorType : "SYSTEM");
        event.setOperatorId(operatorId);
        event.setQuotaDelta(quotaDelta);
        event.setDetail(truncate(detail));
        event.setCreatedAt(LocalDateTime.now());
        return event;
    }

    private static String truncate(String detail) {
        if (detail == null || detail.length() <= MAX_DETAIL_LENGTH) {
            return detail;
        }
        return detail.substring(0, MAX_DETAIL_LENGTH - 1) + "…";
    }
}
