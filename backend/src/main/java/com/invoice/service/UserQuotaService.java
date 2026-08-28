package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.invoice.entity.UserQuota;
import com.invoice.entity.UserQuotaTransaction;
import com.invoice.exception.BusinessException;
import com.invoice.entity.User;
import com.invoice.mapper.UserMapper;
import com.invoice.mapper.UserQuotaMapper;
import com.invoice.mapper.UserQuotaTransactionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 用户额度服务
 */
@Service
public class UserQuotaService {

    private final UserQuotaMapper userQuotaMapper;
    private final UserQuotaTransactionMapper userQuotaTransactionMapper;
    private final UserMapper userMapper;

    private static final BigDecimal MIN_RECHARGE_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_RECHARGE_AMOUNT = new BigDecimal("999999.99");

    public UserQuotaService(UserQuotaMapper userQuotaMapper,
                           UserQuotaTransactionMapper userQuotaTransactionMapper,
                           UserMapper userMapper) {
        this.userQuotaMapper = userQuotaMapper;
        this.userQuotaTransactionMapper = userQuotaTransactionMapper;
        this.userMapper = userMapper;
    }

    /**
     * 获取用户当前额度
     */
    /**
     * 获取用户当前额度
     */
    public UserQuota getUserQuota(Long userId) {
        UserQuota quota = userQuotaMapper.selectOne(
            new LambdaQueryWrapper<UserQuota>()
                .eq(UserQuota::getUserId, userId)
        );
        
        if (quota == null) {
            // 如果用户没有额度记录，创建初始记录
            quota = createInitialQuota(userId);
        } else {
            quota = syncQuotaFromTransactions(userId, quota);
        }
        
        return quota;
    }

    /**
     * 批量获取多个用户的额度（用于列表页展示，避免 N+1 查询）
     * 自动为缺失额度记录的用户补全初始记录，并同步历史交易数据。
     */
    public List<UserQuota> getQuotasByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<UserQuota> existingQuotas = userQuotaMapper.selectList(
            new LambdaQueryWrapper<UserQuota>()
                .in(UserQuota::getUserId, userIds)
        );

        java.util.Map<Long, UserQuota> quotaMap = new java.util.HashMap<>();
        for (UserQuota q : existingQuotas) {
            if (q != null && q.getUserId() != null) {
                quotaMap.merge(q.getUserId(), q, (q1, q2) ->
                    (q2.getId() != null && q1.getId() != null && q2.getId() > q1.getId()) ? q2 : q1
                );
            }
        }

        List<UserQuota> result = new java.util.ArrayList<>();
        for (Long userId : userIds) {
            UserQuota quota = quotaMap.get(userId);
            if (quota == null) {
                try {
                    quota = createInitialQuota(userId);
                } catch (DuplicateKeyException exception) {
                    quota = userQuotaMapper.selectOne(
                        new LambdaQueryWrapper<UserQuota>()
                            .eq(UserQuota::getUserId, userId)
                    );
                }
            } else {
                quota = syncQuotaFromTransactions(userId, quota);
            }
            if (quota != null) {
                result.add(quota);
            }
        }
        return result;
    }

    /**
     * 为新用户创建初始额度记录
     */
    @Transactional
    public UserQuota createInitialQuota(Long userId) {
        UserQuota quota = new UserQuota();
        quota.setUserId(userId);
        quota.setBalance(BigDecimal.ZERO);
        quota.setTotalRecharged(BigDecimal.ZERO);
        quota.setTotalDeducted(BigDecimal.ZERO);
        quota.setCreatedAt(LocalDateTime.now());
        quota.setUpdatedAt(LocalDateTime.now());
        
        userQuotaMapper.insert(quota);
        return syncQuotaFromTransactions(userId, quota);
    }

    /**
     * 检查并根据交易历史恢复/同步额度信息（容错防不一致）
     */
    private UserQuota syncQuotaFromTransactions(Long userId, UserQuota quota) {
        if (userId == null || quota == null) {
            return quota;
        }
        List<UserQuotaTransaction> transactions = userQuotaTransactionMapper.selectList(
            new LambdaQueryWrapper<UserQuotaTransaction>()
                .eq(UserQuotaTransaction::getUserId, userId)
                .orderByAsc(UserQuotaTransaction::getId)
        );
        if (transactions == null || transactions.isEmpty()) {
            return quota;
        }

        BigDecimal totalRecharged = BigDecimal.ZERO;
        BigDecimal totalDeducted = BigDecimal.ZERO;

        for (UserQuotaTransaction tx : transactions) {
            if (tx.getAmount() == null) continue;
            String type = tx.getTransactionType();
            BigDecimal amount = tx.getAmount();
            if ("RECHARGE".equals(type)) {
                totalRecharged = totalRecharged.add(amount);
            } else if ("DEDUCT".equals(type)) {
                totalDeducted = totalDeducted.add(amount.abs());
            } else if ("ADJUST".equals(type)) {
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    totalRecharged = totalRecharged.add(amount);
                } else {
                    totalDeducted = totalDeducted.add(amount.abs());
                }
            }
        }

        UserQuotaTransaction latestTx = transactions.get(transactions.size() - 1);
        BigDecimal expectedBalance = latestTx.getBalanceAfter() != null
                ? latestTx.getBalanceAfter()
                : totalRecharged.subtract(totalDeducted);

        boolean updated = false;
        if (quota.getBalance() == null || quota.getBalance().compareTo(expectedBalance) != 0) {
            quota.setBalance(expectedBalance);
            updated = true;
        }
        if (quota.getTotalRecharged() == null || quota.getTotalRecharged().compareTo(totalRecharged) != 0) {
            quota.setTotalRecharged(totalRecharged);
            updated = true;
        }
        if (quota.getTotalDeducted() == null || quota.getTotalDeducted().compareTo(totalDeducted) != 0) {
            quota.setTotalDeducted(totalDeducted);
            updated = true;
        }

        if (updated) {
            quota.setUpdatedAt(LocalDateTime.now());
            userQuotaMapper.updateById(quota);
        }

        return quota;
    }

    /**
     * 充值额度（管理员操作）
     */
    @Transactional
    public UserQuota rechargeQuota(Long userId, BigDecimal amount, Long operatorId, String remark,
                                   String idempotencyKey) {
        validateRechargeAmount(amount);

        requireUserRoleForUpdate(userId);
        UserQuota quota = getUserQuotaWithLock(userId);
        UserQuotaTransaction existing = findByIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            validateRepeatedOperation(existing, "RECHARGE", amount, remark);
            return quota;
        }
        BigDecimal balanceBefore = quota.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);
        
        // 更新额度
        quota.setBalance(balanceAfter);
        quota.setTotalRecharged(quota.getTotalRecharged().add(amount));
        quota.setUpdatedAt(LocalDateTime.now());
        userQuotaMapper.updateById(quota);
        
        // 记录交易历史
        createTransaction(userId, "RECHARGE", amount, balanceBefore, balanceAfter,
                         operatorId, "ADMIN", null, remark, idempotencyKey);
        return quota;
    }

    /**
     * 扣除额度（开票时调用）
     */
    @Transactional
    public void deductQuota(Long userId, BigDecimal amount, Long invoiceId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "扣除金额必须大于0");
        }

        requireUserRoleForUpdate(userId);
        UserQuota quota = getUserQuotaWithLock(userId);
        BigDecimal balanceBefore = quota.getBalance();
        
        if (balanceBefore.compareTo(amount) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40002, "额度不足，当前余额：" + balanceBefore + "，需要：" + amount);
        }
        
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        
        // 更新额度
        quota.setBalance(balanceAfter);
        quota.setTotalDeducted(quota.getTotalDeducted().add(amount));
        quota.setUpdatedAt(LocalDateTime.now());
        userQuotaMapper.updateById(quota);
        
        // 记录交易历史
        createTransaction(userId, "DEDUCT", amount.negate(), balanceBefore, balanceAfter,
                         null, "SYSTEM", invoiceId, "开票扣除", null);
    }

    /**
     * 批量扣除额度（批量开票时调用）
     */
    @Transactional
    public void deductBatchQuota(Long userId, BigDecimal totalAmount, Long batchId) {
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "扣除金额必须大于0");
        }

        requireUserRoleForUpdate(userId);
        UserQuota quota = getUserQuotaWithLock(userId);
        BigDecimal balanceBefore = quota.getBalance();

        if (balanceBefore.compareTo(totalAmount) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40002, "额度不足，当前余额：" + balanceBefore + "，需要：" + totalAmount);
        }

        BigDecimal balanceAfter = balanceBefore.subtract(totalAmount);

        // 更新额度
        quota.setBalance(balanceAfter);
        quota.setTotalDeducted(quota.getTotalDeducted().add(totalAmount));
        quota.setUpdatedAt(LocalDateTime.now());
        userQuotaMapper.updateById(quota);

        // 记录交易历史
        createTransaction(userId, "DEDUCT", totalAmount.negate(), balanceBefore, balanceAfter,
                         null, "SYSTEM", null, "批量开票扣除(批次#" + batchId + ")", null);
    }

    /**
     * 管理员修改发票金额时同步调整用户额度
     * @param userId 发票申请用户ID
     * @param amountDiff 金额变化差额（newAmount - oldAmount）。正数表示发票金额增加需要补扣额度，负数表示发票金额减少需要退还额度
     * @param invoiceId 关联发票ID
     * @param operatorId 操作管理员ID
     */
    @Transactional
    public void adjustQuotaForInvoiceAmountChange(Long userId, BigDecimal amountDiff, Long invoiceId, Long operatorId) {
        if (amountDiff == null || amountDiff.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        requireUserRoleForUpdate(userId);
        UserQuota quota = getUserQuotaWithLock(userId);
        BigDecimal balanceBefore = quota.getBalance();

        if (amountDiff.compareTo(BigDecimal.ZERO) > 0) {
            // 发票金额调高，需要额外扣除用户额度
            if (balanceBefore.compareTo(amountDiff) < 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, 40002,
                        "修改开票金额失败：用户剩余额度不足（当前剩余额度：" + balanceBefore.stripTrailingZeros().toPlainString()
                                + "元，增加金额需扣除：" + amountDiff.stripTrailingZeros().toPlainString() + "元）");
            }
            BigDecimal balanceAfter = balanceBefore.subtract(amountDiff);
            quota.setBalance(balanceAfter);
            quota.setTotalDeducted(quota.getTotalDeducted().add(amountDiff));
            quota.setUpdatedAt(LocalDateTime.now());
            userQuotaMapper.updateById(quota);

            createTransaction(userId, "DEDUCT", amountDiff.negate(), balanceBefore, balanceAfter,
                    operatorId, "ADMIN", invoiceId, "管理员修改发票金额补扣额度", null);
        } else {
            // 发票金额调低，退还差额额度
            BigDecimal refundAmount = amountDiff.abs();
            BigDecimal balanceAfter = balanceBefore.add(refundAmount);
            quota.setBalance(balanceAfter);
            BigDecimal newTotalDeducted = quota.getTotalDeducted().subtract(refundAmount);
            quota.setTotalDeducted(newTotalDeducted.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newTotalDeducted);
            quota.setUpdatedAt(LocalDateTime.now());
            userQuotaMapper.updateById(quota);

            createTransaction(userId, "ADJUST", refundAmount, balanceBefore, balanceAfter,
                    operatorId, "ADMIN", invoiceId, "管理员修改发票金额退还额度", null);
        }
    }

    /**
     * 调整额度（管理员手动增减）
     */
    @Transactional
    public UserQuota adjustQuota(Long userId, BigDecimal amount, Long operatorId, String remark,
                                 String idempotencyKey) {
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "调整金额不能为0");
        }

        requireUserRoleForUpdate(userId);
        UserQuota quota = getUserQuotaWithLock(userId);
        UserQuotaTransaction existing = findByIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            validateRepeatedOperation(existing, "ADJUST", amount, remark);
            return quota;
        }
        BigDecimal balanceBefore = quota.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);
        
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40004, "调整后余额不能为负数");
        }
        
        // 更新相关统计
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            quota.setTotalRecharged(quota.getTotalRecharged().add(amount));
        } else {
            quota.setTotalDeducted(quota.getTotalDeducted().add(amount.negate()));
        }
        
        quota.setBalance(balanceAfter);
        quota.setUpdatedAt(LocalDateTime.now());
        userQuotaMapper.updateById(quota);
        
        // 记录交易历史
        createTransaction(userId, "ADJUST", amount, balanceBefore, balanceAfter,
                         operatorId, "ADMIN", null, remark, idempotencyKey);
        return quota;
    }

    /**
     * 查询额度变更历史
     */
    public List<UserQuotaTransaction> getTransactionHistory(Long userId, String transactionType) {
        // Whitelist validation to reject unknown transaction types explicitly
        // rather than silently returning an empty result.
        if (transactionType != null && !transactionType.isBlank()) {
            Set<String> validTypes = Set.of("RECHARGE", "DEDUCT", "ADJUST");
            if (!validTypes.contains(transactionType)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, 40009, "无效的交易类型，只支持 RECHARGE、DEDUCT、ADJUST");
            }
        }
        LambdaQueryWrapper<UserQuotaTransaction> wrapper = new LambdaQueryWrapper<UserQuotaTransaction>()
                .eq(UserQuotaTransaction::getUserId, userId)
                .eq(transactionType != null && !transactionType.isBlank(),
                        UserQuotaTransaction::getTransactionType, transactionType)
                .orderByDesc(UserQuotaTransaction::getCreatedAt);
        return userQuotaTransactionMapper.selectList(wrapper);
    }

    /**
     * 管理员查看额度时只允许操作普通用户，避免通过隐藏前端按钮绕过业务边界。
     */
    public UserQuota getAdminUserQuota(Long userId) {
        requireUserRole(userId);
        return getUserQuota(userId);
    }

    public List<UserQuotaTransaction> getAdminTransactionHistory(Long userId, String transactionType) {
        requireUserRole(userId);
        return getTransactionHistory(userId, transactionType);
    }

    /**
     * 获取用户额度（带行锁，用于并发扣除）
     */
    private UserQuota getUserQuotaWithLock(Long userId) {
        UserQuota quota = userQuotaMapper.selectOne(
            new LambdaQueryWrapper<UserQuota>()
                .eq(UserQuota::getUserId, userId)
                .last("FOR UPDATE")
        );
        
        if (quota == null) {
            try {
                quota = createInitialQuota(userId);
            } catch (DuplicateKeyException exception) {
                quota = userQuotaMapper.selectOne(
                        new LambdaQueryWrapper<UserQuota>()
                                .eq(UserQuota::getUserId, userId)
                                .last("FOR UPDATE")
                );
            }
        } else {
            quota = syncQuotaFromTransactions(userId, quota);
        }
        
        return quota;
    }

    /**
     * 验证充值金额
     */
    private void validateRechargeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40005, "充值金额必须大于0");
        }
        
        if (amount.compareTo(MIN_RECHARGE_AMOUNT) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40006, "充值金额不能小于" + MIN_RECHARGE_AMOUNT);
        }
        
        if (amount.compareTo(MAX_RECHARGE_AMOUNT) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40007, "充值金额不能大于" + MAX_RECHARGE_AMOUNT);
        }
        
        // 检查小数位数
        if (amount.scale() > 2) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40008, "充值金额最多两位小数");
        }
    }

    /**
     * 创建交易记录
     */
    private void createTransaction(Long userId, String transactionType, BigDecimal amount,
                                   BigDecimal balanceBefore, BigDecimal balanceAfter,
                                   Long operatorId, String operatorType, Long invoiceId, String remark,
                                   String idempotencyKey) {
        UserQuotaTransaction transaction = new UserQuotaTransaction();
        transaction.setUserId(userId);
        transaction.setTransactionType(transactionType);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setAmount(amount);
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setOperatorId(operatorId);
        transaction.setOperatorType(operatorType);
        transaction.setInvoiceId(invoiceId);
        transaction.setRemark(remark);
        transaction.setCreatedAt(LocalDateTime.now());
        
        userQuotaTransactionMapper.insert(transaction);
    }

    private UserQuotaTransaction findByIdempotencyKey(Long userId, String idempotencyKey) {
        return userQuotaTransactionMapper.selectOne(
                new LambdaQueryWrapper<UserQuotaTransaction>()
                        .eq(UserQuotaTransaction::getUserId, userId)
                        .eq(UserQuotaTransaction::getIdempotencyKey, idempotencyKey)
        );
    }

    private void validateRepeatedOperation(UserQuotaTransaction existing, String transactionType,
                                           BigDecimal amount, String remark) {
        boolean sameRequest = Objects.equals(existing.getTransactionType(), transactionType)
                && existing.getAmount().compareTo(amount) == 0
                && Objects.equals(normalizeRemark(existing.getRemark()), normalizeRemark(remark));
        if (!sameRequest) {
            throw new BusinessException(HttpStatus.CONFLICT, 40902,
                    "Idempotency-Key 已用于其他额度操作");
        }
    }

    private String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String normalized = remark.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private User requireUserRole(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40403, "用户不存在");
        }
        if (!"USER".equals(user.getRole())) {
            throw new BusinessException(HttpStatus.CONFLICT, 40904, "只有普通用户拥有额度");
        }
        return user;
    }

    private User requireUserRoleForUpdate(Long userId) {
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40403, "用户不存在");
        }
        if (!"USER".equals(user.getRole())) {
            throw new BusinessException(HttpStatus.CONFLICT, 40904, "只有普通用户拥有额度");
        }
        return user;
    }
}
