package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.UserQuotaTransaction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户额度变更历史 Mapper
 */
@Mapper
public interface UserQuotaTransactionMapper extends BaseMapper<UserQuotaTransaction> {
}