<template>
  <div class="workspace-shell">
    <AppHeader title="数据概览" />

    <main class="workspace-content">
      <div class="dashboard-container" v-loading="loading">
        <!-- 顶部统计卡片 -->
        <el-row :gutter="20" class="stats-row">
          <el-col :xs="24" :sm="12" :lg="6">
            <el-card class="stat-card">
              <div class="stat-content">
                <div class="stat-label">发票总数</div>
                <div class="stat-value">{{ stats?.totalInvoices || 0 }}</div>
              </div>
            </el-card>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-card class="stat-card stat-card-pending">
              <div class="stat-content">
                <div class="stat-label">未开发票</div>
                <div class="stat-value">{{ stats?.pendingInvoices || 0 }}</div>
              </div>
            </el-card>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-card class="stat-card stat-card-completed">
              <div class="stat-content">
                <div class="stat-label">已开发票</div>
                <div class="stat-value">{{ stats?.completedInvoices || 0 }}</div>
              </div>
            </el-card>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-card class="stat-card stat-card-amount">
              <div class="stat-content">
                <div class="stat-label">总开票金额（已完成）</div>
                <div class="stat-value">¥{{ formatAmount(stats?.totalAmount || 0) }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 图表区域 -->
        <el-row :gutter="20" class="charts-row">
          <!-- 饼状图：用户发票分布 -->
          <el-col :xs="24" :lg="12">
            <el-card class="chart-card">
              <template #header>
                <div class="card-header">
                  <span>用户发票分布</span>
                </div>
              </template>
              <div v-if="hasUserStats" ref="pieChartRef" class="chart-container"></div>
              <div v-else class="chart-empty">
                <el-empty description="暂无统计数据" :image-size="80" />
              </div>
            </el-card>
          </el-col>

          <!-- 可切换图表：已开票增长趋势 -->
          <el-col :xs="24" :lg="12">
            <el-card class="chart-card">
              <template #header>
                <div class="card-header">
                  <span>已开票增长趋势</span>
                  <el-radio-group v-model="chartType" size="small">
                    <el-radio-button value="bar">柱状图</el-radio-button>
                    <el-radio-button value="line">折线图</el-radio-button>
                  </el-radio-group>
                </div>
              </template>
              <div v-if="hasTimelineStats" ref="trendChartRef" class="chart-container"></div>
              <div v-else class="chart-empty">
                <el-empty description="暂无统计数据" :image-size="80" />
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { init, use, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { storeToRefs } from 'pinia'
import { useDashboardStore } from '@/stores/dashboard'
import AppHeader from '@/components/AppHeader.vue'

use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer
])

const dashboardStore = useDashboardStore()
// Bug 1 fix: 使用 storeToRefs 保持响应性
const { stats, loading } = storeToRefs(dashboardStore)

const pieChartRef = ref<HTMLElement>()
const trendChartRef = ref<HTMLElement>()
const chartType = ref<'bar' | 'line'>('bar')

let pieChart: ECharts | null = null
let trendChart: ECharts | null = null

// Bug 7 fix: 计算是否有有效的用户统计数据
const hasUserStats = computed(() => (stats.value?.userStats?.length ?? 0) > 0)
const hasTimelineStats = computed(() =>
  stats.value?.userStats?.some(user => user.timeline.length > 0) ?? false
)

// 格式化金额
function formatAmount(amount: number): string {
  return amount.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 初始化饼图
function initPieChart() {
  if (!pieChartRef.value || !stats.value) return

  if (!pieChart) {
    pieChart = init(pieChartRef.value)
  }

  const data = stats.value.userStats.map(user => ({
    name: user.username,
    value: user.completedCount + user.pendingCount,
    completed: user.completedCount,
    pending: user.pendingCount
  }))

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        const data = params.data as any
        return `${data.name}<br/>已开: ${data.completed}<br/>未开: ${data.pending}<br/>总计: ${data.value}`
      }
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: '用户发票分布',
        type: 'pie',
        radius: '50%',
        data: data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }

  pieChart.setOption(option, true)
}

// 初始化趋势图
function initTrendChart() {
  if (!trendChartRef.value || !stats.value) return

  if (!trendChart) {
    trendChart = init(trendChartRef.value)
  }

  // 获取所有日期并排序
  const allDates = new Set<string>()
  stats.value.userStats.forEach(user => {
    user.timeline.forEach(t => allDates.add(t.date))
  })
  const sortedDates = Array.from(allDates).sort()

  // 为每个用户准备数据
  const series = stats.value.userStats.map(user => ({
    name: user.username,
    type: chartType.value,
    data: sortedDates.map(date => {
      const timelineData = user.timeline.find(t => t.date === date)
      return timelineData ? timelineData.count : 0
    })
  }))

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: stats.value.userStats.map(u => u.username)
    },
    xAxis: {
      type: 'category',
      data: sortedDates,
      axisLabel: {
        rotate: sortedDates.length > 15 ? 30 : 0
      }
    },
    yAxis: {
      type: 'value'
    },
    series: series
  }

  trendChart.setOption(option, true)
}

// 加载数据
async function loadData() {
  try {
    await dashboardStore.fetchStats()
  } catch {
    // 请求拦截器已显示具体错误，避免重复弹出第二条提示。
    return
  }

  await nextTick()
  initPieChart()
  initTrendChart()
}

// 监听图表类型切换
watch(chartType, () => {
  initTrendChart()
})

// 窗口大小变化时重绘图表
function handleResize() {
  pieChart?.resize()
  trendChart?.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

// 组件卸载时清理
onBeforeUnmount(() => {
  pieChart?.dispose()
  trendChart?.dispose()
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
}

.stats-row {
  margin-bottom: 20px;
  row-gap: 20px;
}

.stat-card {
  text-align: center;
  border-radius: 8px;
  transition: transform 0.3s, box-shadow 0.3s;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.stat-card-pending {
  border-top: 4px solid #e6a23c;
}

.stat-card-completed {
  border-top: 4px solid #67c23a;
}

.stat-card-amount {
  border-top: 4px solid #409eff;
}

.stat-content {
  padding: 10px 0;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.charts-row {
  margin-top: 20px;
  row-gap: 20px;
}

.chart-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-container {
  width: 100%;
  height: 400px;
}

/* Bug 7 fix: 无数据占位 */
.chart-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 400px;
}

@media (max-width: 600px) {
  .dashboard-container {
    padding: 0;
  }

  .stats-row,
  .charts-row {
    row-gap: 12px;
  }

  .stat-card {
    min-height: auto;
    padding: 0;
  }

  .stat-value {
    font-size: 24px;
  }

  .card-header {
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 12px;
  }

  .chart-container,
  .chart-empty {
    height: 320px;
  }
}
</style>
