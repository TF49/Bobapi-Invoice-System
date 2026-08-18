package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户数据访问层
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM `user` WHERE `id` = #{id} AND `deleted` = 0 FOR UPDATE")
    User selectByIdForUpdate(Long id);

    @Select("SELECT `id` FROM `user` " +
            "WHERE `role` = 'ADMIN' AND `enabled` = 1 AND `deleted` = 0 ORDER BY `id` FOR UPDATE")
    List<Long> selectEnabledAdminIdsForUpdate();
}
