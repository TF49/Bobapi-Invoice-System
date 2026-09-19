// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Dashboard from './Dashboard.vue'
import { dashboardApi, type DashboardStats } from '@/api/dashboard'

const chartMocks = vi.hoisted(() => ({
  init: vi.fn(),
  use: vi.fn(),
  graphic: {
    LinearGradient: vi.fn().mockImplementation(() => ({}))
  },
  instances: [] as Array<{
    setOption: ReturnType<typeof vi.fn>
    resize: ReturnType<typeof vi.fn>
    dispose: ReturnType<typeof vi.fn>
    isDisposed: ReturnType<typeof vi.fn>
  }>
}))

vi.mock('echarts/core', () => ({
  init: chartMocks.init,
  use: chartMocks.use,
  graphic: chartMocks.graphic
}))

vi.mock('echarts/charts', () => ({
  BarChart: {},
  LineChart: {},
  PieChart: {}
}))

vi.mock('echarts/components', () => ({
  GridComponent: {},
  LegendComponent: {},
  TooltipComponent: {},
  TitleComponent: {},
  MarkPointComponent: {},
  MarkLineComponent: {}
}))

vi.mock('echarts/renderers', () => ({
  CanvasRenderer: {}
}))

vi.mock('@/api/dashboard', () => ({
  dashboardApi: {
    getStats: vi.fn()
  }
}))

vi.mock('@/api/supplierSettlement', () => ({
  getSettlementHistory: vi.fn(() => Promise.resolve([]))
}))

const emptyStats: DashboardStats = {
  totalInvoices: 0,
  pendingInvoices: 0,
  completedInvoices: 0,
  totalAmount: 0,
  pendingAmount: 0,
  totalSettledAmount: 0,
  unsettledAmount: 0,
  userStats: [],
  typeStats: [],
  companyTopStats: [],
  amountRangeStats: [],
  hourDistribution: [],
  dailyTrend: [],
  quotaPoolStats: {
    totalBalance: 0,
    totalRecharged: 0,
    totalDeducted: 0,
    pendingRechargeCount: 0
  }
}

const populatedStats: DashboardStats = {
  totalInvoices: 3,
  pendingInvoices: 1,
  completedInvoices: 2,
  totalAmount: 2300.75,
  pendingAmount: 500.0,
  totalSettledAmount: 1000.0,
  unsettledAmount: 1300.75,
  userStats: [
    {
      userId: 2,
      username: 'user',
      completedCount: 2,
      pendingCount: 1,
      totalAmount: 2300.75,
      timeline: [
        { date: '2026-08-18', count: 1, amount: 1000 },
        { date: '2026-08-19', count: 1, amount: 1300.75 }
      ]
    }
  ],
  typeStats: [
    { invoiceType: '技术服务费', count: 2, amount: 2300.75 }
  ],
  companyTopStats: [
    { companyName: '测试科技公司', count: 2, amount: 2300.75 }
  ],
  amountRangeStats: [
    { rangeLabel: '< 500元', minAmount: 0, maxAmount: 500, count: 0, amount: 0 },
    { rangeLabel: '500 - 2,000元', minAmount: 500, maxAmount: 2000, count: 2, amount: 2300.75 }
  ],
  hourDistribution: [
    { hour: 10, count: 2 },
    { hour: 14, count: 1 }
  ],
  dailyTrend: [
    { date: '2026-08-18', completedCount: 1, completedAmount: 1000, createdCount: 1, pendingCount: 0, cancelledCount: 0 },
    { date: '2026-08-19', completedCount: 1, completedAmount: 1300.75, createdCount: 2, pendingCount: 0, cancelledCount: 1 }
  ],
  quotaPoolStats: {
    totalBalance: 8800.5,
    totalRecharged: 15000,
    totalDeducted: 6199.5,
    pendingRechargeCount: 2
  }
}

const mockedApi = vi.mocked(dashboardApi)
let wrapper: VueWrapper | null = null

async function mountPage() {
  wrapper = mount(Dashboard, {
    attachTo: document.body,
    global: {
      plugins: [createPinia(), ElementPlus],
      stubs: {
        AppHeader: { template: '<header>数据概览</header>' },
        AnimatedContent: { template: '<div><slot /></div>' },
        SpotlightCard: { template: '<div><slot /></div>' },
        CountUp: {
          props: ['value', 'prefix', 'decimals'],
          template: '<span>{{ prefix }}{{ Number(value || 0).toLocaleString("zh-CN", { minimumFractionDigits: decimals || 0, maximumFractionDigits: decimals || 0 }) }}</span>'
        },
        SupplierSettlementDialog: {
          template: '<div v-if="modelValue">Settlement Dialog</div>',
          props: ['modelValue'],
          emits: ['update:modelValue', 'success']
        }
      }
    }
  })
  await flushPromises()
  return wrapper
}

describe('Dashboard', () => {
  beforeEach(() => {
    chartMocks.instances.length = 0
    chartMocks.init.mockImplementation(() => {
      let disposed = false
      const chart = {
        setOption: vi.fn(),
        resize: vi.fn(),
        dispose: vi.fn().mockImplementation(() => {
          disposed = true
        }),
        isDisposed: vi.fn().mockImplementation(() => disposed)
      }
      chartMocks.instances.push(chart)
      return chart as never
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('renders all KPI cards and charts when data is populated', async () => {
    mockedApi.getStats.mockResolvedValue(populatedStats)

    const page = await mountPage()

    // 验证 KPI 概览指标
    expect(page.text()).toContain('发票总申请数')
    expect(page.text()).toContain('待开发票需求')
    expect(page.text()).toContain('已开具发票')
    expect(page.text()).toContain('累计已开金额')
    expect(page.text()).toContain('用户额度总余额')
    expect(page.text()).toContain('供应商已结款项')
    expect(page.text()).toContain('供应商未结款项')
    expect(page.text()).toContain('¥2,300.75')
    expect(page.text()).toContain('¥1,000.00')
    expect(page.text()).toContain('¥1,300.75')
    expect(page.text()).toContain('待开票 1 笔')
    expect(page.text()).toContain('待审充值 2 笔')

    // 验证 6 个 ECharts 图表全部初始化
    expect(chartMocks.init).toHaveBeenCalledTimes(6)
  })

  it('switches trend view mode and re-renders trend chart option', async () => {
    mockedApi.getStats.mockResolvedValue(populatedStats)

    const page = await mountPage()
    expect(chartMocks.init).toHaveBeenCalledTimes(6)

    const trendChart = chartMocks.instances[0]
    expect(trendChart.setOption).toHaveBeenCalled()

    // 默认 composite 模式包含「已开金额」「申请单数」「已开单数」
    expect(trendChart.setOption).toHaveBeenCalledWith(
      expect.objectContaining({
        series: [
          expect.objectContaining({ name: '已开金额 (¥)', type: 'line' }),
          expect.objectContaining({ name: '申请单数 (单)', type: 'bar' }),
          expect.objectContaining({ name: '已开单数 (单)', type: 'bar' })
        ]
      }),
      true
    )

    // 切换为「金额走势」
    await page.find('input[value="amount"]').setValue()
    await flushPromises()

    expect(trendChart.setOption).toHaveBeenLastCalledWith(
      expect.objectContaining({
        series: [expect.objectContaining({ name: '已开票金额', type: 'line' })]
      }),
      true
    )

    // 切换为「申请单数」
    await page.find('input[value="created"]').setValue()
    await flushPromises()

    expect(trendChart.setOption).toHaveBeenLastCalledWith(
      expect.objectContaining({
        series: [expect.objectContaining({ name: '申请发票单数', type: 'bar' })]
      }),
      true
    )

    // 切换为「已开单数」
    await page.find('input[value="completed"]').setValue()
    await flushPromises()

    expect(trendChart.setOption).toHaveBeenLastCalledWith(
      expect.objectContaining({
        series: [expect.objectContaining({ name: '已开发票单数', type: 'bar' })]
      }),
      true
    )
  })

  it('switches time range and re-renders continuous dates', async () => {
    mockedApi.getStats.mockResolvedValue(populatedStats)

    const page = await mountPage()
    const trendChart = chartMocks.instances[0]
    expect(trendChart.setOption).toHaveBeenCalled()

    // 切换为「近 7 天」
    await page.find('input[value="7"]').setValue()
    await flushPromises()

    const calls = trendChart.setOption.mock.calls
    const lastOption = calls[calls.length - 1]?.[0]
    expect(lastOption.xAxis.data).toHaveLength(7)

    // 验证 Tooltip 正确处理已取消单，不误判为待处理
    const tooltipHtml = lastOption.tooltip.formatter([{ axisValue: '2026-08-19' }])
    expect(tooltipHtml).toContain('已取消申请:')
    expect(tooltipHtml).toContain('1 单')
    expect(tooltipHtml).toContain('✅ 当日发票已全开完（含 1 单已取消）')
    expect(tooltipHtml).not.toContain('⏳ 待开票处理:')
  })

  it('aggregates top users and scroll legend when user count exceeds 8', async () => {
    const multiUserStats: DashboardStats = {
      ...populatedStats,
      totalSettledAmount: 1000.0,
      unsettledAmount: 1300.75,
      userStats: Array.from({ length: 12 }, (_, i) => ({
        userId: i + 1,
        username: `user_${i + 1}`,
        completedCount: 10 - (i % 5),
        pendingCount: 1,
        totalAmount: (1000 - i * 50),
        timeline: [{ date: '2026-08-19', count: 1, amount: 100 }]
      }))
    }
    mockedApi.getStats.mockResolvedValue(multiUserStats)

    await mountPage()

    // userPieChart is instance index 3
    const userPie = chartMocks.instances[3]
    expect(userPie.setOption).toHaveBeenCalled()
    const pieCalls = userPie.setOption.mock.calls
    const option = pieCalls[pieCalls.length - 1]?.[0]
    expect(option.legend.type).toBe('scroll')
    expect(option.series[0].data).toHaveLength(8) // 7 top + 1 others
    expect(option.series[0].data.some((d: any) => d.name === '其他用户')).toBe(true)
  })

  it('gracefully handles empty stats without charts crash', async () => {
    mockedApi.getStats.mockResolvedValue(emptyStats)

    const page = await mountPage()

    expect(page.text()).toContain('发票总申请数')
    expect(page.text()).toContain('完成率 0.0%')
    expect(page.findAll('.chart-empty').length).toBeGreaterThan(0)
  })

  it('does not add a second message when the request layer rejects', async () => {
    mockedApi.getStats.mockRejectedValue(new Error('统计服务暂时不可用'))
    const errorMessage = vi.spyOn(ElMessage, 'error').mockImplementation(() => undefined as never)

    await mountPage()

    expect(errorMessage).not.toHaveBeenCalled()
  })

  it('displays supplier settlement KPI cards with correct values', async () => {
    mockedApi.getStats.mockResolvedValue(populatedStats)

    const page = await mountPage()

    expect(page.text()).toContain('供应商已结款项')
    expect(page.text()).toContain('供应商未结款项')
    expect(page.text()).toContain('¥1,000.00')
    expect(page.text()).toContain('¥1,300.75')
  })
})

