// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia, type Pinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AnnouncementPopup from './AnnouncementPopup.vue'
import { announcementApi, type Announcement } from '@/api/announcement'
import { useAnnouncementStore } from '@/stores/announcement'

vi.mock('@/api/announcement', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/announcement')>()
  return {
    ...actual,
    announcementApi: {
      getUnread: vi.fn(),
      markRead: vi.fn()
    }
  }
})

const mockedApi = vi.mocked(announcementApi)
let wrapper: VueWrapper | null = null
let pinia: Pinia

function item(id: number, content = `正文 ${id}`): Announcement {
  return {
    id,
    title: `公告标题 ${id}`,
    content,
    category: id === 1 ? 'FEATURE' : 'MAINTENANCE',
    status: 'PUBLISHED',
    pushVersion: 1,
    publishedAt: '2026-11-10T09:30:00',
    createdAt: '2026-11-10T09:00:00',
    updatedAt: '2026-11-10T09:00:00'
  }
}

async function mountWith(list: Announcement[]) {
  pinia = createPinia()
  setActivePinia(pinia)
  mockedApi.getUnread.mockResolvedValue(list)
  wrapper = mount(AnnouncementPopup, {
    attachTo: document.body,
    global: { plugins: [pinia, ElementPlus] }
  })
  await useAnnouncementStore().checkUnread('token')
  await flushPromises()
  return useAnnouncementStore()
}

function dialog() {
  return document.body.querySelector<HTMLElement>('.announcement-popup')
}

function buttonByText(text: string) {
  return [...document.body.querySelectorAll<HTMLButtonElement>('.announcement-popup button')]
    .find((button) => button.textContent?.trim() === text)
}

async function closeDialog() {
  const store = useAnnouncementStore()
  buttonByText('我知道了')?.click()
  await flushPromises()
  // el-dialog 的 closed 事件依赖过渡动画结束，测试中直接触发
  wrapper!.findComponent({ name: 'ElDialog' }).vm.$emit('closed')
  await flushPromises()
  return store
}

describe('AnnouncementPopup', () => {
  beforeEach(() => {
    mockedApi.markRead.mockResolvedValue(undefined)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  it('does not render a dialog when there is nothing unread', async () => {
    await mountWith([])

    expect(dialog()).toBeNull()
  })

  it('shows a single announcement with rendered markdown and category', async () => {
    await mountWith([item(1, '## 版本说明\n- 批量导入')])

    expect(dialog()?.textContent).toContain('公告标题 1')
    expect(dialog()?.textContent).toContain('新功能')
    expect(dialog()?.textContent).toContain('2026-11-10 09:30')
    // Markdown 结构（h2/li 等）由 jsdom 下的 markdown.test.ts 覆盖；happy-dom 中 DOMPurify 会丢失结构，只校验文本
    const content = dialog()?.querySelector('[data-testid="announcement-content"]')?.textContent
    expect(content).toContain('版本说明')
    expect(content).toContain('批量导入')
    expect(content).not.toContain('##')
    expect(buttonByText('上一条')).toBeUndefined()
    expect(buttonByText('我知道了')).toBeDefined()
  })

  it('pages through multiple announcements', async () => {
    await mountWith([item(1), item(2), item(3)])

    expect(dialog()?.textContent).toContain('第 1 / 3 条公告')
    expect(buttonByText('上一条')?.disabled).toBe(true)

    buttonByText('下一条')!.click()
    await flushPromises()
    expect(dialog()?.textContent).toContain('公告标题 2')
    expect(dialog()?.textContent).toContain('第 2 / 3 条公告')

    buttonByText('上一条')!.click()
    await flushPromises()
    expect(dialog()?.textContent).toContain('公告标题 1')

    buttonByText('下一条')!.click()
    await flushPromises()
    buttonByText('下一条')!.click()
    await flushPromises()
    expect(dialog()?.textContent).toContain('公告标题 3')
    expect(buttonByText('我知道了')).toBeDefined()
  })

  it('marks every viewed announcement as read when closed', async () => {
    await mountWith([item(1), item(2)])
    buttonByText('下一条')!.click()
    await flushPromises()

    const store = await closeDialog()

    expect(mockedApi.markRead).toHaveBeenCalledWith([1, 2])
    expect(store.popupVisible).toBe(false)
    expect(store.unread).toHaveLength(0)
  })

  it('leaves announcements that were never viewed unread', async () => {
    await mountWith([item(1), item(2), item(3)])

    const store = useAnnouncementStore()
    store.popupVisible = false
    await flushPromises()
    wrapper!.findComponent({ name: 'ElDialog' }).vm.$emit('closed')
    await flushPromises()

    expect(mockedApi.markRead).toHaveBeenCalledWith([1])
    expect(store.unread.map((a) => a.id)).toEqual([2, 3])
  })
})
