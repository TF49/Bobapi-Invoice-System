// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UserQuota from './UserQuota.vue'
import { quotaApi, type QuotaTransaction } from '@/api/quota'
import { userApi } from '@/api/user'

vi.mock('@/api/quota', () => ({
  quotaApi: {
    getMyQuota: vi.fn(),
    getMyTransactions: vi.fn()
  }
}))

vi.mock('@/api/user', () => ({
  userApi: {
    getMyApiKey: vi.fn()
  }
}))

const mockedQuotaApi = vi.mocked(quotaApi)
const mockedUserApi = vi.mocked(userApi)
let wrapper: VueWrapper | null = null

const sampleTransaction: QuotaTransaction = {
  id: 1,
  userId: 1,
  transactionType: 'RECHARGE',
  amount: 100,
  balanceBefore: 0,
  balanceAfter: 100,
  remark: '管理员充值',
  createdAt: '2026-08-20T10:00:00'
}

async function mountPage() {
  wrapper = mount(UserQuota, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      stubs: {
        AppHeader: { template: '<header>我的额度</header>' },
        AnimatedContent: { template: '<section><slot /></section>' },
        SpotlightCard: { template: '<div><slot /></div>' },
        CountUp: { props: ['value', 'decimals', 'prefix'], template: '<span>{{ prefix }}{{ value }}</span>' }
      }
    }
  })
  await flushPromises()
  return wrapper
}

describe('UserQuota', () => {
  beforeEach(() => {
    mockedQuotaApi.getMyQuota.mockResolvedValue({
      userId: 1,
      balance: 100,
      totalRecharged: 100,
      totalDeducted: 0
    })
    mockedQuotaApi.getMyTransactions.mockResolvedValue([sampleTransaction])
    mockedUserApi.getMyApiKey.mockResolvedValue({
      apiKey: 'bk_live_test_1234567890',
      apiKeyEnabled: true
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('renders quota stats and transactions list', async () => {
    const page = await mountPage()
    expect(page.find('header').text()).toBe('我的额度')
    expect(page.find('.record-id').text()).toBe('#0001')
    expect(page.find('.records-table').text()).toContain('充值')
    expect(page.find('.records-table').text()).toContain('+¥100.00')
  })

  it('renders OpenAPI cancellation endpoint example when switching to API tab', async () => {
    const page = await mountPage()
    const apiTabBtn = page.findAll('.tab-btn').find((btn) => btn.text().includes('OpenAPI 开发者密钥'))
    expect(apiTabBtn).toBeDefined()
    await apiTabBtn!.trigger('click')
    await flushPromises()

    const text = page.text()
    expect(text).toContain('取消待开票申请 (自动退还额度)')
    expect(text).toContain('/open/v1/invoices/by-out-trade-no/{outTradeNo}/cancel')
    expect(text).toContain('/open/v1/invoices/{id}/cancel')
  })
})
