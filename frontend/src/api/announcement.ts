import request from '@/utils/request'

export type AnnouncementCategory = 'FEATURE' | 'MAINTENANCE' | 'IMPORTANT' | 'GENERAL'
export type AnnouncementStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE'

export interface Announcement {
  id: number
  title: string
  /** Markdown 正文，展示前必须经过 renderMarkdown 过滤 */
  content: string
  category: AnnouncementCategory
  status: AnnouncementStatus
  pushVersion: number
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminAnnouncement extends Announcement {
  /** 当前推送版本下的已读人数 */
  readCount: number
}

export interface AnnouncementPage<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export interface AnnouncementStats {
  total: number
  published: number
  draft: number
  offline: number
}

export interface AdminAnnouncementPage extends AnnouncementPage<AdminAnnouncement> {
  stats: AnnouncementStats
}

export interface AnnouncementSavePayload {
  title: string
  content: string
  category: AnnouncementCategory
}

export interface AdminAnnouncementQuery {
  page: number
  pageSize: number
  keyword?: string
  category?: AnnouncementCategory
  status?: AnnouncementStatus
}

export interface AnnouncementHistoryQuery {
  page: number
  pageSize: number
  category?: AnnouncementCategory
}

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

export const ANNOUNCEMENT_CATEGORIES: Record<AnnouncementCategory, { label: string; tag: TagType }> = {
  FEATURE: { label: '新功能', tag: 'success' },
  MAINTENANCE: { label: '系统维护', tag: 'warning' },
  IMPORTANT: { label: '重要通知', tag: 'danger' },
  GENERAL: { label: '一般通知', tag: 'info' }
}

export const ANNOUNCEMENT_STATUSES: Record<AnnouncementStatus, { label: string; tag: TagType }> = {
  DRAFT: { label: '草稿', tag: 'info' },
  PUBLISHED: { label: '已发布', tag: 'success' },
  OFFLINE: { label: '已下线', tag: 'warning' }
}

/** 将后端 LocalDateTime（如 2026-11-10T12:30:45）格式化为 2026-11-10 12:30 */
export function formatAnnouncementTime(value: string | null | undefined): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

export const announcementApi = {
  // ---- 管理员 ----
  getAdminList(params: AdminAnnouncementQuery) {
    return request.get<any, AdminAnnouncementPage>('/announcements/admin', { params })
  },
  create(data: AnnouncementSavePayload) {
    return request.post<any, AdminAnnouncement>('/announcements/admin', data)
  },
  update(id: number, data: AnnouncementSavePayload) {
    return request.put<any, AdminAnnouncement>(`/announcements/admin/${id}`, data)
  },
  publish(id: number) {
    return request.put<any, AdminAnnouncement>(`/announcements/admin/${id}/publish`)
  },
  offline(id: number) {
    return request.put<any, AdminAnnouncement>(`/announcements/admin/${id}/offline`)
  },
  repush(id: number) {
    return request.post<any, AdminAnnouncement>(`/announcements/admin/${id}/repush`)
  },
  remove(id: number) {
    return request.delete<any, void>(`/announcements/admin/${id}`)
  },

  // ---- 所有登录用户 ----
  getUnread() {
    return request.get<any, Announcement[]>('/announcements/unread')
  },
  markRead(ids: number[]) {
    return request.post<any, void>('/announcements/read', { ids })
  },
  getHistory(params: AnnouncementHistoryQuery) {
    return request.get<any, AnnouncementPage<Announcement>>('/announcements', { params })
  }
}
