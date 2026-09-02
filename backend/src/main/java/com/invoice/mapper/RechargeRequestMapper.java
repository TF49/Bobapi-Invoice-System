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
}