// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TaxFlowDrawer from './TaxFlowDrawer.vue'
import { invoiceFlowApi, type TaxFlowResult } from '@/api/invoiceFlow'

vi.mock('@/api/invoiceFlow', async () => {
  const actual = await vi.importActual<typeof import('@/api/invoiceFlow')>('@/api/invoiceFlow')
  return {
    ...actual,
    invoiceFlowApi: {
      suggest: vi.fn(),
      getFlow: vi.fn()
    }
  }
})

const mockedApi = vi.mocked(invoiceFlowApi)
let wrapper: VueWrapper | null = null

const summary = {
  invoiceCount: 2,
  totalAmount: 1200,
  pendingCount: 0,
  completedCount: 1,
  cancelledCount: 0,
  redFlushedCount: 1,
  activeDeducted: 200
}

function invoice(id: number, overrides: Partial<TaxFlowResult['invoices'][number]> = {}) {
  return {
    id,
    userId: null,
    username: null,
    companyName: '示例科技有限公司',
    amount: 1000,
    invoiceType: '技术服务费',
    invoiceCategory: 'NORMAL',
    sellerCompany: 'DEFAULT',
    taxRate: 'EXEMPT',
    status: 'COMPLETED' as const,
    redFlushStatus: 'NONE',
    submissionType: 'MANUAL',
    batchId: null,
    outTradeNo: null,
    deductedQuota: 1000,
    createdAt: '2026-11-16T13:12:00',
    events: [],
    ...overrides
  }
}

async function mountDrawer(result: TaxFlowResult) {
  mockedApi.getFlow.mockResolvedValue(result)
  wrapper = mount(TaxFlowDrawer, {
    props: { modelValue: true, taxNumber: '91110000ABCD', scope: { mode: 'user' } },
    attachTo: document.body,
    global: { plugins: [ElementPlus] }
  })
  await flushPromises()
  return wrapper
}

describe('TaxFlowDrawer', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  it('loads the flow and renders summary, timeline events and the legacy empty state', async () => {
    await mountDrawer({
      taxNumber: '91110000ABCD',
      companyNames: ['示例科技有限公司'],
      summary,
      page: 1,
      size: 20,
      total: 2,
      invoices: [
        invoice(1023, {
          redFlushStatus: 'COMPLETED',
          events: [
            { id: 1, eventType: 'SUBMITTED', operatorType: 'USER', quotaDelta: null, balanceAfter: null, quotaTransactionId: null, detail: '网页提交', createdAt: '2026-11-16T13:12:00' },
            { id: 2, eventType: 'QUOTA_DEDUCTED', operatorType: 'USER', quotaDelta: -1000, balanceAfter: 25571.87, quotaTransactionId: 3099, detail: '开票扣除', createdAt: '2026-11-16T13:12:00' },
            { id: 3, eventType: 'RED_FLUSH_CONFIRMED', operatorType: 'ADMIN', quotaDelta: 1000, balanceAfter: 26571.87, quotaTransactionId: 3105, detail: '确认红冲申请，退还额度', createdAt: '2026-11-17T10:00:00' }
          ]
        }),
        invoice(900, { amount: 200, deductedQuota: 200 })
      ]
    })

    expect(mockedApi.getFlow).toHaveBeenCalledWith({ mode: 'user' }, '91110000ABCD', 1, 20)
    const text = document.body.textContent || ''
    expect(text).toContain('91110000ABCD')
    expect(text).toContain('2 张')
    expect(text).toContain('已红冲 1')
    expect(text).toContain('扣除额度')
    expect(text).toContain('−¥1,000.00')
    expect(text).toContain('+¥1,000.00')
    expect(text).toContain('流水 #3099')
    expect(text).toContain('红冲完成')
    expect(text).toContain('该发票创建于数据流功能上线前')
  })

  it('shows an empty state when the tax number has no invoices', async () => {
    await mountDrawer({
      taxNumber: '91110000ABCD',
      companyNames: [],
      summary: { ...summary, invoiceCount: 0 },
      page: 1,
      size: 20,
      total: 0,
      invoices: []
    })

    expect(document.body.textContent).toContain('未找到该税号的发票')
  })

  it('appends the next page when loading more', async () => {
    await mountDrawer({
      taxNumber: '91110000ABCD',
      companyNames: [],
      summary,
      page: 1,
      size: 1,
      total: 2,
      invoices: [invoice(1)]
    })
    mockedApi.getFlow.mockResolvedValueOnce({
      taxNumber: '91110000ABCD',
      companyNames: [],
      summary,
      page: 2,
      size: 1,
      total: 2,
      invoices: [invoice(2, { companyName: '第二页公司' })]
    })

    const moreButton = Array.from(document.body.querySelectorAll('button'))
      .find(button => button.textContent?.includes('加载更多'))
    expect(moreButton).toBeTruthy()
    moreButton!.click()
    await flushPromises()

    expect(mockedApi.getFlow).toHaveBeenLastCalledWith({ mode: 'user' }, '91110000ABCD', 2, 20)
    expect(document.body.textContent).toContain('第二页公司')
    expect(document.body.textContent).not.toContain('加载更多')
  })
})
