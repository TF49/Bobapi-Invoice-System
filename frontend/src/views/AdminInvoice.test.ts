// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminInvoice from './AdminInvoice.vue'
import { invoiceApi, type Invoice } from '@/api/invoice'
import { useUserStore } from '@/stores/user'

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: vi.fn(() => ({ query: {} })),
    useRouter: vi.fn(() => ({ push: vi.fn() }))
  }
})

vi.mock('@/api/invoice', () => ({
  invoiceApi: {
    getAllInvoices: vi.fn(),
    uploadInvoice: vi.fn(),
    downloadInvoice: vi.fn(),
    previewInvoice: vi.fn(),
    updateInvoice: vi.fn(),
    updateProcessed: vi.fn(),
    batchUpdateProcessed: vi.fn(),
    cancelInvoice: vi.fn(),
    confirmRedFlush: vi.fn(),
    rejectRedFlush: vi.fn(),
    getPendingRedFlushCount: vi.fn()
  }
}))

vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn()
}))

const mockedInvoiceApi = vi.mocked(invoiceApi)
const mockedUseUserStore = vi.mocked(useUserStore)
let wrapper: VueWrapper | null = null

const pendingInvoice: Invoice = {
  id: 101,
  companyName: '开票测试企业',
  taxNumber: '91410100MAE5H38A0F',
  amount: 500,
  invoiceType: '技术服务费',
  remark: '请尽快处理',
  status: 'PENDING',
  userId: 10,
  username: 'test_user',
  createdAt: '2026-08-20T10:00:00',
  updatedAt: '2026-08-20T10:00:00',
  downloadable: false,
  fileExists: false
}

async function mountPage() {
  wrapper = mount(AdminInvoice, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      stubs: {
        AppHeader: { template: '<header>管理控制台</header>' },
        AnimatedContent: { template: '<div><slot /></div>' },
        SpotlightCard: { template: '<div><slot /></div>' },
        CountUp: { props: ['value', 'prefix', 'decimals'], template: '<span>{{ prefix }}{{ value }}</span>' }
      }
    }
  })
  await flushPromises()
  return wrapper
}

describe('AdminInvoice', () => {
  beforeEach(() => {
    mockedUseUserStore.mockReturnValue({
      user: { id: 1, username: 'admin', role: 'ADMIN' }
    } as never)
    mockedInvoiceApi.getAllInvoices.mockResolvedValue([pendingInvoice])
    mockedInvoiceApi.uploadInvoice.mockResolvedValue({} as never)

    vi.spyOn(ElMessage, 'success').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'info').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'error').mockImplementation(() => undefined as never)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('directly uploads clipboard image when paste button is clicked', async () => {
    const fakeBlob = new Blob(['dummy image'], { type: 'image/png' })
    const clipboardItem = {
      types: ['image/png'],
      getType: vi.fn().mockResolvedValue(fakeBlob)
    }

    vi.stubGlobal('navigator', {
      clipboard: {
        read: vi.fn().mockResolvedValue([clipboardItem])
      }
    })

    const page = await mountPage()
    const pasteZone = page.find('.paste-zone')
    expect(pasteZone.exists()).toBe(true)

    await pasteZone.trigger('click')
    await flushPromises()

    expect(mockedInvoiceApi.uploadInvoice).toHaveBeenCalledWith(101, expect.any(File))
    expect(ElMessage.success).toHaveBeenCalledWith('上传成功')
  })

  it('shows warning when clipboard contains no image on button click', async () => {
    const clipboardItem = {
      types: ['text/plain'],
      getType: vi.fn().mockResolvedValue(new Blob(['hello'], { type: 'text/plain' }))
    }

    vi.stubGlobal('navigator', {
      clipboard: {
        read: vi.fn().mockResolvedValue([clipboardItem])
      }
    })

    const page = await mountPage()
    const pasteZone = page.find('.paste-zone')
    await pasteZone.trigger('click')
    await flushPromises()

    expect(mockedInvoiceApi.uploadInvoice).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('剪贴板中没有可用的 JPG 或 PNG 图片，请先截图或复制图片')
  })

  it('shows fallback info when clipboard read permission is rejected', async () => {
    vi.stubGlobal('navigator', {
      clipboard: {
        read: vi.fn().mockRejectedValue(new Error('NotAllowedError: Read permission denied'))
      }
    })

    const page = await mountPage()
    const pasteZone = page.find('.paste-zone')
    await pasteZone.trigger('click')
    await flushPromises()

    expect(mockedInvoiceApi.uploadInvoice).not.toHaveBeenCalled()
    expect(ElMessage.info).toHaveBeenCalledWith('未获得剪贴板权限或自动粘贴受限，请按 Ctrl+V 粘贴图片')
  })

  it('renders pagination and paginates items when multiple pages exist', async () => {
    const manyInvoices: Invoice[] = Array.from({ length: 25 }, (_, i) => ({
      ...pendingInvoice,
      id: i + 1,
      companyName: `管理员测试公司_${i + 1}`
    }))
    mockedInvoiceApi.getAllInvoices.mockResolvedValue(manyInvoices)

    const page = await mountPage()
    expect(page.find('.result-count').text()).toContain('共 25 条')
    expect(page.find('.pagination-bar').exists()).toBe(true)
    // 默认每页 10 条
    expect(page.text()).toContain('管理员测试公司_1')
    expect(page.text()).toContain('管理员测试公司_10')
    expect(page.text()).not.toContain('管理员测试公司_11')
  })

  it('renders danger tag for non-default invoiceType and warning text for remark', async () => {
    mockedInvoiceApi.getAllInvoices.mockResolvedValue([
      {
        ...pendingInvoice,
        id: 201,
        invoiceType: '计算服务费',
        remark: '需要电子发票'
      }
    ])

    const page = await mountPage()
    const warningTag = page.find('.warning-type-tag')
    expect(warningTag.exists()).toBe(true)
    expect(warningTag.text()).toContain('计算服务费')

    const remarkWarning = page.find('.remark-warning-text')
    expect(remarkWarning.exists()).toBe(true)
    expect(remarkWarning.text()).toContain('需要电子发票')
  })

  it('initializes statusFilter from route.query.status and matches whitelist', async () => {
    const { useRoute } = await import('vue-router')
    vi.mocked(useRoute).mockReturnValue({ query: { status: 'PENDING' } } as any)
    mockedInvoiceApi.getAllInvoices.mockResolvedValue([
      pendingInvoice,
      { ...pendingInvoice, id: 102, status: 'COMPLETED', isProcessed: true }
    ])

    const page = await mountPage()
    expect(page.find('.result-count').text()).toContain('共 1 条')
    expect(page.text()).toContain('开票测试企业')
  })

  it('opens confirm red flush dialog and submits confirmation', async () => {
    const redFlushPendingInvoice: Invoice = {
      ...pendingInvoice,
      id: 301,
      status: 'COMPLETED',
      downloadable: true,
      fileExists: true,
      redFlushStatus: 'PENDING',
      redFlushReason: '金额填写错误'
    }
    mockedInvoiceApi.getAllInvoices.mockResolvedValue([redFlushPendingInvoice])
    mockedInvoiceApi.confirmRedFlush.mockResolvedValue({
      ...redFlushPendingInvoice,
      redFlushStatus: 'COMPLETED'
    } as never)

    const page = await mountPage()
    const redFlushBtn = page.find('.red-flush-btn')
    expect(redFlushBtn.exists()).toBe(true)

    await redFlushBtn.trigger('click')
    await flushPromises()

    const submitBtn = page.findAll('.dialog-footer button').find(b => b.text().includes('确认标记红冲'))
    expect(submitBtn).toBeDefined()
    await submitBtn!.trigger('click')
    await flushPromises()

    expect(mockedInvoiceApi.confirmRedFlush).toHaveBeenCalledWith(301, undefined)
    expect(ElMessage.success).toHaveBeenCalledWith('已完成发票红冲标记，额度已自动退还至用户账户')
  })

  it('opens reject red flush dialog and submits rejection', async () => {
    const redFlushPendingInvoice: Invoice = {
      ...pendingInvoice,
      id: 302,
      status: 'COMPLETED',
      downloadable: true,
      fileExists: true,
      redFlushStatus: 'PENDING',
      redFlushReason: '抬头写错'
    }
    mockedInvoiceApi.getAllInvoices.mockResolvedValue([redFlushPendingInvoice])
    mockedInvoiceApi.rejectRedFlush.mockResolvedValue({
      ...redFlushPendingInvoice,
      redFlushStatus: 'REJECTED',
      redFlushRemark: '发票已跨期入账'
    } as never)

    const page = await mountPage()
    const rejectBtn = page.find('.reject-btn')
    expect(rejectBtn.exists()).toBe(true)

    await rejectBtn.trigger('click')
    await flushPromises()

    // 填写驳回理由
    const rejectDialog = page.find('.red-flush-admin-dialog')
    expect(rejectDialog.exists()).toBe(true)
    const textarea = rejectDialog.find('textarea')
    await textarea.setValue('发票已跨期入账，无法冲红')
    await flushPromises()

    const submitBtn = page.findAll('.dialog-footer button').find(b => b.text().includes('确认驳回'))
    expect(submitBtn).toBeDefined()
    await submitBtn!.trigger('click')
    await flushPromises()

    expect(mockedInvoiceApi.rejectRedFlush).toHaveBeenCalledWith(302, '发票已跨期入账，无法冲红')
    expect(ElMessage.success).toHaveBeenCalledWith('已驳回该红冲申请')
  })

  it('renders submissionType tag and filters by submissionType', async () => {
    const apiInvoice: Invoice = {
      ...pendingInvoice,
      id: 201,
      submissionType: 'API'
    }
    const manualInvoice: Invoice = {
      ...pendingInvoice,
      id: 202,
      submissionType: 'MANUAL'
    }
    mockedInvoiceApi.getAllInvoices.mockResolvedValue([apiInvoice, manualInvoice])

    const page = await mountPage()
    expect(page.text()).toContain('API')
    expect(page.text()).toContain('手动')
  })

  it('renders golden badge with icon for VAT_SPECIAL category', async () => {
    const vatSpecialInvoice: Invoice = {
      ...pendingInvoice,
      id: 501,
      invoiceCategory: 'VAT_SPECIAL'
    }
    mockedInvoiceApi.getAllInvoices.mockResolvedValue([vatSpecialInvoice])

    const page = await mountPage()
    const badge = page.find('.vat-special-badge')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toContain('专票')
    expect(badge.find('.vat-special-ico').exists()).toBe(true)
  })

  it('renders normal badge with icon for NORMAL category in admin table', async () => {
    const normalInvoice: Invoice = {
      ...pendingInvoice,
      id: 502,
      invoiceCategory: 'NORMAL'
    }
    mockedInvoiceApi.getAllInvoices.mockResolvedValue([normalInvoice])

    const page = await mountPage()
    const badge = page.find('.normal-badge')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toContain('普票')
    expect(badge.find('.normal-ico').exists()).toBe(true)
  })

  it('filters invoices by keyword "专票"', async () => {
    const normalInvoice: Invoice = {
      ...pendingInvoice,
      id: 502,
      companyName: '普通公司',
      invoiceCategory: 'NORMAL'
    }
    const vatSpecialInvoice: Invoice = {
      ...pendingInvoice,
      id: 503,
      companyName: '专票企业A',
      invoiceCategory: 'VAT_SPECIAL'
    }
    mockedInvoiceApi.getAllInvoices.mockResolvedValue([normalInvoice, vatSpecialInvoice])

    const page = await mountPage()
    const searchInput = page.find('.search-input input')
    await searchInput.setValue('专票')
    await flushPromises()

    expect(page.text()).toContain('专票企业A')
    expect(page.text()).not.toContain('普通公司')
  })

  it('includes invoiceCategory when submitting edit form', async () => {
    const vatSpecialInvoice: Invoice = {
      ...pendingInvoice,
      id: 505,
      invoiceCategory: 'VAT_SPECIAL'
    }
    mockedInvoiceApi.getAllInvoices.mockResolvedValue([vatSpecialInvoice])
    mockedInvoiceApi.updateInvoice.mockResolvedValue({} as never)

    const page = await mountPage()
    // Open edit dialog by clicking edit button
    const editBtn = page.find('.action-btn.icon-only-btn')
    expect(editBtn.exists()).toBe(true)
    await editBtn.trigger('click')
    await flushPromises()

    // Find submit button in edit dialog
    const saveBtn = page.findAll('.dialog-footer button').find(b => b.text().includes('保存修改'))
    expect(saveBtn).toBeDefined()
    await saveBtn!.trigger('click')
    await flushPromises()

    expect(mockedInvoiceApi.updateInvoice).toHaveBeenCalledWith(505, expect.objectContaining({
      invoiceCategory: 'VAT_SPECIAL'
    }))
  })
})
