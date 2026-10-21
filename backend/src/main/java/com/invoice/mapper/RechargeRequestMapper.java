package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.RechargeRequest;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Select;

/**
 * 充值申请Mapper
 */
@Mapper
public interface RechargeRequestMapper extends BaseMapper<RechargeRequest> {

    @Select("SELECT COUNT(*) FROM recharge_request WHERE status = 'PENDING' AND deleted = 0")
    Long countPendingRequests();

    /**
     * 查询充值申请审核汇总（通过、拒绝、待审）
     */
    @Select("SELECT " +
            "COALESCE(SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END), 0) as approvedCount, " +
            "COALESCE(SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END), 0) as rejectedCount, " +
            "COALESCE(SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END), 0) as pendingCount, " +
            "COALESCE(SUM(CASE WHEN status = 'APPROVED' THEN amount ELSE 0 END), 0) as approvedAmount " +
            "FROM recharge_request " +
            "WHERE deleted = 0")
    RechargeAuditSummary selectRechargeAuditSummary();

    record RechargeAuditSummary(
            Long approvedCount,
            Long rejectedCount,
            Long pendingCount,
            java.math.BigDecimal approvedAmount
    ) {}
}