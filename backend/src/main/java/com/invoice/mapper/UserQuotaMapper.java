package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.UserQuota;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 用户额度 Mapper
 */
@Mapper
public interface UserQuotaMapper extends BaseMapper<UserQuota> {

    @Select("SELECT " +
            "COALESCE(SUM(balance), 0) as totalBalance, " +
            "COALESCE(SUM(total_recharged), 0) as totalRecharged, " +
            "COALESCE(SUM(total_deducted), 0) as totalDeducted " +
            "FROM user_quota WHERE deleted = 0")
    QuotaPoolSummary selectQuotaPoolSummary();

    record QuotaPoolSummary(
            BigDecimal totalBalance,
            BigDecimal totalRecharged,
            BigDecimal totalDeducted
    ) {}
}