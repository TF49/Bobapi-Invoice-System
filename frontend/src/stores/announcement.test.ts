// @vitest-environment happy-dom

import { flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { announcementApi, type Announcement } from '@/api/announcement'
import { RECHECK_INTERVAL_MS, useAnnouncementStore } from './announcement'

vi.mock('@/api/announcement', () => ({
  announcementApi: {
    getUnread: vi.fn(),
    markRead: vi.fn()
  }
}))

const mockedApi = vi.mocked(announcementApi)

function item(id: number): Announcement {
  return {
    id,
    title: `公告 ${id}`,
    content: '内容',
    category: 'FEATURE',
    status: 'PUBLISHED',
    pushVersion: 1,
    publishedAt: '2026-11-10T09:00:00',
    createdAt: '2026-11-10T09:00:00',
    updatedAt: '2026-11-10T09:00:00'
  }
}

describe('announcement store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockedApi.getUnread.mockResolvedValue([item(1), item(2)])
    mockedApi.markRead.mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  it('opens the popup when there are unread announcements', async () => {
    const store = useAnnouncementStore()
    await store.checkUnread('token-a')

    expect(store.unread).toHaveLength(2)
    expect(store.popupVisible).toBe(true)
  })

  it('stays closed when nothing is unread', async () => {
    mockedApi.getUnread.mockResolvedValue([])
    const store = useAnnouncementStore()
    await store.checkUnread('token-a')

    expect(store.popupVisible).toBe(false)
  })

  it('throttles rechecks within the same session but checks immediately for a new login', async () => {
    vi.useFakeTimers()
    mockedApi.getUnread.mockResolvedValue([])
    const store = useAnnouncementStore()

    await store.checkUnread('token-a')
    await store.checkUnread('token-a')
    expect(mockedApi.getUnread).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(RECHECK_INTERVAL_MS + 1)
    await store.checkUnread('token-a')
    expect(mockedApi.getUnread).toHaveBeenCalledTimes(2)

    await store.checkUnread('token-b')
    expect(mockedApi.getUnread).toHaveBeenCalledTimes(3)
  })

  it('discards a response that arrives after logout', async () => {
    let resolve!: (value: Announcement[]) => void
    mockedApi.getUnread.mockReturnValue(new Promise((r) => { resolve = r }))
    const store = useAnnouncementStore()

    const pending = store.checkUnread('token-a')
    store.reset()
    resolve([item(1)])
    await pending
    await flushPromises()

    expect(store.unread).toHaveLength(0)
    expect(store.popupVisible).toBe(false)
  })

  it('marks announcements as read and removes them locally', async () => {
    const store = useAnnouncementStore()
    await store.checkUnread('token-a')

    await store.markRead([1, 1])

    expect(mockedApi.markRead).toHaveBeenCalledWith([1])
    expect(store.unread.map((a) => a.id)).toEqual([2])
  })

  it('skips the request when there is nothing to mark', async () => {
    const store = useAnnouncementStore()
    await store.markRead([])

    expect(mockedApi.markRead).not.toHaveBeenCalled()
  })
})
