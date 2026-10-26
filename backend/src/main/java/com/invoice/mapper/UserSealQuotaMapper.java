package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.UserSealQuota;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户盖章独立额度 Mapper
 */
@Mapper
public interface UserSealQuotaMapper extends BaseMapper<UserSealQuota> {
}
