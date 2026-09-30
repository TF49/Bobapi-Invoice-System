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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商结算服务
 */
@Service
public class SupplierSettlementService {

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
     * 创建结算记录。
     * 支持提前给供应商结款（预结）：结算金额可大于当前未结款项，差额计为负数并在后续开票中自动抵扣。
     * 修复 #5：同时冗余存储 operatorName，保证用户删除后历史操作人信息不丢失。
     */
    @Transactional
    public SupplierSettlementResponse createSettlement(BigDecimal amount, String remark, Long operatorId) {
        return createSettlement(amount, remark, operatorId, null);
    }

    /**
     * 创建结算记录（支持幂等控制）。
     * 支持提前给供应商结款（预结）：结算金额可大于当前未结款项，差额计为负数并在后续开票中自动抵扣。
     * 同时冗余存储 operatorName，保证用户删除后历史操作人信息不丢失。
     */
    @Transactional
    public SupplierSettlementResponse createSettlement(BigDecimal amount, String remark, Long operatorId, String idempotencyKey) {
        String normalizedKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey.trim() : null;
        String normalizedRemark = remark == null ? null : remark.trim();
        if (normalizedKey != null) {
            LambdaQueryWrapper<SupplierSettlement> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SupplierSettlement::getIdempotencyKey, normalizedKey);
            SupplierSettlement existing = supplierSettlementMapper.selectOne(queryWrapper);
            if (existing != null) {
                validateRepeatedSettlement(existing, amount, normalizedRemark, operatorId);
                OffsetDateTime createdAtWithZone = existing.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime();
                return new SupplierSettlementResponse(
                        existing.getId(),
                        existing.getSettlementAmount(),
                        existing.getRemark(),
                        existing.getOperatorName(),
                        createdAtWithZone
                );
            }
        }

        // 查询操作人名称（冗余写入，即使将来用户被删，历史记录操作人仍可查）
        User operator = userMapper.selectById(operatorId);
        String operatorName = operator != null ? operator.getUsername() : "未知";

        SupplierSettlement settlement = new SupplierSettlement();
        settlement.setSettlementAmount(amount);
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
                LambdaQueryWrapper<SupplierSettlement> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(SupplierSettlement::getIdempotencyKey, normalizedKey);
                SupplierSettlement existing = supplierSettlementMapper.selectOne(queryWrapper);
                if (existing != null) {
                    validateRepeatedSettlement(existing, amount, normalizedRemark, operatorId);
                    OffsetDateTime createdAtWithZone = existing.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime();
                    return new SupplierSettlementResponse(
                            existing.getId(),
                            existing.getSettlementAmount(),
                            existing.getRemark(),
                            existing.getOperatorName(),
                            createdAtWithZone
                    );
                }
            }
            throw e;
        }

        // 修复 #6：转换为 OffsetDateTime，序列化时携带时区信息（+08:00）
        OffsetDateTime createdAtWithZone = now.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        return new SupplierSettlementResponse(
                settlement.getId(),
                settlement.getSettlementAmount(),
                settlement.getRemark(),
                operatorName,
                createdAtWithZone
        );
    }

    private void validateRepeatedSettlement(SupplierSettlement existing, BigDecimal amount,
                                             String remark, Long operatorId) {
        boolean sameAmount = existing.getSettlementAmount() != null
                && existing.getSettlementAmount().compareTo(amount) == 0;
        boolean sameRemark = java.util.Objects.equals(existing.getRemark(), remark);
        boolean sameOperator = java.util.Objects.equals(existing.getOperatorId(), operatorId);
        if (!sameAmount || !sameRemark || !sameOperator) {
            throw new BusinessException(HttpStatus.CONFLICT, 40902,
                    "Idempotency-Key 已用于其他供应商结算");
        }
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
            // 修复 #6：转换为 OffsetDateTime，序列化时携带时区信息（+08:00）
            OffsetDateTime createdAtWithZone = s.getCreatedAt() != null
                    ? s.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime()
                    : null;
            return new SupplierSettlementResponse(
                    s.getId(),
                    s.getSettlementAmount(),
                    s.getRemark(),
                    name,
                    createdAtWithZone
            );
        }).collect(Collectors.toList());
    }

    /**
     * 获取总已结算金额
     */
    public BigDecimal getTotalSettledAmount() {
        BigDecimal total = supplierSettlementMapper.selectTotalSettledAmount();
        return total != null ? total : BigDecimal.ZERO;
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

