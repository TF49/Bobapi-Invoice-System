package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.Invoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 发票数据访问层
 */
@Mapper
public interface InvoiceMapper extends BaseMapper<Invoice> {

    @Insert({
            "<script>",
            "INSERT INTO invoice (company_name, tax_number, amount, invoice_type, remark, status, idempotency_key, ",
            "batch_id, batch_row_number, user_id, created_at, updated_at, deleted) VALUES ",
            "<foreach collection='invoices' item='invoice' separator=','>",
            "(#{invoice.companyName}, #{invoice.taxNumber}, #{invoice.amount}, #{invoice.invoiceType}, #{invoice.remark}, #{invoice.status}, ",
            "#{invoice.idempotencyKey}, #{invoice.batchId}, #{invoice.batchRowNumber}, ",
            "#{invoice.userId}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("invoices") List<Invoice> invoices);

    /**
     * 一次性查询全局发票汇总指标（总数、待开数、已开数、已完成总金额、待开总金额）
     */
    @Select("SELECT " +
            "COUNT(*) as totalInvoices, " +
            "COALESCE(SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END), 0) as pendingInvoices, " +
            "COALESCE(SUM(CASE WHEN status = 'COMPLETED' AND (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') THEN 1 ELSE 0 END), 0) as completedInvoices, " +
            "COALESCE(SUM(CASE WHEN status = 'COMPLETED' AND (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') THEN amount ELSE 0 END), 0) as totalAmount, " +
            "COALESCE(SUM(CASE WHEN status = 'PENDING' THEN amount ELSE 0 END), 0) as pendingAmount " +
            "FROM invoice " +
            "WHERE deleted = 0")
    OverallStat selectOverallStat();

    /**
     * 查询按用户分组的发票统计
     */
    @Select("SELECT i.user_id, COALESCE(u.username, CONCAT('用户#', i.user_id)) as username, " +
            "SUM(CASE WHEN i.status = 'COMPLETED' AND (i.red_flush_status IS NULL OR i.red_flush_status != 'COMPLETED') THEN 1 ELSE 0 END) as completed_count, " +
            "SUM(CASE WHEN i.status = 'PENDING' THEN 1 ELSE 0 END) as pending_count, " +
            "SUM(CASE WHEN i.status = 'COMPLETED' AND (i.red_flush_status IS NULL OR i.red_flush_status != 'COMPLETED') THEN i.amount ELSE 0 END) as total_amount " +
            "FROM invoice i " +
            "LEFT JOIN `user` u ON i.user_id = u.id " +
            "WHERE i.deleted = 0 " +
            "GROUP BY i.user_id, u.username")
    List<UserInvoiceStat> selectUserInvoiceStats();

    /**
     * 查询指定用户的已开票时间线数据（仅统计 COMPLETED 状态，与图表标题"已开票增长趋势"口径一致）
     *
     * @deprecated 存在 N+1 问题，请使用 {@link #selectAllTimelineStats()} 代替
     */
    @Deprecated
    @Select("SELECT DATE(completed_at) as date, COUNT(*) as count, SUM(amount) as amount " +
            "FROM invoice " +
            "WHERE user_id = #{userId} AND deleted = 0 AND status = 'COMPLETED' AND (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') AND completed_at IS NOT NULL " +
            "GROUP BY DATE(completed_at) " +
            "ORDER BY date ASC")
    List<TimelineStat> selectTimelineStats(Long userId);

    /**
     * 一次性查询所有用户的已开票时间线数据，避免 N+1 问题。
     * 仅统计 COMPLETED 状态且排除已红冲发票，限制最近 90 天数据防止趋势图 X 轴过载。
     * 结果按日期升序排列，由调用方按 userId 分组。
     */
    @Select("SELECT user_id, DATE(created_at) as date, COUNT(*) as count, COALESCE(SUM(amount), 0) as amount " +
            "FROM invoice " +
            "WHERE deleted = 0 AND status = 'COMPLETED' AND (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') AND created_at >= DATE_SUB(CURDATE(), INTERVAL 90 DAY) " +
            "GROUP BY user_id, DATE(created_at) " +
            "ORDER BY date ASC")
    List<TimelineStatWithUser> selectAllTimelineStats();

    /**
     * 查询开票类目/项目分布
     */
    @Select("SELECT COALESCE(NULLIF(invoice_type, ''), '其他') as invoice_type, " +
            "COUNT(*) as count, " +
            "COALESCE(SUM(amount), 0) as amount " +
            "FROM invoice " +
            "WHERE deleted = 0 AND (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') " +
            "GROUP BY invoice_type " +
            "ORDER BY count DESC")
    List<InvoiceTypeStat> selectInvoiceTypeStats();

    /**
     * 查询企业抬头开票排行 TOP 10
     */
    @Select("SELECT company_name, " +
            "COUNT(*) as count, " +
            "COALESCE(SUM(amount), 0) as amount " +
            "FROM invoice " +
            "WHERE deleted = 0 AND (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') AND company_name IS NOT NULL AND TRIM(company_name) != '' " +
            "GROUP BY company_name " +
            "ORDER BY amount DESC " +
            "LIMIT 20")
    List<CompanyStat> selectTopCompanyStats();

    /**
     * 查询 24 小时申请热度分布
     */
    @Select("SELECT HOUR(created_at) as hour, " +
            "COUNT(*) as count " +
            "FROM invoice " +
            "WHERE deleted = 0 " +
            "GROUP BY HOUR(created_at) " +
            "ORDER BY hour ASC")
    List<HourStat> selectHourDistributionStats();

    /**
     * 查询每日综合开票与申请趋势（过去 90 天，以统一业务单据日期 DATE(created_at) 归集）
     */
    @Select("SELECT DATE(created_at) as date, " +
            "COALESCE(SUM(CASE WHEN status = 'COMPLETED' AND (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') THEN 1 ELSE 0 END), 0) as completed_count, " +
            "COALESCE(SUM(CASE WHEN status = 'COMPLETED' AND (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') THEN amount ELSE 0 END), 0) as completed_amount, " +
            "COUNT(*) as created_count, " +
            "COALESCE(SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END), 0) as pending_count, " +
            "COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0) as cancelled_count " +
            "FROM invoice " +
            "WHERE deleted = 0 AND created_at >= DATE_SUB(CURDATE(), INTERVAL 90 DAY) " +
            "GROUP BY DATE(created_at) " +
            "ORDER BY date ASC")
    List<DailyTrendStat> selectDailyTrendStats();

    /**
     * 单条 SQL 高效聚合金额区间画像统计（避免大数据量时拉取全量实体到 JVM 内存）
     */
    @Select("SELECT " +
            "COALESCE(SUM(CASE WHEN (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') AND amount < 500 THEN 1 ELSE 0 END), 0) as c1, " +
            "COALESCE(SUM(CASE WHEN (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') AND amount < 500 THEN amount ELSE 0 END), 0) as a1, " +
            "COALESCE(SUM(CASE WHEN (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') AND amount >= 500 AND amount < 2000 THEN 1 ELSE 0 END), 0) as c2, " +
            "COALESCE(SUM(CASE WHEN (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') AND amount >= 500 AND amount < 2000 THEN amount ELSE 0 END), 0) as a2, " +
            "COALESCE(SUM(CASE WHEN (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') AND amount >= 2000 AND amount < 5000 THEN 1 ELSE 0 END), 0) as c3, " +
            "COALESCE(SUM(CASE WHEN (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') AND amount >= 2000 AND amount < 5000 THEN amount ELSE 0 END), 0) as a3, " +
            "COALESCE(SUM(CASE WHEN (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') AND amount >= 5000 AND amount < 10000 THEN 1 ELSE 0 END), 0) as c4, " +
            "COALESCE(SUM(CASE WHEN (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') AND amount >= 5000 AND amount < 10000 THEN amount ELSE 0 END), 0) as a4, " +
            "COALESCE(SUM(CASE WHEN (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') AND amount >= 10000 THEN 1 ELSE 0 END), 0) as c5, " +
            "COALESCE(SUM(CASE WHEN (red_flush_status IS NULL OR red_flush_status != 'COMPLETED') AND amount >= 10000 THEN amount ELSE 0 END), 0) as a5 " +
            "FROM invoice " +
            "WHERE deleted = 0")
    AmountRangeSummary selectAmountRangeSummary();

    /**
     * 全局发票汇总指标记录
     */
    record OverallStat(
            Long totalInvoices,
            Long pendingInvoices,
            Long completedInvoices,
            BigDecimal totalAmount,
            BigDecimal pendingAmount
    ) {}

    /**
     * 用户发票统计记录
     */
    record UserInvoiceStat(
            Long userId,
            String username,
            Long completedCount,
            Long pendingCount,
            BigDecimal totalAmount
    ) {}

    /**
     * 开票类目统计
     */
    record InvoiceTypeStat(
            String invoiceType,
            Long count,
            BigDecimal amount
    ) {}

    /**
     * 企业抬头统计
     */
    record CompanyStat(
            String companyName,
            Long count,
            BigDecimal amount
    ) {}

    /**
     * 小时时段统计
     */
    record HourStat(
            Integer hour,
            Long count
    ) {}

    /**
     * 每日综合趋势统计
     */
    record DailyTrendStat(
            LocalDate date,
            Long completedCount,
            BigDecimal completedAmount,
            Long createdCount,
            Long pendingCount,
            Long cancelledCount
    ) {}

    /**
     * 时间线统计记录（单用户，已废弃）
     */
    record TimelineStat(
            LocalDate date,
            Long count,
            BigDecimal amount
    ) {}

    /**
     * 携带用户 ID 的时间线统计记录（批量查询使用）
     */
    record TimelineStatWithUser(
            Long userId,
            LocalDate date,
            Long count,
            BigDecimal amount
    ) {}

    /**
     * 金额区间聚合统计汇总
     */
    record AmountRangeSummary(
            Long c1, BigDecimal a1,
            Long c2, BigDecimal a2,
            Long c3, BigDecimal a3,
            Long c4, BigDecimal a4,
            Long c5, BigDecimal a5
    ) {}

    /**
     * 根据批次ID查询发票列表
     */
    @Select("SELECT * FROM invoice WHERE batch_id = #{batchId} AND deleted = 0 ORDER BY batch_row_number ASC")
    List<Invoice> selectByBatchId(Long batchId);
}
