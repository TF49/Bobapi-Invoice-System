package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.SupplierSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商结算记录 Mapper。
 * 注意：所有已结算汇总都只统计有效记录（deleted = 0 AND voided_at IS NULL），作废记录仅在历史中展示。
 */
@Mapper
public interface SupplierSettlementMapper extends BaseMapper<SupplierSettlement> {

    /**
     * 查询总已结算金额（不含作废记录）
     */
    @Select("SELECT COALESCE(SUM(settlement_amount), 0) FROM supplier_settlement WHERE deleted = 0 AND voided_at IS NULL")
    BigDecimal selectTotalSettledAmount();

    /**
     * 按结算通道（出票主体 × 票种）汇总已结算金额（不含作废记录）
     */
    @Select("SELECT seller_company AS sellerCompany, invoice_category AS invoiceCategory, " +
            "COALESCE(SUM(settlement_amount), 0) AS amount " +
            "FROM supplier_settlement WHERE deleted = 0 AND voided_at IS NULL " +
            "GROUP BY seller_company, invoice_category")
    java.util.List<ChannelSettledStat> selectSettledAmountByChannel();

    record ChannelSettledStat(
            String sellerCompany,
            String invoiceCategory,
            BigDecimal amount
    ) {}

    /**
     * 作废结算记录（条件更新，仅当记录有效且未作废时生效，防止并发重复作废）。
     *
     * @return 受影响行数；0 表示记录不存在或已被作废
     */
    @Update("UPDATE supplier_settlement SET voided_at = #{voidedAt}, voided_by = #{voidedBy}, " +
            "voided_by_name = #{voidedByName}, void_reason = #{voidReason}, updated_at = #{voidedAt} " +
            "WHERE id = #{id} AND deleted = 0 AND voided_at IS NULL")
    int markVoided(@Param("id") Long id,
                   @Param("voidedAt") LocalDateTime voidedAt,
                   @Param("voidedBy") Long voidedBy,
                   @Param("voidedByName") String voidedByName,
                   @Param("voidReason") String voidReason);

    /**
     * 查询最近 6 个月月度结算汇总，按结算通道拆分（不含作废记录）
     */
    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m') AS month, " +
            "seller_company AS sellerCompany, invoice_category AS invoiceCategory, " +
            "COALESCE(SUM(settlement_amount), 0) AS amount " +
            "FROM supplier_settlement " +
            "WHERE deleted = 0 AND voided_at IS NULL AND created_at >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH) " +
            "GROUP BY month, seller_company, invoice_category " +
            "ORDER BY month ASC")
    java.util.List<com.invoice.mapper.InvoiceMapper.MonthlyChannelAmountStat> selectRecentMonthlySettledByChannel();
}
