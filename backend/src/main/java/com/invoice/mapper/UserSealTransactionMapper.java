package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.UserSealTransaction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户盖章额度变更流水 Mapper
 */
@Mapper
public interface UserSealTransactionMapper extends BaseMapper<UserSealTransaction> {
}
