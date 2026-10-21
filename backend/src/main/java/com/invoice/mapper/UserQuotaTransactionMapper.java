package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.UserQuotaTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 用户额度变更历史 Mapper
 */
@Mapper
public interface UserQuotaTransactionMapper extends BaseMapper<UserQuotaTransaction> {

    /**
     * 查询近 30 天每日额度收支流水（充值入账 vs 扣除开票 vs 调账）
     */
    @Select("SELECT DATE(created_at) as date, " +
            "COALESCE(SUM(CASE WHEN transaction_type = 'RECHARGE' THEN amount ELSE 0 END), 0) as rechargeAmount, " +
            "COALESCE(SUM(CASE WHEN transaction_type = 'DEDUCT' THEN ABS(amount) ELSE 0 END), 0) as deductAmount, " +
            "COALESCE(SUM(CASE WHEN transaction_type = 'ADJUST' THEN amount ELSE 0 END), 0) as adjustAmount " +
            "FROM user_quota_transaction " +
            "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
            "GROUP BY DATE(created_at) " +
            "ORDER BY date ASC")
    List<DailyQuotaFlowStat> selectDailyQuotaFlowStats();

    record DailyQuotaFlowStat(
            LocalDate date,
            BigDecimal rechargeAmount,
            BigDecimal deductAmount,
            BigDecimal adjustAmount
    ) {}
}