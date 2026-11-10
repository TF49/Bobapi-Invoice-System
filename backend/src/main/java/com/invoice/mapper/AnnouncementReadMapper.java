package com.invoice.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AnnouncementReadMapper {

    /**
     * 记录已读：已存在则更新为最新推送版本
     */
    @Insert("""
            INSERT INTO announcement_read (announcement_id, user_id, read_version, read_at)
            VALUES (#{announcementId}, #{userId}, #{readVersion}, NOW())
            ON DUPLICATE KEY UPDATE
              read_version = GREATEST(read_version, VALUES(read_version)),
              read_at = VALUES(read_at)
            """)
    int upsertRead(@Param("announcementId") Long announcementId,
                   @Param("userId") Long userId,
                   @Param("readVersion") int readVersion);

    /**
     * 统计每条公告在当前推送版本下的已读人数
     */
    @Select("""
            <script>
            SELECT r.announcement_id AS announcementId, COUNT(*) AS readCount
            FROM announcement_read r
            JOIN announcement a ON a.id = r.announcement_id
            WHERE r.read_version >= a.push_version
              AND r.announcement_id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY r.announcement_id
            </script>
            """)
    List<Map<String, Object>> countReadByAnnouncementIds(@Param("ids") List<Long> ids);
}
