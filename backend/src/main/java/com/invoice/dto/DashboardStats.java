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
         * 总开票金额
         */
        BigDecimal totalAmount,

        /**
         * 各用户统计列表
         */
        List<UserInvoiceStats> userStats
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
}