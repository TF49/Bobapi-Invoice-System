package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.invoice.dto.SupplierSettlementResponse;
import com.invoice.entity.SupplierSettlement;
import com.invoice.entity.User;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.InvoiceMapper;
import com.invoice.mapper.SupplierSettlementMapper;
import com.invoice.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商结算服务。
 * 结算按通道（出票主体 sellerCompany × 票种 invoiceCategory）分类型记账，各通道独立统计已结 / 未结。
 */
@Service
public class SupplierSettlementService {

    private static final Set<String> ALLOWED_SELLER_COMPANIES = Set.of("DEFAULT", "TECH");
    private static final Set<String> ALLOWED_INVOICE_CATEGORIES = Set.of("NORMAL", "VAT_SPECIAL");

    private final SupplierSettlementMapper supplierSettlementMapper;
    private final UserMapper userMapper;
    private final InvoiceMapper invoiceMapper;

    public SupplierSettlementService(SupplierSettlementMapper supplierSettlementMapper,
                                     UserMapper userMapper,
                                     InvoiceMapper invoiceMapper) {
        this.supplierSettlementMapper = supplierSettlementMapper;
        this.userMapper = userMapper;
        this.invoiceMapper = invoiceMapper;
    }

    /**
     * 通道唯一键，例如 DEFAULT:NORMAL、TECH:VAT_SPECIAL。
     */
    public static String channelKey(String sellerCompany, String invoiceCategory) {
        return sellerCompany + ":" + invoiceCategory;
    }

    /**
     * 创建结算记录（支持幂等控制）。
     * 每笔结算必须归属一个通道；支持提前给供应商结款（预结），通道未结款项可为负数，后续该通道开票自动抵扣（不跨通道抵扣）。
     * 同时冗余存储 operatorName，保证用户删除后历史操作人信息不丢失。
     */
    @Transactional
    public SupplierSettlementResponse createSettlement(BigDecimal amount, String sellerCompany, String invoiceCategory,
                                                       String remark, Long operatorId, String idempotencyKey) {
        String normalizedSeller = normalizeChannelPart(sellerCompany);
        String normalizedCategory = normalizeChannelPart(invoiceCategory);
        if (!ALLOWED_SELLER_COMPANIES.contains(normalizedSeller) || !ALLOWED_INVOICE_CATEGORIES.contains(normalizedCategory)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "结算通道不合法，必须指定出票主体与票种");
        }
        String normalizedKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey.trim() : null;
        String normalizedRemark = remark == null ? null : remark.trim();
        if (normalizedKey != null) {
            SupplierSettlement existing = findByIdempotencyKey(normalizedKey);
            if (existing != null) {
                validateRepeatedSettlement(existing, amount, normalizedSeller, normalizedCategory, normalizedRemark, operatorId);
                return toResponse(existing, existing.getOperatorName());
            }
        }

        // 查询操作人名称（冗余写入，即使将来用户被删，历史记录操作人仍可查）
        User operator = userMapper.selectById(operatorId);
        String operatorName = operator != null ? operator.getUsername() : "未知";

        SupplierSettlement settlement = new SupplierSettlement();
        settlement.setSettlementAmount(amount);
        settlement.setSellerCompany(normalizedSeller);
        settlement.setInvoiceCategory(normalizedCategory);
        settlement.setRemark(normalizedRemark);
        settlement.setOperatorId(operatorId);
        settlement.setOperatorName(operatorName);
        settlement.setIdempotencyKey(normalizedKey);
        LocalDateTime now = LocalDateTime.now();
        settlement.setCreatedAt(now);
        settlement.setUpdatedAt(now);

        try {
            supplierSettlementMapper.insert(settlement);
        } catch (DuplicateKeyException e) {
            if (normalizedKey != null) {
                SupplierSettlement existing = findByIdempotencyKey(normalizedKey);
                if (existing != null) {
                    validateRepeatedSettlement(existing, amount, normalizedSeller, normalizedCategory, normalizedRemark, operatorId);
                    return toResponse(existing, existing.getOperatorName());
                }
            }
            throw e;
        }

        return toResponse(settlement, operatorName);
    }

    private static String normalizeChannelPart(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private SupplierSettlement findByIdempotencyKey(String key) {
        LambdaQueryWrapper<SupplierSettlement> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SupplierSettlement::getIdempotencyKey, key);
        return supplierSettlementMapper.selectOne(queryWrapper);
    }

    private void validateRepeatedSettlement(SupplierSettlement existing, BigDecimal amount,
                                             String sellerCompany, String invoiceCategory,
                                             String remark, Long operatorId) {
        boolean sameAmount = existing.getSettlementAmount() != null
                && existing.getSettlementAmount().compareTo(amount) == 0;
        boolean sameChannel = Objects.equals(existing.getSellerCompany(), sellerCompany)
                && Objects.equals(existing.getInvoiceCategory(), invoiceCategory);
        boolean sameRemark = Objects.equals(existing.getRemark(), remark);
        boolean sameOperator = Objects.equals(existing.getOperatorId(), operatorId);
        if (!sameAmount || !sameChannel || !sameRemark || !sameOperator) {
            throw new BusinessException(HttpStatus.CONFLICT, 40902,
                    "Idempotency-Key 已用于其他供应商结算");
        }
    }

    /**
     * 修复 #6：转换为 OffsetDateTime，序列化时携带时区信息（+08:00）
     */
    private SupplierSettlementResponse toResponse(SupplierSettlement s, String operatorName) {
        return new SupplierSettlementResponse(
                s.getId(),
                s.getSettlementAmount(),
                s.getSellerCompany(),
                s.getInvoiceCategory(),
                s.getRemark(),
                operatorName,
                toOffset(s.getCreatedAt()),
                s.getVoidedAt() != null,
                toOffset(s.getVoidedAt()),
                s.getVoidedByName(),
                s.getVoidReason()
        );
    }

    private static OffsetDateTime toOffset(LocalDateTime time) {
        return time != null ? time.atZone(ZoneId.systemDefault()).toOffsetDateTime() : null;
    }

    /**
     * 作废结算记录（用于选错通道 / 填错金额的纠错，作废后由管理员重新登记正确记录）。
     * 作废记录保留在历史中，但不再计入任何已结算汇总；已作废的记录不可再次作废。
     */
    @Transactional
    public SupplierSettlementResponse voidSettlement(Long id, String reason, Long operatorId) {
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "作废原因不能为空");
        }
        SupplierSettlement settlement = supplierSettlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40401, "结算记录不存在");
        }
        if (settlement.getVoidedAt() != null) {
            throw new BusinessException(HttpStatus.CONFLICT, 40903, "该结算记录已作废");
        }

        User operator = userMapper.selectById(operatorId);
        String operatorName = operator != null ? operator.getUsername() : "未知";
        LocalDateTime now = LocalDateTime.now();
        int updated = supplierSettlementMapper.markVoided(id, now, operatorId, operatorName, normalizedReason);
        if (updated == 0) {
            // 并发场景：读取后被其他请求抢先作废
            throw new BusinessException(HttpStatus.CONFLICT, 40903, "该结算记录已作废");
        }

        settlement.setVoidedAt(now);
        settlement.setVoidedBy(operatorId);
        settlement.setVoidedByName(operatorName);
        settlement.setVoidReason(normalizedReason);
        String creatorName = settlement.getOperatorName() != null ? settlement.getOperatorName() : "未知";
        return toResponse(settlement, creatorName);
    }

    /**
     * 获取结算历史记录列表。
     * 修复 #4：消除 N+1 查询——先批量拉取所有涉及的操作人，再内存组装；
     *          同时优先使用冗余字段 operatorName（兼容历史无冗余字段的旧数据）。
     */
    public List<SupplierSettlementResponse> getSettlementHistory() {
        LambdaQueryWrapper<SupplierSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SupplierSettlement::getCreatedAt);

        List<SupplierSettlement> settlements = supplierSettlementMapper.selectList(wrapper);
        if (settlements == null || settlements.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询缺少冗余字段的旧记录的操作人（operatorName 为 null 的记录）
        Set<Long> missingNameIds = settlements.stream()
                .filter(s -> s.getOperatorName() == null && s.getOperatorId() != null)
                .map(SupplierSettlement::getOperatorId)
                .collect(Collectors.toSet());

        Map<Long, String> idToName = missingNameIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(missingNameIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername));

        return settlements.stream().map(s -> {
            // 优先使用冗余字段，降级使用实时查询结果，最终兜底"未知"
            String name = s.getOperatorName() != null
                    ? s.getOperatorName()
                    : idToName.getOrDefault(s.getOperatorId(), "未知");
            return toResponse(s, name);
        }).collect(Collectors.toList());
    }

    /**
     * 获取总已结算金额（所有通道之和，不含作废记录）
     */
    public BigDecimal getTotalSettledAmount() {
        BigDecimal total = supplierSettlementMapper.selectTotalSettledAmount();
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * 按通道汇总已结算金额，key 为 {@link #channelKey(String, String)}。
     */
    public Map<String, BigDecimal> getSettledAmountByChannel() {
        List<SupplierSettlementMapper.ChannelSettledStat> stats = supplierSettlementMapper.selectSettledAmountByChannel();
        Map<String, BigDecimal> result = new HashMap<>();
        if (stats == null) {
            return result;
        }
        for (SupplierSettlementMapper.ChannelSettledStat stat : stats) {
            if (stat == null || stat.sellerCompany() == null || stat.invoiceCategory() == null) {
                continue;
            }
            BigDecimal amount = stat.amount() != null ? stat.amount() : BigDecimal.ZERO;
            result.merge(channelKey(normalizeChannelPart(stat.sellerCompany()), normalizeChannelPart(stat.invoiceCategory())),
                    amount, BigDecimal::add);
        }
        return result;
    }

    /**
     * 获取当前未结款项（累计已开票金额 - 累计已结算金额）。
     * 支持负数（提前给供应商结款时为负数，后续开票自动抵扣）。
     */
    public BigDecimal getTotalUnsettledAmount() {
        InvoiceMapper.OverallStat overallStat = invoiceMapper.selectOverallStat();
        BigDecimal totalAmount = overallStat != null && overallStat.totalAmount() != null
                ? overallStat.totalAmount()
                : BigDecimal.ZERO;
        BigDecimal totalSettled = getTotalSettledAmount();
        return totalAmount.subtract(totalSettled);
    }
}
