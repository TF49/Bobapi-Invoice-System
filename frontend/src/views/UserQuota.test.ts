// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UserQuota from './UserQuota.vue'
import { quotaApi, type QuotaTransaction } from '@/api/quota'

vi.mock('@/api/quota', () => ({
  quotaApi: {
    getMyQuota: vi.fn(),
    getMyTransactions: vi.fn()
  }
}))

const mockedQuotaApi = vi.mocked(quotaApi)
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
})
