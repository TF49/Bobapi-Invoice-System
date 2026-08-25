package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.RechargeRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 充值申请Mapper
 */
@Mapper
public interface RechargeRequestMapper extends BaseMapper<RechargeRequest> {
}