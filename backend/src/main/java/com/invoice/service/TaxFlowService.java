package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.invoice.dto.TaxFlowResponse;
import com.invoice.entity.Invoice;
import com.invoice.entity.InvoiceEvent;
import com.invoice.entity.User;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.InvoiceEventMapper;
import com.invoice.mapper.InvoiceMapper;
import com.invoice.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 税号数据流查询：税号候选匹配 + 按税号聚合的发票流转时间线。
 * userId 为 null 表示管理员跨用户查询；普通用户调用时必须传入本人 userId。
 */
@Service
public class TaxFlowService {

    public static final int MIN_QUERY_LENGTH = 4;
    public static final int MAX_QUERY_LENGTH = 100;
    static final int SUGGEST_LIMIT = 10;
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;
    static final int MAX_COMPANY_NAMES = 5;

    private final InvoiceMapper invoiceMapper;
    private final InvoiceEventMapper invoiceEventMapper;
    private final UserMapper userMapper;

    public TaxFlowService(InvoiceMapper invoiceMapper, InvoiceEventMapper invoiceEventMapper, UserMapper userMapper) {
        this.invoiceMapper = invoiceMapper;
        this.invoiceEventMapper = invoiceEventMapper;
        this.userMapper = userMapper;
    }

    public List<TaxFlowResponse.Suggestion> suggest(String query, Long userId) {
        String normalized = normalize(query);
        if (normalized == null || normalized.length() < MIN_QUERY_LENGTH) {
            return Collections.emptyList();
        }
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "税号最多100个字符");
        }
        return invoiceMapper.selectTaxNumberSuggestions(escapeLike(normalized), userId, SUGGEST_LIMIT).stream()
                .map(row -> new TaxFlowResponse.Suggestion(
                        row.getTaxNumber(),
                        row.getCompanyName(),
                        row.getInvoiceCount() == null ? 0 : row.getInvoiceCount()))
                .toList();
    }

    /**
     * @param includeOwner   是否返回发票所属用户名（管理员跨用户查询时使用）
     * @param includeBalance 是否返回额度变动后余额（开票员不可见用户余额）
     */
    public TaxFlowResponse getFlow(String taxNumber, Long userId, Integer page, Integer size,
                                   boolean includeOwner, boolean includeBalance) {
        String normalized = normalize(taxNumber);
        if (normalized == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "税号不能为空");
        }
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "税号最多100个字符");
        }
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        InvoiceMapper.TaxFlowSummaryRow row = invoiceMapper.selectTaxFlowSummary(normalized, userId);
        TaxFlowResponse.Summary summary = toSummary(row);
        if (summary.invoiceCount() == 0) {
            return new TaxFlowResponse(normalized, List.of(), summary, safePage, safeSize, 0, List.of());
        }

        LambdaQueryWrapper<Invoice> invoiceQuery = new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getTaxNumber, normalized)
                .eq(userId != null, Invoice::getUserId, userId)
                .orderByDesc(Invoice::getCreatedAt)
                .orderByDesc(Invoice::getId)
                .last("LIMIT " + ((long) (safePage - 1) * safeSize) + ", " + safeSize);
        List<Invoice> invoices = invoiceMapper.selectList(invoiceQuery);

        Map<Long, List<InvoiceEvent>> eventsByInvoice = loadEvents(invoices);
        Map<Long, String> usernames = includeOwner ? loadUsernames(invoices) : Map.of();

        List<TaxFlowResponse.InvoiceFlow> flows = invoices.stream()
                .map(invoice -> toFlow(invoice,
                        eventsByInvoice.getOrDefault(invoice.getId(), List.of()),
                        includeOwner ? usernames.get(invoice.getUserId()) : null,
                        includeOwner,
                        includeBalance))
                .toList();

        return new TaxFlowResponse(normalized, loadCompanyNames(normalized, userId), summary,
                safePage, safeSize, summary.invoiceCount(), flows);
    }

    private Map<Long, List<InvoiceEvent>> loadEvents(List<Invoice> invoices) {
        if (invoices.isEmpty()) {
            return Map.of();
        }
        List<Long> invoiceIds = invoices.stream().map(Invoice::getId).toList();
        List<InvoiceEvent> events = invoiceEventMapper.selectList(new LambdaQueryWrapper<InvoiceEvent>()
                .in(InvoiceEvent::getInvoiceId, invoiceIds)
                .orderByAsc(InvoiceEvent::getCreatedAt)
                .orderByAsc(InvoiceEvent::getId));
        return events.stream().collect(Collectors.groupingBy(InvoiceEvent::getInvoiceId));
    }

    private Map<Long, String> loadUsernames(List<Invoice> invoices) {
        List<Long> userIds = invoices.stream().map(Invoice::getUserId).filter(Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    private List<String> loadCompanyNames(String taxNumber, Long userId) {
        QueryWrapper<Invoice> query = new QueryWrapper<Invoice>()
                .select("DISTINCT company_name")
                .eq("tax_number", taxNumber)
                .eq(userId != null, "user_id", userId)
                .last("LIMIT " + MAX_COMPANY_NAMES);
        return invoiceMapper.selectObjs(query).stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();
    }

    private TaxFlowResponse.InvoiceFlow toFlow(Invoice invoice, List<InvoiceEvent> events, String username,
                                               boolean includeOwner, boolean includeBalance) {
        Function<InvoiceEvent, TaxFlowResponse.Event> mapper = event -> new TaxFlowResponse.Event(
                event.getId(),
                event.getEventType(),
                event.getOperatorType(),
                event.getQuotaDelta(),
                includeBalance ? event.getBalanceAfter() : null,
                event.getQuotaTransactionId(),
                event.getDetail(),
                event.getCreatedAt());
        return new TaxFlowResponse.InvoiceFlow(
                invoice.getId(),
                includeOwner ? invoice.getUserId() : null,
                username,
                invoice.getCompanyName(),
                invoice.getAmount(),
                invoice.getInvoiceType(),
                invoice.getInvoiceCategory(),
                invoice.getSellerCompany(),
                invoice.getTaxRate(),
                invoice.getStatus(),
                invoice.getRedFlushStatus(),
                invoice.getSubmissionType(),
                invoice.getBatchId(),
                invoice.getOutTradeNo(),
                invoice.getDeductedQuota(),
                invoice.getCreatedAt(),
                events.stream().map(mapper).toList());
    }

    private static TaxFlowResponse.Summary toSummary(InvoiceMapper.TaxFlowSummaryRow row) {
        if (row == null) {
            return new TaxFlowResponse.Summary(0, BigDecimal.ZERO, 0, 0, 0, 0, BigDecimal.ZERO);
        }
        return new TaxFlowResponse.Summary(
                orZero(row.getInvoiceCount()),
                row.getTotalAmount() == null ? BigDecimal.ZERO : row.getTotalAmount(),
                orZero(row.getPendingCount()),
                orZero(row.getCompletedCount()),
                orZero(row.getCancelledCount()),
                orZero(row.getRedFlushedCount()),
                row.getActiveDeducted() == null ? BigDecimal.ZERO : row.getActiveDeducted());
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }

    static String normalize(String taxNumber) {
        if (taxNumber == null) {
            return null;
        }
        String trimmed = taxNumber.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    /** 转义 MySQL LIKE 通配符（默认转义符为反斜杠） */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
