// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminInvoice from './AdminInvoice.vue'
import { invoiceApi, type Invoice } from '@/api/invoice'
import { useUserStore } from '@/stores/user'

vi.mock('@/api/invoice', () => ({
  invoiceApi: {
    getAllInvoices: vi.fn(),
    uploadInvoice: vi.fn(),
    downloadInvoice: vi.fn(),
    previewInvoice: vi.fn()
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
})
