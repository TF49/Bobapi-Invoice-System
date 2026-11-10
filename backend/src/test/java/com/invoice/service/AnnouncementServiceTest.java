package com.invoice.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.invoice.dto.AdminAnnouncementPageResponse;
import com.invoice.dto.AdminAnnouncementResponse;
import com.invoice.dto.AnnouncementResponse;
import com.invoice.dto.AnnouncementSaveRequest;
import com.invoice.entity.Announcement;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.AnnouncementMapper;
import com.invoice.mapper.AnnouncementReadMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock
    private AnnouncementMapper announcementMapper;

    @Mock
    private AnnouncementReadMapper announcementReadMapper;

    private AnnouncementService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Announcement.class);
    }

    @BeforeEach
    void setUp() {
        service = new AnnouncementService(announcementMapper, announcementReadMapper);
    }

    @Test
    void createsDraftWithZeroPushVersion() {
        AdminAnnouncementResponse response = service.create(request("  新功能上线  "), 9L);

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementMapper).insert(captor.capture());
        Announcement saved = captor.getValue();
        assertEquals("新功能上线", saved.getTitle());
        assertEquals(Announcement.STATUS_DRAFT, saved.getStatus());
        assertEquals(0, saved.getPushVersion());
        assertEquals(9L, saved.getCreatedBy());
        assertNull(saved.getPublishedAt());
        assertEquals("DRAFT", response.status());
    }

    @Test
    void updateKeepsPushVersionSoReadersAreNotNotifiedAgain() {
        Announcement existing = announcement(1L, Announcement.STATUS_PUBLISHED, 3);
        when(announcementMapper.selectById(1L)).thenReturn(existing);

        AdminAnnouncementResponse response = service.update(1L, request("修正标题"));

        assertEquals(3, response.pushVersion());
        assertEquals("修正标题", response.title());
        verify(announcementMapper).updateById(existing);
    }

    @Test
    void publishBumpsPushVersionFromDraftAndOffline() {
        Announcement draft = announcement(1L, Announcement.STATUS_DRAFT, 0);
        Announcement offline = announcement(2L, Announcement.STATUS_OFFLINE, 2);
        when(announcementMapper.selectById(1L)).thenReturn(draft);
        when(announcementMapper.selectById(2L)).thenReturn(offline);

        AdminAnnouncementResponse first = service.publish(1L);
        AdminAnnouncementResponse second = service.publish(2L);

        assertEquals("PUBLISHED", first.status());
        assertEquals(1, first.pushVersion());
        assertNotNull(first.publishedAt());
        assertEquals(3, second.pushVersion());
    }

    @Test
    void rejectsPublishingAlreadyPublishedAnnouncement() {
        when(announcementMapper.selectById(1L)).thenReturn(announcement(1L, Announcement.STATUS_PUBLISHED, 1));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.publish(1L));
        assertEquals(40920, ex.getCode());
        verify(announcementMapper, never()).updateById(any(Announcement.class));
    }

    @Test
    void repushOnlyAllowedWhilePublished() {
        Announcement published = announcement(1L, Announcement.STATUS_PUBLISHED, 1);
        published.setPublishedAt(LocalDateTime.now().minusDays(3));
        LocalDateTime previous = published.getPublishedAt();
        when(announcementMapper.selectById(1L)).thenReturn(published);
        when(announcementMapper.selectById(2L)).thenReturn(announcement(2L, Announcement.STATUS_DRAFT, 0));

        AdminAnnouncementResponse response = service.repush(1L);
        assertEquals(2, response.pushVersion());
        assertTrue(response.publishedAt().isAfter(previous));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.repush(2L));
        assertEquals(40920, ex.getCode());
    }

    @Test
    void offlineOnlyAllowedWhilePublished() {
        when(announcementMapper.selectById(1L)).thenReturn(announcement(1L, Announcement.STATUS_PUBLISHED, 1));
        when(announcementMapper.selectById(2L)).thenReturn(announcement(2L, Announcement.STATUS_DRAFT, 0));

        assertEquals("OFFLINE", service.offline(1L).status());
        assertThrows(BusinessException.class, () -> service.offline(2L));
    }

    @Test
    void missingAnnouncementIsNotFound() {
        when(announcementMapper.selectById(404L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(404L));
        assertEquals(40420, ex.getCode());
        verify(announcementMapper, never()).deleteById(anyLong());
    }

    @Test
    void marksOnlyPublishedAnnouncementsAtTheirCurrentVersion() {
        Announcement published = announcement(5L, Announcement.STATUS_PUBLISHED, 4);
        when(announcementMapper.selectList(any(Wrapper.class))).thenReturn(List.of(published));

        service.markRead(7L, List.of(5L, 5L, 6L));

        verify(announcementReadMapper).upsertRead(5L, 7L, 4);
        verify(announcementReadMapper, never()).upsertRead(6L, 7L, 0);
    }

    @Test
    void returnsUnreadAnnouncementsWithLimit() {
        when(announcementMapper.selectUnreadByUser(7L, AnnouncementService.UNREAD_LIMIT))
                .thenReturn(List.of(announcement(5L, Announcement.STATUS_PUBLISHED, 1)));

        List<AnnouncementResponse> unread = service.getUnread(7L);

        assertEquals(1, unread.size());
        assertEquals(5L, unread.get(0).id());
    }

    @Test
    void adminPageIncludesReadCounts() {
        Page<Announcement> page = Page.of(1, 10);
        page.setRecords(List.of(
                announcement(1L, Announcement.STATUS_PUBLISHED, 1),
                announcement(2L, Announcement.STATUS_DRAFT, 0)));
        page.setTotal(2);
        when(announcementMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        when(announcementReadMapper.countReadByAnnouncementIds(List.of(1L, 2L)))
                .thenReturn(List.of(Map.of("announcementId", 1L, "readCount", 12L)));
        when(announcementMapper.countByStatus()).thenReturn(List.of(
                Map.of("status", "PUBLISHED", "cnt", 3L),
                Map.of("status", "DRAFT", "cnt", 2L)));

        AdminAnnouncementPageResponse response = service.getAdminPage(1, 10, " 上线 ", "FEATURE", null);

        assertEquals(2, response.total());
        assertEquals(12L, response.records().get(0).readCount());
        assertEquals(0L, response.records().get(1).readCount());
        assertEquals(5L, response.stats().total());
        assertEquals(3L, response.stats().published());
        assertEquals(2L, response.stats().draft());
        assertEquals(0L, response.stats().offline());
    }

    private AnnouncementSaveRequest request(String title) {
        AnnouncementSaveRequest request = new AnnouncementSaveRequest();
        request.setTitle(title);
        request.setContent("## 更新内容\n- 支持批量导入");
        request.setCategory("FEATURE");
        return request;
    }

    private Announcement announcement(Long id, String status, int pushVersion) {
        Announcement a = new Announcement();
        a.setId(id);
        a.setTitle("公告" + id);
        a.setContent("内容");
        a.setCategory("FEATURE");
        a.setStatus(status);
        a.setPushVersion(pushVersion);
        a.setCreatedBy(1L);
        a.setCreatedAt(LocalDateTime.now().minusDays(5));
        a.setUpdatedAt(LocalDateTime.now().minusDays(5));
        a.setDeleted(0);
        return a;
    }
}
