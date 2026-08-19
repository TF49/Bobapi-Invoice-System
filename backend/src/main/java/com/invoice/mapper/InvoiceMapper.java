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
            "INSERT INTO invoice (company_name, tax_number, amount, status, idempotency_key, ",
            "batch_id, batch_row_number, user_id, created_at, updated_at, deleted) VALUES ",
            "<foreach collection='invoices' item='invoice' separator=','>",
            "(#{invoice.companyName}, #{invoice.taxNumber}, #{invoice.amount}, #{invoice.status}, ",
            "#{invoice.idempotencyKey}, #{invoice.batchId}, #{invoice.batchRowNumber}, ",
            "#{invoice.userId}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("invoices") List<Invoice> invoices);

    /**
     * 一次性查询全局发票汇总指标（总数、待开数、已开数、已完成总金额），避免多次 SQL 查询导致的潜在并发不一致与网络 I/O 开销。
     */
    @Select("SELECT " +
            "COUNT(*) as totalInvoices, " +
            "COALESCE(SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END), 0) as pendingInvoices, " +
            "COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) as completedInvoices, " +
            "COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN amount ELSE 0 END), 0) as totalAmount " +
            "FROM invoice " +
            "WHERE deleted = 0")
    OverallStat selectOverallStat();

    /**
     * 查询按用户分组的发票统计
     */
    @Select("SELECT i.user_id, COALESCE(u.username, CONCAT('用户#', i.user_id)) as username, " +
            "SUM(CASE WHEN i.status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_count, " +
            "SUM(CASE WHEN i.status = 'PENDING' THEN 1 ELSE 0 END) as pending_count, " +
            "SUM(CASE WHEN i.status = 'COMPLETED' THEN i.amount ELSE 0 END) as total_amount " +
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
            "WHERE user_id = #{userId} AND deleted = 0 AND status = 'COMPLETED' AND completed_at IS NOT NULL " +
            "GROUP BY DATE(completed_at) " +
            "ORDER BY date ASC")
    List<TimelineStat> selectTimelineStats(Long userId);

    /**
     * 一次性查询所有用户的已开票时间线数据，避免 N+1 问题。
     * 仅统计 COMPLETED 状态，限制最近 90 天数据防止趋势图 X 轴过载。
     * 结果按日期升序排列，由调用方按 userId 分组。
     */
    @Select("SELECT user_id, DATE(completed_at) as date, COUNT(*) as count, SUM(amount) as amount " +
            "FROM invoice " +
            "WHERE deleted = 0 AND status = 'COMPLETED' AND completed_at >= DATE_SUB(CURDATE(), INTERVAL 90 DAY) " +
            "GROUP BY user_id, DATE(completed_at) " +
            "ORDER BY date ASC")
    List<TimelineStatWithUser> selectAllTimelineStats();

    /**
     * 全局发票汇总指标记录
     */
    record OverallStat(
            Long totalInvoices,
            Long pendingInvoices,
            Long completedInvoices,
            BigDecimal totalAmount
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
     * 根据批次ID查询发票列表
     */
    @Select("SELECT * FROM invoice WHERE batch_id = #{batchId} AND deleted = 0 ORDER BY batch_row_number ASC")
    List<Invoice> selectByBatchId(Long batchId);
}
