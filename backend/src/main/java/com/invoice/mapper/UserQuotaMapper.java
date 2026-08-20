package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.UserQuota;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户额度 Mapper
 */
@Mapper
public interface UserQuotaMapper extends BaseMapper<UserQuota> {
}