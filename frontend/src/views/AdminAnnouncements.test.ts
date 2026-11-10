// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminAnnouncements from './AdminAnnouncements.vue'
import {
  announcementApi,
  type AdminAnnouncement,
  type AdminAnnouncementPage
} from '@/api/announcement'

vi.mock('@/api/announcement', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/announcement')>()
  return {
    ...actual,
    announcementApi: {
      getAdminList: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      publish: vi.fn(),
      offline: vi.fn(),
      repush: vi.fn(),
      remove: vi.fn()
    }
  }
})

const draft: AdminAnnouncement = {
  id: 1,
  title: '批量导入即将上线',
  content: '草稿内容',
  category: 'FEATURE',
  status: 'DRAFT',
  pushVersion: 0,
  publishedAt: null,
  createdAt: '2026-11-09T10:00:00',
  updatedAt: '2026-11-09T10:00:00',
  readCount: 0
}

const published: AdminAnnouncement = {
  id: 2,
  title: '周末系统维护',
  content: '维护内容',
  category: 'MAINTENANCE',
  status: 'PUBLISHED',
  pushVersion: 2,
  publishedAt: '2026-11-10T08:00:00',
  createdAt: '2026-11-08T10:00:00',
  updatedAt: '2026-11-10T08:00:00',
  readCount: 37
}

const pageResult: AdminAnnouncementPage = {
  records: [draft, published],
  total: 2,
  page: 1,
  pageSize: 10,
  totalPages: 1,
  stats: { total: 7, published: 4, draft: 2, offline: 1 }
}

const mockedApi = vi.mocked(announcementApi)
let wrapper: VueWrapper | null = null

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/admin/announcements', component: AdminAnnouncements }]
  })
  await router.push('/admin/announcements')
  await router.isReady()

  wrapper = mount(AdminAnnouncements, {
    attachTo: document.body,
    global: {
      plugins: [createPinia(), router, ElementPlus],
      stubs: {
        AppHeader: { template: '<header>公告管理</header>' },
        AnimatedContent: { template: '<section><slot /></section>' },
        SpotlightCard: { template: '<div><slot /></div>' },
        CountUp: { props: ['value'], template: '<span class="count-up">{{ value }}</span>' }
      }
    }
  })
  await flushPromises()
  return wrapper
}

function rowButton(page: VueWrapper, rowIndex: number, text: string) {
  const rows = page.findAll('.el-table__body-wrapper tbody tr')
  return rows[rowIndex].findAll('button').find((button) => button.text() === text)
}

async function runMoreCommand(page: VueWrapper, rowIndex: number, command: 'offline' | 'delete') {
  const dropdowns = page.findAllComponents({ name: 'ElDropdown' })
  dropdowns[rowIndex].vm.$emit('command', command)
  await flushPromises()
}

function dialogButton(text: string) {
  return [...document.body.querySelectorAll<HTMLButtonElement>('.announcement-editor-dialog button')]
    .find((button) => button.textContent?.trim() === text)
}

function fillEditor(title: string, content: string) {
  const titleInput = document.body.querySelector<HTMLInputElement>('.announcement-editor-dialog input[maxlength="100"]')!
  titleInput.value = title
  titleInput.dispatchEvent(new Event('input', { bubbles: true }))
  const textarea = document.body.querySelector<HTMLTextAreaElement>('.announcement-editor-dialog textarea')!
  textarea.value = content
  textarea.dispatchEvent(new Event('input', { bubbles: true }))
}

describe('AdminAnnouncements', () => {
  beforeEach(() => {
    mockedApi.getAdminList.mockResolvedValue(pageResult)
    mockedApi.create.mockImplementation(async (payload) => ({ ...draft, id: 9, ...payload }))
    mockedApi.update.mockImplementation(async (id, payload) => ({ ...published, id, ...payload }))
    mockedApi.publish.mockImplementation(async (id) => ({ ...draft, id, status: 'PUBLISHED', pushVersion: 1 }))
    mockedApi.repush.mockResolvedValue({ ...published, pushVersion: 3 })
    mockedApi.offline.mockResolvedValue({ ...published, status: 'OFFLINE' })
    mockedApi.remove.mockResolvedValue(undefined)
    vi.spyOn(ElMessage, 'success').mockImplementation(() => undefined as never)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('shows overall statistics from the list response', async () => {
    const page = await mountPage()

    expect(page.findAll('.count-up').map((n) => n.text())).toEqual(['7', '4', '2', '1'])
  })

  it('lists announcements with status, read count and state-specific actions', async () => {
    const page = await mountPage()

    expect(mockedApi.getAdminList).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 10 }))
    expect(page.text()).toContain('批量导入即将上线')
    expect(page.text()).toContain('草稿')
    expect(page.text()).toContain('37')
    expect(page.text()).toContain('第 2 次推送')
    expect(rowButton(page, 0, '发布')).toBeDefined()
    expect(rowButton(page, 0, '重新推送')).toBeUndefined()
    expect(rowButton(page, 1, '重新推送')).toBeDefined()
    expect(rowButton(page, 1, '发布')).toBeUndefined()
    expect(page.text()).toContain('草稿内容')
  })

  it('sends the keyword filter to the server', async () => {
    const page = await mountPage()
    const search = page.find('input[placeholder="搜索公告标题"]')
    await search.setValue('维护')
    await search.trigger('keyup.enter')
    await flushPromises()

    expect(mockedApi.getAdminList).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, keyword: '维护' }))
  })

  it('creates a draft from the editor with a live preview', async () => {
    const page = await mountPage()
    await page.findAll('button').find((b) => b.text() === '新建公告')!.trigger('click')
    await flushPromises()

    fillEditor('  开票提速  ', '## 更新\n- 开票更快')
    await flushPromises()
    const preview = document.body.querySelector('.editor-preview .announcement-markdown')?.textContent
    expect(document.body.querySelector('.editor-preview .ann-card-title')?.textContent).toBe('开票提速')
    expect(preview).toContain('更新')
    expect(preview).toContain('开票更快')
    expect(preview).not.toContain('##')

    dialogButton('保存草稿')!.click()
    await flushPromises()

    expect(mockedApi.create).toHaveBeenCalledWith({
      title: '开票提速',
      content: '## 更新\n- 开票更快',
      category: 'FEATURE'
    })
    expect(mockedApi.publish).not.toHaveBeenCalled()
  })

  it('can save and publish in one step', async () => {
    const page = await mountPage()
    await page.findAll('button').find((b) => b.text() === '新建公告')!.trigger('click')
    await flushPromises()

    fillEditor('新功能', '内容')
    await flushPromises()
    dialogButton('保存并发布')!.click()
    await flushPromises()

    expect(mockedApi.create).toHaveBeenCalled()
    expect(mockedApi.publish).toHaveBeenCalledWith(9)
  })

  it('does not submit an empty form', async () => {
    const page = await mountPage()
    await page.findAll('button').find((b) => b.text() === '新建公告')!.trigger('click')
    await flushPromises()

    dialogButton('保存草稿')!.click()
    await flushPromises()

    expect(mockedApi.create).not.toHaveBeenCalled()
  })

  it('edits a published announcement without republishing it', async () => {
    const page = await mountPage()
    await rowButton(page, 1, '编辑')!.trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('修改已发布的公告不会再次弹窗')
    expect(dialogButton('保存并发布')).toBeUndefined()

    fillEditor('周末系统维护（时间调整）', '维护内容')
    await flushPromises()
    dialogButton('保存修改')!.click()
    await flushPromises()

    expect(mockedApi.update).toHaveBeenCalledWith(2, expect.objectContaining({ title: '周末系统维护（时间调整）' }))
    expect(mockedApi.publish).not.toHaveBeenCalled()
  })

  it('confirms before publishing, repushing, taking offline and deleting', async () => {
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue(undefined as never)
    const page = await mountPage()

    await rowButton(page, 0, '发布')!.trigger('click')
    await flushPromises()
    expect(mockedApi.publish).toHaveBeenCalledWith(1)

    await rowButton(page, 1, '重新推送')!.trigger('click')
    await flushPromises()
    expect(mockedApi.repush).toHaveBeenCalledWith(2)

    await runMoreCommand(page, 1, 'offline')
    expect(mockedApi.offline).toHaveBeenCalledWith(2)

    await runMoreCommand(page, 1, 'delete')
    expect(mockedApi.remove).toHaveBeenCalledWith(2)

    expect(confirm).toHaveBeenCalledTimes(4)
  })

  it('does nothing when the confirmation is cancelled', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')
    const page = await mountPage()

    await rowButton(page, 1, '重新推送')!.trigger('click')
    await flushPromises()

    expect(mockedApi.repush).not.toHaveBeenCalled()
  })

  it('applies markdown formatting from the toolbar to the selected text', async () => {
    const page = await mountPage()
    await page.findAll('button').find((b) => b.text() === '新建公告')!.trigger('click')
    await flushPromises()

    fillEditor('标题', '重要内容')
    await flushPromises()
    const textarea = document.body.querySelector<HTMLTextAreaElement>('.announcement-editor-dialog textarea')!
    textarea.setSelectionRange(0, 2)
    document.body.querySelector<HTMLButtonElement>('[aria-label="加粗（Ctrl+B）"]')!.click()
    await flushPromises()
    expect(textarea.value).toBe('**重要**内容')

    textarea.setSelectionRange(0, 0)
    document.body.querySelector<HTMLButtonElement>('[aria-label="无序列表"]')!.click()
    await flushPromises()
    expect(textarea.value).toBe('- **重要**内容')
  })
})
