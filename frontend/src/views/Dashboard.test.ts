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
  instances: [] as Array<{
    setOption: ReturnType<typeof vi.fn>
    resize: ReturnType<typeof vi.fn>
    dispose: ReturnType<typeof vi.fn>
  }>
}))

vi.mock('echarts/core', () => ({
  init: chartMocks.init,
  use: chartMocks.use
}))

vi.mock('echarts/charts', () => ({
  BarChart: {},
  LineChart: {},
  PieChart: {}
}))

vi.mock('echarts/components', () => ({
  GridComponent: {},
  LegendComponent: {},
  TooltipComponent: {}
}))

vi.mock('echarts/renderers', () => ({
  CanvasRenderer: {}
}))

vi.mock('@/api/dashboard', () => ({
  dashboardApi: {
    getStats: vi.fn()
  }
}))

const pendingOnlyStats: DashboardStats = {
  totalInvoices: 2,
  pendingInvoices: 2,
  completedInvoices: 0,
  totalAmount: 0,
  userStats: [
    {
      userId: 2,
      username: 'user',
      completedCount: 0,
      pendingCount: 2,
      totalAmount: 0,
      timeline: []
    }
  ]
}

const populatedStats: DashboardStats = {
  totalInvoices: 3,
  pendingInvoices: 1,
  completedInvoices: 2,
  totalAmount: 2300.75,
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
  ]
}

const mockedApi = vi.mocked(dashboardApi)
let wrapper: VueWrapper | null = null

async function mountPage() {
  wrapper = mount(Dashboard, {
    attachTo: document.body,
    global: {
      plugins: [createPinia(), ElementPlus],
      stubs: {
        AppHeader: { template: '<header>数据概览</header>' }
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
      const chart = {
        setOption: vi.fn(),
        resize: vi.fn(),
        dispose: vi.fn()
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

  it('shows a trend empty state when invoices exist but none are completed', async () => {
    mockedApi.getStats.mockResolvedValue(pendingOnlyStats)

    const page = await mountPage()

    expect(page.text()).toContain('发票总数')
    expect(page.findAll('.chart-container')).toHaveLength(1)
    expect(page.findAll('.chart-empty')).toHaveLength(1)
    expect(chartMocks.init).toHaveBeenCalledTimes(1)
  })

  it('renders both charts and switches the trend series to a line', async () => {
    mockedApi.getStats.mockResolvedValue(populatedStats)

    const page = await mountPage()

    expect(page.text()).toContain('¥2,300.75')
    expect(chartMocks.init).toHaveBeenCalledTimes(2)

    await page.find('input[value="line"]').setValue()
    await flushPromises()

    const trendChart = chartMocks.instances[1]
    expect(trendChart.setOption).toHaveBeenLastCalledWith(
      expect.objectContaining({
        series: [expect.objectContaining({ type: 'line' })]
      }),
      true
    )
  })

  it('does not add a second message when the request layer rejects', async () => {
    mockedApi.getStats.mockRejectedValue(new Error('统计服务暂时不可用'))
    const errorMessage = vi.spyOn(ElMessage, 'error').mockImplementation(() => undefined as never)

    await mountPage()

    expect(errorMessage).not.toHaveBeenCalled()
  })
})
