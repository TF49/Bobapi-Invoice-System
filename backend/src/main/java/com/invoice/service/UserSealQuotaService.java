package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.invoice.dto.UserSealQuotaResponse;
import com.invoice.entity.User;
import com.invoice.entity.UserSealQuota;
import com.invoice.entity.UserSealTransaction;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.UserMapper;
import com.invoice.mapper.UserSealQuotaMapper;
import com.invoice.mapper.UserSealTransactionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 用户盖章独立额度服务（10元/次）
 */
@Service
public class UserSealQuotaService {

    private final UserSealQuotaMapper userSealQuotaMapper;
    private final UserSealTransactionMapper userSealTransactionMapper;
    private final UserMapper userMapper;

    public static final BigDecimal SINGLE_SEAL_FEE = new BigDecimal("10.00");

    public UserSealQuotaService(UserSealQuotaMapper userSealQuotaMapper,
                                UserSealTransactionMapper userSealTransactionMapper,
                                UserMapper userMapper) {
        this.userSealQuotaMapper = userSealQuotaMapper;
        this.userSealTransactionMapper = userSealTransactionMapper;
        this.userMapper = userMapper;
    }

    /**
     * 获取用户盖章额度
     */
    public UserSealQuota getUserQuota(Long userId) {
        if (userId == null) {
            return null;
        }
        UserSealQuota quota = userSealQuotaMapper.selectOne(
                new LambdaQueryWrapper<UserSealQuota>().eq(UserSealQuota::getUserId, userId)
        );
        if (quota == null) {
            try {
                quota = createInitialQuota(userId);
            } catch (DuplicateKeyException e) {
                quota = userSealQuotaMapper.selectOne(
                        new LambdaQueryWrapper<UserSealQuota>().eq(UserSealQuota::getUserId, userId)
                );
            }
        }
        return quota;
    }

    /**
     * 获取用户盖章额度响应 DTO
     */
    public UserSealQuotaResponse getUserQuotaResponse(Long userId) {
        UserSealQuota quota = getUserQuota(userId);
        User user = userMapper.selectById(userId);
        
        UserSealQuotaResponse response = new UserSealQuotaResponse();
        if (quota != null) {
            response.setId(quota.getId());
            response.setUserId(quota.getUserId());
            response.setBalance(quota.getBalance());
            response.setTotalRecharged(quota.getTotalRecharged());
            response.setTotalDeducted(quota.getTotalDeducted());
            response.setUpdatedAt(quota.getUpdatedAt());
            
            // 计算可用次数 (balance / 10)
            int count = quota.getBalance() != null
                    ? quota.getBalance().divide(SINGLE_SEAL_FEE, 0, RoundingMode.DOWN).intValue()
                    : 0;
            response.setAvailableCount(Math.max(0, count));
        }
        if (user != null) {
            response.setUsername(user.getUsername());
        }
        return response;
    }

    /**
     * 为用户创建初始盖章额度
     */
    @Transactional
    public UserSealQuota createInitialQuota(Long userId) {
        UserSealQuota quota = new UserSealQuota();
        quota.setUserId(userId);
        quota.setBalance(BigDecimal.ZERO);
        quota.setTotalRecharged(BigDecimal.ZERO);
        quota.setTotalDeducted(BigDecimal.ZERO);
        quota.setCreatedAt(LocalDateTime.now());
        quota.setUpdatedAt(LocalDateTime.now());
        userSealQuotaMapper.insert(quota);
        return quota;
    }

    /**
     * 扣除盖章额度（原子检查与扣减）
     */
    @Transactional
    public void deductQuota(Long userId, Long documentId, BigDecimal amount, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "扣款金额必须大于 0");
        }
        
        // 排他锁查询用户额度
        UserSealQuota quota = userSealQuotaMapper.selectOne(
                new LambdaQueryWrapper<UserSealQuota>()
                        .eq(UserSealQuota::getUserId, userId)
                        .last("FOR UPDATE")
        );
        if (quota == null) {
            quota = createInitialQuota(userId);
        }

        BigDecimal balanceBefore = quota.getBalance() != null ? quota.getBalance() : BigDecimal.ZERO;
        if (balanceBefore.compareTo(amount) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40002, 
                    "盖章额度不足，本次盖章需 " + amount + " 元，当前可用余额 " + balanceBefore + " 元，请先充值");
        }

        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        BigDecimal totalDeducted = (quota.getTotalDeducted() != null ? quota.getTotalDeducted() : BigDecimal.ZERO).add(amount);

        quota.setBalance(balanceAfter);
        quota.setTotalDeducted(totalDeducted);
        quota.setUpdatedAt(LocalDateTime.now());
        userSealQuotaMapper.updateById(quota);

        // 记流水
        UserSealTransaction tx = new UserSealTransaction();
        tx.setUserId(userId);
        tx.setTransactionType("DEDUCT");
        tx.setAmount(amount.negate());
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(balanceAfter);
        tx.setDocumentId(documentId);
        tx.setOperatorType("SYSTEM");
        tx.setRemark(remark != null ? remark : "盖章服务费扣除");
        tx.setCreatedAt(LocalDateTime.now());
        userSealTransactionMapper.insert(tx);
    }

    /**
     * 退还盖章额度（工单撤销或驳回时）
     */
    @Transactional
    public void refundQuota(Long userId, Long documentId, BigDecimal amount, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        UserSealQuota quota = userSealQuotaMapper.selectOne(
                new LambdaQueryWrapper<UserSealQuota>()
                        .eq(UserSealQuota::getUserId, userId)
                        .last("FOR UPDATE")
        );
        if (quota == null) {
            quota = createInitialQuota(userId);
        }

        BigDecimal balanceBefore = quota.getBalance() != null ? quota.getBalance() : BigDecimal.ZERO;
        BigDecimal balanceAfter = balanceBefore.add(amount);
        BigDecimal totalDeducted = quota.getTotalDeducted() != null ? quota.getTotalDeducted() : BigDecimal.ZERO;
        totalDeducted = totalDeducted.subtract(amount);
        if (totalDeducted.compareTo(BigDecimal.ZERO) < 0) {
            totalDeducted = BigDecimal.ZERO;
        }

        quota.setBalance(balanceAfter);
        quota.setTotalDeducted(totalDeducted);
        quota.setUpdatedAt(LocalDateTime.now());
        userSealQuotaMapper.updateById(quota);

        // 记退款流水
        UserSealTransaction tx = new UserSealTransaction();
        tx.setUserId(userId);
        tx.setTransactionType("REFUND");
        tx.setAmount(amount);
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(balanceAfter);
        tx.setDocumentId(documentId);
        tx.setOperatorType("SYSTEM");
        tx.setRemark(remark != null ? remark : "盖章服务费退还");
        tx.setCreatedAt(LocalDateTime.now());
        userSealTransactionMapper.insert(tx);
    }

    /**
     * 充值盖章额度（审核通过或管理员手动充值）
     */
    @Transactional
    public UserSealQuota rechargeQuota(Long userId, BigDecimal amount, Long operatorId, String remark, String idempotencyKey) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "充值金额必须大于 0");
        }

        // 幂等防重
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            UserSealTransaction existing = userSealTransactionMapper.selectOne(
                    new LambdaQueryWrapper<UserSealTransaction>()
                            .eq(UserSealTransaction::getUserId, userId)
                            .eq(UserSealTransaction::getIdempotencyKey, idempotencyKey)
            );
            if (existing != null) {
                return getUserQuota(userId);
            }
        }

        UserSealQuota quota = userSealQuotaMapper.selectOne(
                new LambdaQueryWrapper<UserSealQuota>()
                        .eq(UserSealQuota::getUserId, userId)
                        .last("FOR UPDATE")
        );
        if (quota == null) {
            quota = createInitialQuota(userId);
        }

        BigDecimal balanceBefore = quota.getBalance() != null ? quota.getBalance() : BigDecimal.ZERO;
        BigDecimal balanceAfter = balanceBefore.add(amount);
        BigDecimal totalRecharged = (quota.getTotalRecharged() != null ? quota.getTotalRecharged() : BigDecimal.ZERO).add(amount);

        quota.setBalance(balanceAfter);
        quota.setTotalRecharged(totalRecharged);
        quota.setUpdatedAt(LocalDateTime.now());
        userSealQuotaMapper.updateById(quota);

        // 记充值流水
        UserSealTransaction tx = new UserSealTransaction();
        tx.setUserId(userId);
        tx.setTransactionType("RECHARGE");
        tx.setAmount(amount);
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(balanceAfter);
        tx.setOperatorId(operatorId);
        tx.setOperatorType("ADMIN");
        tx.setIdempotencyKey(idempotencyKey);
        tx.setRemark(remark != null ? remark : "盖章钱包充值");
        tx.setCreatedAt(LocalDateTime.now());
        userSealTransactionMapper.insert(tx);

        return quota;
    }

    /**
     * 管理员调整盖章额度
     */
    @Transactional
    public UserSealQuota adjustQuota(Long userId, BigDecimal amount, Long operatorId, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40004, "调整金额不能为 0");
        }

        UserSealQuota quota = userSealQuotaMapper.selectOne(
                new LambdaQueryWrapper<UserSealQuota>()
                        .eq(UserSealQuota::getUserId, userId)
                        .last("FOR UPDATE")
        );
        if (quota == null) {
            quota = createInitialQuota(userId);
        }

        BigDecimal balanceBefore = quota.getBalance() != null ? quota.getBalance() : BigDecimal.ZERO;
        BigDecimal balanceAfter = balanceBefore.add(amount);
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40005, "调整后余额不能为负数");
        }

        quota.setBalance(balanceAfter);
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            quota.setTotalRecharged((quota.getTotalRecharged() != null ? quota.getTotalRecharged() : BigDecimal.ZERO).add(amount));
        } else {
            quota.setTotalDeducted((quota.getTotalDeducted() != null ? quota.getTotalDeducted() : BigDecimal.ZERO).add(amount.abs()));
        }
        quota.setUpdatedAt(LocalDateTime.now());
        userSealQuotaMapper.updateById(quota);

        UserSealTransaction tx = new UserSealTransaction();
        tx.setUserId(userId);
        tx.setTransactionType("ADJUST");
        tx.setAmount(amount);
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(balanceAfter);
        tx.setOperatorId(operatorId);
        tx.setOperatorType("ADMIN");
        tx.setRemark(remark != null ? remark : "管理员调整盖章额度");
        tx.setCreatedAt(LocalDateTime.now());
        userSealTransactionMapper.insert(tx);

        return quota;
    }

    /**
     * 批量获取用户的盖章额度
     */
    public java.util.List<UserSealQuota> getQuotasByUserIds(java.util.List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return userSealQuotaMapper.selectList(
                new LambdaQueryWrapper<UserSealQuota>().in(UserSealQuota::getUserId, userIds)
        );
    }

    /**
     * 查询盖章额度变更历史
     */
    public java.util.List<UserSealTransaction> getTransactionHistory(Long userId, String transactionType) {
        if (transactionType != null && !transactionType.isBlank()) {
            java.util.Set<String> validTypes = java.util.Set.of("RECHARGE", "DEDUCT", "ADJUST", "REFUND");
            if (!validTypes.contains(transactionType)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, 40009, "无效的交易类型，只支持 RECHARGE、DEDUCT、ADJUST、REFUND");
            }
        }
        LambdaQueryWrapper<UserSealTransaction> wrapper = new LambdaQueryWrapper<UserSealTransaction>()
                .eq(UserSealTransaction::getUserId, userId)
                .eq(transactionType != null && !transactionType.isBlank(),
                        UserSealTransaction::getTransactionType, transactionType)
                .orderByDesc(UserSealTransaction::getCreatedAt);
        return userSealTransactionMapper.selectList(wrapper);
    }
}
