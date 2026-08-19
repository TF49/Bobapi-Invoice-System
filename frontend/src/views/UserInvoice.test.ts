// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UserInvoice from './UserInvoice.vue'
import { invoiceApi, type Invoice } from '@/api/invoice'

vi.mock('@/api/invoice', () => ({
  invoiceApi: {
    getMyInvoices: vi.fn(),
    createInvoice: vi.fn(),
    previewInvoice: vi.fn(),
    downloadInvoice: vi.fn()
  }
}))

const mockedApi = vi.mocked(invoiceApi)
let wrapper: VueWrapper | null = null

const completedInvoice: Invoice = {
  id: 1,
  companyName: '测试公司',
  taxNumber: '91410100MAE5H38A0F',
  amount: 300.01,
  status: 'COMPLETED',
  userId: 2,
  createdAt: '2026-08-19T21:52:00',
  updatedAt: '2026-08-19T21:53:00',
  downloadable: true,
  fileExists: true,
  fileName: 'invoice.png'
}

async function mountPage() {
  wrapper = mount(UserInvoice, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      stubs: {
        AppHeader: { template: '<header>我的发票</header>' },
        AnimatedContent: { template: '<section><slot /></section>' },
        SpotlightCard: { template: '<div><slot /></div>' },
        CountUp: { props: ['value', 'decimals', 'prefix'], template: '<span>{{ prefix }}{{ value }}</span>' },
        InvoiceBatchImportDialog: { template: '<div />' }
      }
    }
  })
  await flushPromises()
  return wrapper
}

describe('UserInvoice', () => {
  beforeEach(() => {
    mockedApi.getMyInvoices.mockResolvedValue([])
    mockedApi.createInvoice.mockResolvedValue({} as never)
    vi.stubGlobal('crypto', {
      getRandomValues: (values: Uint32Array) => {
        values.set([11, 22, 33, 44])
        return values
      }
    })
    vi.spyOn(ElMessage, 'success').mockImplementation(() => undefined as never)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('submits successfully when randomUUID is unavailable', async () => {
    const page = await mountPage()
    const inputs = page.findAll('input')

    await inputs[0].setValue(' 测试公司 ')
    await inputs[1].setValue('91410100MAE5H38A0F')
    await inputs[2].setValue('300.01')

    const submit = page.findAll('button').find(button => button.text().includes('提交申请'))
    expect(submit).toBeDefined()
    await submit!.trigger('click')
    await flushPromises()

    expect(mockedApi.createInvoice).toHaveBeenCalledWith(
      {
        companyName: '测试公司',
        taxNumber: '91410100MAE5H38A0F',
        amount: 300.01
      },
      expect.stringMatching(/^invoice-[a-z0-9]+-[a-z0-9]+$/)
    )
    expect(ElMessage.success).toHaveBeenCalledWith('提交成功')
    expect(mockedApi.getMyInvoices).toHaveBeenCalledTimes(2)
  })

  it('renders aligned record actions for desktop and mobile layouts', async () => {
    mockedApi.getMyInvoices.mockResolvedValue([completedInvoice])

    const page = await mountPage()

    expect(page.find('.result-count').text()).toContain('1 条记录')
    expect(page.find('.invoice-id').text()).toBe('#0001')
    expect(page.findAll('.record-actions button')).toHaveLength(2)
    expect(page.findAll('.mobile-record-actions button')).toHaveLength(2)
    expect(page.find('.mobile-records').text()).toContain('¥300.01')
  })
})
