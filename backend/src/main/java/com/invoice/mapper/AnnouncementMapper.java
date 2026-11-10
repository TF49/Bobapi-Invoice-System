package com.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.invoice.entity.Announcement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {

    /**
     * 查询用户未读（或被重新推送）的已发布公告，按发布时间倒序
     */
    @Select("""
            SELECT a.* FROM announcement a
            LEFT JOIN announcement_read r ON r.announcement_id = a.id AND r.user_id = #{userId}
            WHERE a.status = 'PUBLISHED' AND a.deleted = 0
              AND (r.id IS NULL OR r.read_version < a.push_version)
            ORDER BY a.published_at DESC, a.id DESC
            LIMIT #{limit}
            """)
    List<Announcement> selectUnreadByUser(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 按状态统计未删除的公告数量
     */
    @Select("SELECT status AS status, COUNT(*) AS cnt FROM announcement WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> countByStatus();
}
