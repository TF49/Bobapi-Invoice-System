// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminRechargeRequests from './AdminRechargeRequests.vue'
import { rechargeRequestApi, type RechargeRequest } from '@/api/rechargeRequest'

vi.mock('@/api/rechargeRequest', () => ({
  rechargeRequestApi: {
    getAllRequests: vi.fn(),
    getPendingRequests: vi.fn(),
    reviewRequest: vi.fn(),
    getPendingCount: vi.fn()
  }
}))

const mockRequests: RechargeRequest[] = [
  {
    id: 123,
    userId: 10,
    username: 'pidan',
    feeAmount: 300,
    amount: 10000,
    status: 'APPROVED',
    screenshotUrl: '/uploads/screenshots/receipt1.png',
    remark: '测试充值',
    adminRemark: '已核验到账',
    reviewedBy: 1,
    reviewedByName: 'admin',
    reviewedAt: '2026-10-19T18:00:00',
    createdAt: '2026-10-19T17:55:00',
    updatedAt: '2026-10-19T18:00:00'
  },
  {
    id: 124,
    userId: 11,
    username: 'maidou',
    feeAmount: 300,
    amount: 10000,
    status: 'PENDING',
    screenshotUrl: '/uploads/screenshots/receipt2.png',
    remark: '加急处理',
    adminRemark: undefined,
    reviewedBy: undefined,
    reviewedByName: undefined,
    reviewedAt: undefined,
    createdAt: '2026-10-20T10:00:00',
    updatedAt: '2026-10-20T10:00:00'
  },
  {
    id: 125,
    userId: 12,
    username: 'rookie',
    feeAmount: 600,
    amount: 20000,
    status: 'REJECTED',
    screenshotUrl: undefined,
    remark: '测试单',
    adminRemark: '未查收到该笔转账',
    reviewedBy: 1,
    reviewedByName: 'admin',
    reviewedAt: '2026-10-20T11:00:00',
    createdAt: '2026-10-20T10:30:00',
    updatedAt: '2026-10-20T11:00:00'
  }
]

const mockedApi = vi.mocked(rechargeRequestApi)
let wrapper: VueWrapper | null = null
let router: Router

async function mountPage() {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/recharge-requests', component: AdminRechargeRequests },
      { path: '/login', component: { template: '<div>login</div>' } }
    ]
  })

  await router.push('/admin/recharge-requests')
  await router.isReady()

  const pinia = createPinia()

  wrapper = mount(AdminRechargeRequests, {
    global: {
      plugins: [pinia, router, ElementPlus],
      stubs: {
        AppHeader: { template: '<header><slot /></header>' },
        AnimatedContent: { template: '<div><slot /></div>' },
        SpotlightCard: { template: '<div class="spotlight-card"><slot /></div>' },
        CountUp: {
          props: ['value', 'prefix'],
          template: '<span>{{ prefix || "" }}{{ value }}</span>'
        }
      }
    }
  })

  await flushPromises()
}

describe('AdminRechargeRequests.vue', () => {
  beforeEach(() => {
    mockedApi.getAllRequests.mockResolvedValue(mockRequests)
    mockedApi.reviewRequest.mockResolvedValue(undefined)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('正确加载并计算顶部数据概览指标', async () => {
    await mountPage()

    expect(mockedApi.getAllRequests).toHaveBeenCalled()
    const text = wrapper!.text()
    // 申请总数 3
    expect(text).toContain('3')
    // 包含待审核笔数 1
    expect(text).toContain('待审核笔数')
    // 包含申请人
    expect(text).toContain('pidan')
    expect(text).toContain('maidou')
    expect(text).toContain('rookie')
  })

  it('支持状态 Tab 切换筛选', async () => {
    await mountPage()

    const pendingTab = wrapper!.findAll('.status-tab-btn').find(b => b.text().includes('待审核'))
    expect(pendingTab).toBeDefined()
    await pendingTab!.trigger('click')
    await flushPromises()

    // 筛选后只有 maidou (PENDING)
    const text = wrapper!.text()
    expect(text).toContain('maidou')
    expect(text).not.toContain('pidan')
    expect(text).not.toContain('rookie')
  })

  it('支持搜索关键词过滤', async () => {
    await mountPage()

    const searchInput = wrapper!.find('.search-input input')
    await searchInput.setValue('pidan')
    await flushPromises()

    const text = wrapper!.text()
    expect(text).toContain('pidan')
    expect(text).not.toContain('maidou')
    expect(text).not.toContain('rookie')
  })

  it('点击查看详情能够打开详情弹窗', async () => {
    await mountPage()

    const detailBtn = wrapper!.find('.detail-view-btn')
    expect(detailBtn.exists()).toBe(true)
    await detailBtn.trigger('click')
    await flushPromises()

    expect(wrapper!.find('.detail-dialog').exists()).toBe(true)
  })

  it('点击审核能够打开审核弹窗并支持快捷原因填入', async () => {
    await mountPage()

    const reviewBtn = wrapper!.find('.review-primary-btn')
    expect(reviewBtn.exists()).toBe(true)
    await reviewBtn.trigger('click')
    await flushPromises()

    expect(wrapper!.find('.review-dialog').exists()).toBe(true)

    // 选择拒绝并填入快捷标签
    const rejectCard = wrapper!.find('.decision-card.reject-card')
    expect(rejectCard.exists()).toBe(true)
    await rejectCard.trigger('click')
    await flushPromises()

    const quickChip = wrapper!.find('.quick-tag-chip')
    expect(quickChip.exists()).toBe(true)
    await quickChip.trigger('click')
    await flushPromises()

    const textarea = wrapper!.find('.review-action-form textarea')
    expect((textarea.element as HTMLTextAreaElement).value).toContain('未查收到该笔转账')
  })

  it('响应路由 query status 参数变化自动筛选', async () => {
    await mountPage()

    await router.push('/admin/recharge-requests?status=PENDING')
    await flushPromises()

    const text = wrapper!.text()
    expect(text).toContain('maidou')
    expect(text).not.toContain('pidan')
  })

  it('点击编号可复制单号', async () => {
    await mountPage()

    const idSpan = wrapper!.find('.clickable-id')
    expect(idSpan.exists()).toBe(true)
    await idSpan.trigger('click')
    await flushPromises()
  })
})
