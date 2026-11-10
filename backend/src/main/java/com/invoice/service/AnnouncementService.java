package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.invoice.dto.AdminAnnouncementPageResponse;
import com.invoice.dto.AdminAnnouncementResponse;
import com.invoice.dto.AnnouncementPageResponse;
import com.invoice.dto.AnnouncementResponse;
import com.invoice.dto.AnnouncementSaveRequest;
import com.invoice.dto.AnnouncementStats;
import com.invoice.entity.Announcement;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.AnnouncementMapper;
import com.invoice.mapper.AnnouncementReadMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 系统公告：管理员维护，所有登录用户以弹窗形式接收。
 * <p>
 * 未读判定基于推送版本：发布 / 重新推送会让 push_version +1，
 * 用户已读记录中的 read_version 小于当前 push_version 时视为未读，会再次弹窗。
 */
@Service
public class AnnouncementService {

    static final int UNREAD_LIMIT = 20;

    private final AnnouncementMapper announcementMapper;
    private final AnnouncementReadMapper announcementReadMapper;

    public AnnouncementService(AnnouncementMapper announcementMapper, AnnouncementReadMapper announcementReadMapper) {
        this.announcementMapper = announcementMapper;
        this.announcementReadMapper = announcementReadMapper;
    }

    public AdminAnnouncementPageResponse getAdminPage(int page, int pageSize, String keyword,
                                                      String category, String status) {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Announcement::getTitle, keyword.trim());
        }
        if (category != null) {
            wrapper.eq(Announcement::getCategory, category);
        }
        if (status != null) {
            wrapper.eq(Announcement::getStatus, status);
        }
        wrapper.orderByDesc(Announcement::getUpdatedAt).orderByDesc(Announcement::getId);

        Page<Announcement> result = announcementMapper.selectPage(Page.of(page, pageSize), wrapper);
        Map<Long, Long> readCounts = loadReadCounts(result.getRecords());
        List<AdminAnnouncementResponse> records = result.getRecords().stream()
                .map(a -> AdminAnnouncementResponse.from(a, readCounts.getOrDefault(a.getId(), 0L)))
                .toList();
        return new AdminAnnouncementPageResponse(records, result.getTotal(), result.getCurrent(),
                result.getSize(), result.getPages(), loadStats());
    }

    private AnnouncementStats loadStats() {
        long published = 0;
        long draft = 0;
        long offline = 0;
        for (Map<String, Object> row : announcementMapper.countByStatus()) {
            Object status = row.get("status");
            long count = row.get("cnt") instanceof Number n ? n.longValue() : 0L;
            if (Announcement.STATUS_PUBLISHED.equals(status)) {
                published = count;
            } else if (Announcement.STATUS_DRAFT.equals(status)) {
                draft = count;
            } else if (Announcement.STATUS_OFFLINE.equals(status)) {
                offline = count;
            }
        }
        return new AnnouncementStats(published + draft + offline, published, draft, offline);
    }

    @Transactional
    public AdminAnnouncementResponse create(AnnouncementSaveRequest request, Long adminId) {
        Announcement announcement = new Announcement();
        applyContent(announcement, request);
        announcement.setStatus(Announcement.STATUS_DRAFT);
        announcement.setPushVersion(0);
        announcement.setCreatedBy(adminId);
        announcementMapper.insert(announcement);
        return AdminAnnouncementResponse.from(announcement, 0L);
    }

    /**
     * 编辑内容不会改变推送版本，已读用户不会再次弹窗
     */
    @Transactional
    public AdminAnnouncementResponse update(Long id, AnnouncementSaveRequest request) {
        Announcement announcement = requireAnnouncement(id);
        applyContent(announcement, request);
        save(announcement);
        return toAdminResponse(announcement);
    }

    @Transactional
    public AdminAnnouncementResponse publish(Long id) {
        Announcement announcement = requireAnnouncement(id);
        if (Announcement.STATUS_PUBLISHED.equals(announcement.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, 40920, "公告已发布，如需再次提醒请使用重新推送");
        }
        announcement.setStatus(Announcement.STATUS_PUBLISHED);
        bumpPushVersion(announcement);
        save(announcement);
        return toAdminResponse(announcement);
    }

    @Transactional
    public AdminAnnouncementResponse offline(Long id) {
        Announcement announcement = requireAnnouncement(id);
        if (!Announcement.STATUS_PUBLISHED.equals(announcement.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, 40920, "只有已发布的公告可以下线");
        }
        announcement.setStatus(Announcement.STATUS_OFFLINE);
        save(announcement);
        return toAdminResponse(announcement);
    }

    @Transactional
    public AdminAnnouncementResponse repush(Long id) {
        Announcement announcement = requireAnnouncement(id);
        if (!Announcement.STATUS_PUBLISHED.equals(announcement.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, 40920, "只有已发布的公告可以重新推送");
        }
        bumpPushVersion(announcement);
        save(announcement);
        return toAdminResponse(announcement);
    }

    @Transactional
    public void delete(Long id) {
        requireAnnouncement(id);
        announcementMapper.deleteById(id);
    }

    public List<AnnouncementResponse> getUnread(Long userId) {
        return announcementMapper.selectUnreadByUser(userId, UNREAD_LIMIT).stream()
                .map(AnnouncementResponse::from)
                .toList();
    }

    /**
     * 只对仍处于发布状态的公告记录已读，记录的是其当前推送版本
     */
    @Transactional
    public void markRead(Long userId, List<Long> ids) {
        List<Long> distinctIds = List.copyOf(new LinkedHashSet<>(ids));
        List<Announcement> published = announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                .in(Announcement::getId, distinctIds)
                .eq(Announcement::getStatus, Announcement.STATUS_PUBLISHED));
        for (Announcement announcement : published) {
            announcementReadMapper.upsertRead(announcement.getId(), userId, announcement.getPushVersion());
        }
    }

    public AnnouncementPageResponse getPublishedPage(int page, int pageSize, String category) {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, Announcement.STATUS_PUBLISHED);
        if (category != null) {
            wrapper.eq(Announcement::getCategory, category);
        }
        wrapper.orderByDesc(Announcement::getPublishedAt).orderByDesc(Announcement::getId);
        Page<Announcement> result = announcementMapper.selectPage(Page.of(page, pageSize), wrapper);
        List<AnnouncementResponse> records = result.getRecords().stream()
                .map(AnnouncementResponse::from)
                .toList();
        return new AnnouncementPageResponse(records, result.getTotal(), result.getCurrent(),
                result.getSize(), result.getPages());
    }

    private void applyContent(Announcement announcement, AnnouncementSaveRequest request) {
        announcement.setTitle(request.getTitle().trim());
        announcement.setContent(request.getContent());
        announcement.setCategory(request.getCategory());
    }

    private void bumpPushVersion(Announcement announcement) {
        int current = announcement.getPushVersion() == null ? 0 : announcement.getPushVersion();
        announcement.setPushVersion(current + 1);
        announcement.setPublishedAt(LocalDateTime.now());
    }

    /**
     * strictUpdateFill 不会覆盖已加载实体中非空的 updatedAt，这里显式刷新
     */
    private void save(Announcement announcement) {
        announcement.setUpdatedAt(LocalDateTime.now());
        announcementMapper.updateById(announcement);
    }

    private Announcement requireAnnouncement(Long id) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40420, "公告不存在或已删除");
        }
        return announcement;
    }

    private AdminAnnouncementResponse toAdminResponse(Announcement announcement) {
        long readCount = loadReadCounts(List.of(announcement)).getOrDefault(announcement.getId(), 0L);
        return AdminAnnouncementResponse.from(announcement, readCount);
    }

    private Map<Long, Long> loadReadCounts(List<Announcement> announcements) {
        List<Long> ids = announcements.stream().map(Announcement::getId).toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : announcementReadMapper.countReadByAnnouncementIds(ids)) {
            Object id = row.get("announcementId");
            Object count = row.get("readCount");
            if (id instanceof Number idNum && count instanceof Number countNum) {
                counts.put(idNum.longValue(), countNum.longValue());
            }
        }
        return counts;
    }
}
