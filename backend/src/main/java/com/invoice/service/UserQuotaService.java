package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.invoice.entity.UserQuota;
import com.invoice.entity.UserQuotaTransaction;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.UserQuotaMapper;
import com.invoice.mapper.UserQuotaTransactionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户额度服务
 */
@Service
public class UserQuotaService {

    private final UserQuotaMapper userQuotaMapper;
    private final UserQuotaTransactionMapper userQuotaTransactionMapper;

    private static final BigDecimal MIN_RECHARGE_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_RECHARGE_AMOUNT = new BigDecimal("999999.99");

    public UserQuotaService(UserQuotaMapper userQuotaMapper, 
                           UserQuotaTransactionMapper userQuotaTransactionMapper) {
        this.userQuotaMapper = userQuotaMapper;
        this.userQuotaTransactionMapper = userQuotaTransactionMapper;
    }

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
        }
        
        return quota;
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
        return quota;
    }

    /**
     * 充值额度（管理员操作）
     */
    @Transactional
    public void rechargeQuota(Long userId, BigDecimal amount, Long operatorId, String remark) {
        validateRechargeAmount(amount);
        
        UserQuota quota = getUserQuota(userId);
        BigDecimal balanceBefore = quota.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);
        
        // 更新额度
        quota.setBalance(balanceAfter);
        quota.setTotalRecharged(quota.getTotalRecharged().add(amount));
        quota.setUpdatedAt(LocalDateTime.now());
        userQuotaMapper.updateById(quota);
        
        // 记录交易历史
        createTransaction(userId, "RECHARGE", amount, balanceBefore, balanceAfter, 
                         operatorId, "ADMIN", null, remark);
    }

    /**
     * 扣除额度（开票时调用）
     */
    @Transactional
    public void deductQuota(Long userId, BigDecimal amount, Long invoiceId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "扣除金额必须大于0");
        }
        
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
                         null, "SYSTEM", invoiceId, "开票扣除");
    }

    /**
     * 调整额度（管理员手动增减）
     */
    @Transactional
    public void adjustQuota(Long userId, BigDecimal amount, Long operatorId, String remark) {
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "调整金额不能为0");
        }
        
        UserQuota quota = getUserQuota(userId);
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
                         operatorId, "ADMIN", null, remark);
    }

    /**
     * 查询额度变更历史
     */
    public List<UserQuotaTransaction> getTransactionHistory(Long userId, String transactionType) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserQuotaTransaction> wrapper = 
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.orderByDesc("created_at");
        
        if (transactionType != null && !transactionType.isEmpty()) {
            wrapper.eq("transaction_type", transactionType);
        }
        
        return userQuotaTransactionMapper.selectList(wrapper);
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
            quota = createInitialQuota(userId);
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
                                   Long operatorId, String operatorType, Long invoiceId, String remark) {
        UserQuotaTransaction transaction = new UserQuotaTransaction();
        transaction.setUserId(userId);
        transaction.setTransactionType(transactionType);
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
}