package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.SupplierSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 供应商结算记录 Mapper
 */
@Mapper
public interface SupplierSettlementMapper extends BaseMapper<SupplierSettlement> {

    /**
     * 查询总已结算金额
     */
    @Select("SELECT COALESCE(SUM(settlement_amount), 0) FROM supplier_settlement WHERE deleted = 0")
    BigDecimal selectTotalSettledAmount();

    /**
     * 查询最近 6 个月月度结算汇总
     */
    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m') as month, " +
            "COALESCE(SUM(settlement_amount), 0) as amount, " +
            "COUNT(*) as count " +
            "FROM supplier_settlement " +
            "WHERE deleted = 0 AND created_at >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH) " +
            "GROUP BY DATE_FORMAT(created_at, '%Y-%m') " +
            "ORDER BY month ASC")
    java.util.List<MonthlySettlementStat> selectRecentMonthlySettlementStats();

    record MonthlySettlementStat(
            String month,
            BigDecimal amount,
            Long count
    ) {}
}
