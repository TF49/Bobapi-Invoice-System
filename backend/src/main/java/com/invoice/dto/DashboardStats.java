package com.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dashboard 统计数据响应
 */
public record DashboardStats(
        /**
         * 总发票数
         */
        Long totalInvoices,

        /**
         * 待开票数
         */
        Long pendingInvoices,

        /**
         * 已开票数
         */
        Long completedInvoices,

        /**
         * 总开票金额（已开票）
         */
        BigDecimal totalAmount,

        /**
         * 待开票总金额
         */
        BigDecimal pendingAmount,

        /**
         * 各用户统计列表
         */
        List<UserInvoiceStats> userStats,

        /**
         * 开票类目统计列表
         */
        List<InvoiceTypeStat> typeStats,

        /**
         * 企业抬头开票排行
         */
        List<CompanyStat> companyTopStats,

        /**
         * 金额区间分布统计
         */
        List<AmountRangeStat> amountRangeStats,

        /**
         * 24小时申请热度分布
         */
        List<HourStat> hourDistribution,

        /**
         * 每日综合开票走势（过去90天）
         */
        List<DailyTrendStat> dailyTrend,

        /**
         * 额度与资金池统计
         */
        QuotaPoolStat quotaPoolStats
) {

    /**
     * 单个用户的发票统计
     */
    public record UserInvoiceStats(
            /**
             * 用户ID
             */
            Long userId,

            /**
             * 用户名
             */
            String username,

            /**
             * 已开票数量
             */
            Long completedCount,

            /**
             * 待开票数量
             */
            Long pendingCount,

            /**
             * 总开票金额
             */
            BigDecimal totalAmount,

            /**
             * 时间线数据（用于趋势图）
             */
            List<TimelineData> timeline
    ) {}

    /**
     * 时间线数据点
     */
    public record TimelineData(
            /**
             * 日期
             */
            LocalDate date,

            /**
             * 当日开票数量
             */
            Long count,

            /**
             * 当日开票金额
             */
            BigDecimal amount
    ) {}

    /**
     * 开票类目统计
     */
    public record InvoiceTypeStat(
            String invoiceType,
            Long count,
            BigDecimal amount
    ) {}

    /**
     * 企业抬头统计
     */
    public record CompanyStat(
            String companyName,
            Long count,
            BigDecimal amount
    ) {}

    /**
     * 金额区间统计
     */
    public record AmountRangeStat(
            String rangeLabel,
            Long minAmount,
            Long maxAmount,
            Long count,
            BigDecimal amount
    ) {}

    /**
     * 小时时段统计
     */
    public record HourStat(
            Integer hour,
            Long count
    ) {}

    /**
     * 每日综合趋势统计
     */
    public record DailyTrendStat(
            LocalDate date,
            Long completedCount,
            BigDecimal completedAmount,
            Long createdCount,
            Long pendingCount,
            Long cancelledCount
    ) {}

    /**
     * 额度与资金池统计
     */
    public record QuotaPoolStat(
            BigDecimal totalBalance,
            BigDecimal totalRecharged,
            BigDecimal totalDeducted,
            Long pendingRechargeCount
    ) {}
}