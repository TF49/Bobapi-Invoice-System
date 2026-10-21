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
         * 供应商已结算金额
         */
        BigDecimal totalSettledAmount,

        /**
         * 供应商未结算金额
         */
        BigDecimal unsettledAmount,

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
        QuotaPoolStat quotaPoolStats,

        /**
         * 渠道提交统计（API调用 vs 手动申请）
         */
        List<ChannelStat> channelStats,

        /**
         * 发票票种统计（普票 vs 专票）
         */
        List<CategoryStat> categoryStats,

        /**
         * 发票红冲与异常审计统计
         */
        RedFlushStat redFlushStats,

        /**
         * 开票履约服务时效统计 (SLA)
         */
        FulfillmentStat fulfillmentStats,

        /**
         * 资金池收支流动与审批统计
         */
        FundFlowStat fundFlowStats,

        /**
         * 供应商开票与结算月度对账趋势
         */
        List<SettlementTrendStat> settlementTrends
) {

    /**
     * 向后兼容构造器（14参数版本），用于历史测试用例和扩展前的代码
     */
    public DashboardStats(
            Long totalInvoices,
            Long pendingInvoices,
            Long completedInvoices,
            BigDecimal totalAmount,
            BigDecimal pendingAmount,
            BigDecimal totalSettledAmount,
            BigDecimal unsettledAmount,
            List<UserInvoiceStats> userStats,
            List<InvoiceTypeStat> typeStats,
            List<CompanyStat> companyTopStats,
            List<AmountRangeStat> amountRangeStats,
            List<HourStat> hourDistribution,
            List<DailyTrendStat> dailyTrend,
            QuotaPoolStat quotaPoolStats
    ) {
        this(
                totalInvoices,
                pendingInvoices,
                completedInvoices,
                totalAmount,
                pendingAmount,
                totalSettledAmount,
                unsettledAmount,
                userStats,
                typeStats,
                companyTopStats,
                amountRangeStats,
                hourDistribution,
                dailyTrend,
                quotaPoolStats,
                List.of(),
                List.of(),
                new RedFlushStat(0L, BigDecimal.ZERO, 0L, 0.0),
                new FulfillmentStat(0L, 0L, 0L, 0L, 0L, 0.0, 0.0),
                new FundFlowStat(List.of(), 0L, 0L, 0L, BigDecimal.ZERO, 0.0),
                List.of()
        );
    }

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

    /**
     * 渠道提交统计 (API自动化 vs 网页手动)
     */
    public record ChannelStat(
            String channel,
            Long count,
            BigDecimal amount
    ) {}

    /**
     * 发票票种统计 (普票 vs 专票)
     */
    public record CategoryStat(
            String category,
            Long count,
            BigDecimal amount
    ) {}

    /**
     * 发票红冲与异常审计统计
     */
    public record RedFlushStat(
            Long redFlushedCount,
            BigDecimal redFlushedAmount,
            Long pendingRedFlushCount,
            Double redFlushRate
    ) {}

    /**
     * 开票履约服务时效统计 (SLA)
     */
    public record FulfillmentStat(
            Long under30Min,
            Long under2Hour,
            Long under6Hour,
            Long under24Hour,
            Long over24Hour,
            Double avgMinutes,
            Double fastCompletionRate
    ) {}

    /**
     * 资金池收支流动与审批统计
     */
    public record FundFlowStat(
            List<DailyFundFlow> dailyFlows,
            Long approvedRecharges,
            Long rejectedRecharges,
            Long pendingRecharges,
            BigDecimal approvedRechargeAmount,
            Double approvalRate
    ) {}

    /**
     * 每日资金与额度流动
     */
    public record DailyFundFlow(
            LocalDate date,
            BigDecimal rechargeAmount,
            BigDecimal deductAmount,
            BigDecimal netAmount
    ) {}

    /**
     * 供应商开票与结算月度对账趋势
     */
    public record SettlementTrendStat(
            String month,
            BigDecimal invoicedAmount,
            BigDecimal settledAmount,
            BigDecimal unsettledBalance
    ) {}
}
