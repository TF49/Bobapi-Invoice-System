<template>
  <div class="workspace-shell dashboard-page" ref="dashboardRootRef">
    <AppHeader title="数据概览" />

    <main class="workspace-content">
      <!-- 顶部操作栏 -->
      <section class="dashboard-toolbar">
        <div class="toolbar-left">
          <div class="page-title-badge">
            <span class="live-dot"></span>
            <span>实时数据看板</span>
          </div>
          <span class="toolbar-hint">汇总系统发票申请、开票走势、类目分布与资金额度分析</span>
        </div>

        <div class="toolbar-right">
          <!-- 刷新按钮 -->
          <el-button
            class="refresh-btn"
            size="small"
            :loading="loading"
            @click="loadData"
          >
            <el-icon><Refresh /></el-icon>
            <span>刷新</span>
          </el-button>

          <!-- 供应商结算按钮（仅管理员） -->
          <el-button
            v-if="isAdmin"
            type="success"
            size="small"
            class="quick-action-btn"
            @click="showSettlementDialog = true"
          >
            <el-icon><Money /></el-icon>
            <span>供应商结算</span>
          </el-button>

          <!-- 待办快捷入口 -->
          <el-button
            v-if="stats && stats.pendingInvoices > 0"
            type="warning"
            size="small"
            class="quick-action-btn"
            @click="$router.push('/admin?status=PENDING')"
          >
            <el-icon><Clock /></el-icon>
            <span>待开票 {{ stats.pendingInvoices }} 笔</span>
          </el-button>
          <el-button
            v-if="stats?.quotaPoolStats && stats.quotaPoolStats.pendingRechargeCount > 0"
            type="primary"
            size="small"
            class="quick-action-btn quick-action-recharge"
            @click="$router.push('/admin/recharge-requests')"
          >
            <el-icon><Wallet /></el-icon>
            <span>待审充值 {{ stats.quotaPoolStats.pendingRechargeCount }} 笔</span>
          </el-button>
        </div>
      </section>

      <!-- KPI 关键指标卡片群 -->
      <AnimatedContent tag="section" class="stats-grid" :distance="10">
        <!-- 1. 发票申请总量 -->
        <SpotlightCard class="stat-card stat-card-total">
          <div class="stat-card-content">
            <span class="stat-icon primary"><Tickets /></span>
            <div class="stat-copy">
              <span class="stat-label">发票总申请数</span>
              <strong class="stat-value">
                <CountUp :value="stats?.totalInvoices || 0" />
              </strong>
            </div>
            <div class="stat-footer-pill">
              <span>完成率 <strong>{{ completionRate }}%</strong></span>
            </div>
          </div>
        </SpotlightCard>

        <!-- 2. 待开票需求 -->
        <SpotlightCard class="stat-card stat-card-pending">
          <div class="stat-card-content">
            <span class="stat-icon warning"><Clock /></span>
            <div class="stat-copy">
              <span class="stat-label">待开发票需求</span>
              <strong class="stat-value warning-text">
                <CountUp :value="stats?.pendingInvoices || 0" />
              </strong>
            </div>
            <div class="stat-footer-pill warning-pill">
              <span>待开金额: ¥<strong><CountUp :value="stats?.pendingAmount || 0" :decimals="2" /></strong></span>
            </div>
          </div>
        </SpotlightCard>

        <!-- 3. 已开发票完成 -->
        <SpotlightCard class="stat-card stat-card-completed">
          <div class="stat-card-content">
            <span class="stat-icon success"><CircleCheck /></span>
            <div class="stat-copy">
              <span class="stat-label">已开具发票</span>
              <strong class="stat-value success-text">
                <CountUp :value="stats?.completedInvoices || 0" />
              </strong>
            </div>
            <div class="stat-footer-pill success-pill">
              <span>已交付凭证</span>
            </div>
          </div>
        </SpotlightCard>

        <!-- 4. 累计已开金额 -->
        <SpotlightCard class="stat-card stat-card-amount">
          <div class="stat-card-content">
            <span class="stat-icon accent"><Wallet /></span>
            <div class="stat-copy">
              <span class="stat-label">累计已开金额</span>
              <strong class="stat-value amount-stat">
                <CountUp :value="stats?.totalAmount || 0" :decimals="2" prefix="¥" />
              </strong>
            </div>
            <div class="stat-footer-pill">
              <span>均单: ¥<strong>{{ avgCompletedAmount }}</strong></span>
            </div>
          </div>
        </SpotlightCard>

        <!-- 5. 平台资金额度池 -->
        <SpotlightCard class="stat-card stat-card-quota stat-card-featured">
          <div class="stat-card-content">
            <span class="stat-icon cyan"><Money /></span>
            <div class="stat-copy">
              <span class="stat-label">用户额度总余额</span>
              <strong class="stat-value">
                <CountUp :value="stats?.quotaPoolStats?.totalBalance || 0" :decimals="2" prefix="¥" />
              </strong>
            </div>
            <div class="stat-footer-pill">
              <span>累计充值: ¥<strong><CountUp :value="stats?.quotaPoolStats?.totalRecharged || 0" :decimals="2" /></strong></span>
            </div>
          </div>
        </SpotlightCard>

        <!-- 6. 供应商已结款项 -->
        <SpotlightCard class="stat-card stat-card-settled">
          <div class="stat-card-content">
            <span class="stat-icon success"><CircleCheck /></span>
            <div class="stat-copy">
              <span class="stat-label">供应商已结款项</span>
              <strong class="stat-value success-text">
                <CountUp :value="stats?.totalSettledAmount || 0" :decimals="2" prefix="¥" />
              </strong>
            </div>
            <div class="stat-footer-pill success-pill">
              <span>已结算</span>
            </div>
          </div>
        </SpotlightCard>

        <!-- 7. 供应商未结款项 -->
        <SpotlightCard class="stat-card stat-card-unsettled">
          <div class="stat-card-content">
            <span class="stat-icon warning"><Clock /></span>
            <div class="stat-copy">
              <span class="stat-label">供应商未结款项</span>
              <strong class="stat-value warning-text">
                <CountUp :value="stats?.unsettledAmount || 0" :decimals="2" prefix="¥" />
              </strong>
            </div>
            <div class="stat-footer-pill warning-pill">
              <span>待结算</span>
            </div>
          </div>
        </SpotlightCard>
      </AnimatedContent>

      <!-- 图表区域 -->
      <div class="dashboard-charts-grid" v-loading="loading">
        <!-- ROW 1: 全景业务走势复合图 + 开票类目分布 -->
        <el-row :gutter="20" class="charts-row">
          <!-- 1. 业务综合走势图 -->
          <el-col :xs="24" :lg="15">
            <el-card class="chart-card">
              <template #header>
                <div class="card-header">
                  <div class="header-title">
                    <span class="header-icon"><TrendCharts /></span>
                    <div>
                      <h3 class="title-text">业务开票走势分析</h3>
                      <p class="subtitle-text">每日开票金额走势与开票单数复合对比</p>
                    </div>
                  </div>
                  <div class="header-controls">
                    <el-radio-group v-model="timeRange" size="small" class="trend-time-filter">
                      <el-radio-button value="7">近 7 天</el-radio-button>
                      <el-radio-button value="30">近 30 天</el-radio-button>
                      <el-radio-button value="90">近 90 天</el-radio-button>
                      <el-radio-button value="all">全部</el-radio-button>
                    </el-radio-group>
                    <el-radio-group v-model="trendViewMode" size="small">
                      <el-radio-button value="composite">复合走势</el-radio-button>
                      <el-radio-button value="amount">金额走势</el-radio-button>
                      <el-radio-button value="created">申请单数</el-radio-button>
                      <el-radio-button value="completed">已开单数</el-radio-button>
                      <el-radio-button value="userStacked">按用户堆叠</el-radio-button>
                    </el-radio-group>
                  </div>
                </div>
              </template>
              <div v-show="hasTrendData" ref="trendChartRef" class="chart-container main-trend-chart"></div>
              <div v-show="!hasTrendData" class="chart-empty">
                <el-empty description="所选周期内暂无开票走势数据" :image-size="80" />
              </div>
            </el-card>
          </el-col>

          <!-- 2. 开票类目/项目分布 -->
          <el-col :xs="24" :lg="9">
            <el-card class="chart-card">
              <template #header>
                <div class="card-header">
                  <div class="header-title">
                    <span class="header-icon"><PieIcon /></span>
                    <div>
                      <h3 class="title-text">开票类目项目分布</h3>
                      <p class="subtitle-text">各项服务品类开票占比与金额结构</p>
                    </div>
                  </div>
                  <div class="header-controls">
                    <el-radio-group v-model="typeMetricMode" size="small">
                      <el-radio-button value="amount">按金额</el-radio-button>
                      <el-radio-button value="count">按笔数</el-radio-button>
                    </el-radio-group>
                  </div>
                </div>
              </template>
              <div v-show="hasTypeStats" ref="typeChartRef" class="chart-container"></div>
              <div v-show="!hasTypeStats" class="chart-empty">
                <el-empty description="暂无类目统计数据" :image-size="80" />
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- ROW 2: 企业抬头开票排行 TOP 10 + 用户开票贡献占比 -->
        <el-row :gutter="20" class="charts-row">
          <!-- 3. 企业抬头开票排行 TOP 10 -->
          <el-col :xs="24" :lg="12">
            <el-card class="chart-card">
              <template #header>
                <div class="card-header">
                  <div class="header-title">
                    <span class="header-icon"><OfficeBuilding /></span>
                    <div>
                      <h3 class="title-text">企业抬头开票排行 TOP 10</h3>
                      <p class="subtitle-text">累计开票金额最高的核心企业客户</p>
                    </div>
                  </div>
                  <div class="header-controls">
                    <el-radio-group v-model="companyMetricMode" size="small">
                      <el-radio-button value="amount">金额排行</el-radio-button>
                      <el-radio-button value="count">笔数排行</el-radio-button>
                    </el-radio-group>
                  </div>
                </div>
              </template>
              <div v-show="hasCompanyStats" ref="companyChartRef" class="chart-container"></div>
              <div v-show="!hasCompanyStats" class="chart-empty">
                <el-empty description="暂无企业抬头数据" :image-size="80" />
              </div>
            </el-card>
          </el-col>

          <!-- 4. 用户发票分布与贡献占比 -->
          <el-col :xs="24" :lg="12">
            <el-card class="chart-card">
              <template #header>
                <div class="card-header">
                  <div class="header-title">
                    <span class="header-icon"><User /></span>
                    <div>
                      <h3 class="title-text">用户发票分布与贡献</h3>
                      <p class="subtitle-text">各注册用户的开票量与金额贡献占比</p>
                    </div>
                  </div>
                  <div class="header-controls">
                    <el-radio-group v-model="userMetricMode" size="small">
                      <el-radio-button value="count">按发票数</el-radio-button>
                      <el-radio-button value="amount">按金额</el-radio-button>
                    </el-radio-group>
                  </div>
                </div>
              </template>
              <div v-show="hasUserStats" ref="userPieChartRef" class="chart-container"></div>
              <div v-show="!hasUserStats" class="chart-empty">
                <el-empty description="暂无用户统计数据" :image-size="80" />
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- ROW 3: 发票金额区间画像 + 24小时时段申请热度 -->
        <el-row :gutter="20" class="charts-row">
          <!-- 5. 发票金额区间画像 -->
          <el-col :xs="24" :lg="12">
            <el-card class="chart-card">
              <template #header>
                <div class="card-header">
                  <div class="header-title">
                    <span class="header-icon"><Histogram /></span>
                    <div>
                      <h3 class="title-text">发票金额区间画像</h3>
                      <p class="subtitle-text">按客单金额分层统计笔数与金额分布</p>
                    </div>
                  </div>
                </div>
              </template>
              <div v-show="hasAmountRangeStats" ref="amountRangeChartRef" class="chart-container"></div>
              <div v-show="!hasAmountRangeStats" class="chart-empty">
                <el-empty description="暂无金额区间数据" :image-size="80" />
              </div>
            </el-card>
          </el-col>

          <!-- 6. 24小时时段申请热度 -->
          <el-col :xs="24" :lg="12">
            <el-card class="chart-card">
              <template #header>
                <div class="card-header">
                  <div class="header-title">
                    <span class="header-icon"><DataAnalysis /></span>
                    <div>
                      <h3 class="title-text">24 小时开票申请热度峰值</h3>
                      <p class="subtitle-text">全天各时段申请频率分布，识别业务高峰</p>
                    </div>
                  </div>
                </div>
              </template>
              <div v-show="hasHourStats" ref="hourChartRef" class="chart-container"></div>
              <div v-show="!hasHourStats" class="chart-empty">
                <el-empty description="暂无时段分布数据" :image-size="80" />
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- ROW 4: 供应商结算记录列表（仅管理员可见） -->
        <el-row v-if="isAdmin" :gutter="20" class="charts-row">
          <el-col :xs="24">
            <el-card class="chart-card">
              <template #header>
                <div class="card-header">
                  <div class="header-title">
                    <span class="header-icon"><Money /></span>
                    <div>
                      <h3 class="title-text">供应商结算记录</h3>
                      <p class="subtitle-text">历史结算操作记录与金额汇总</p>
                    </div>
                  </div>
                  <div class="header-controls">
                    <el-button size="small" @click="loadSettlementHistory">
                      <el-icon><Refresh /></el-icon>
                      <span>刷新记录</span>
                    </el-button>
                  </div>
                </div>
              </template>
              <el-table
                :data="settlementHistory"
                v-loading="loadingSettlementHistory"
                stripe
                style="width: 100%"
              >
                <el-table-column prop="createdAt" label="结算时间" width="180">
                  <template #default="{ row }">
                    {{ formatDate(row.createdAt) }}
                  </template>
                </el-table-column>
                <el-table-column prop="settlementAmount" label="结算金额" width="150">
                  <template #default="{ row }">
                    ¥{{ formatMoney(row.settlementAmount) }}
                  </template>
                </el-table-column>
                <el-table-column prop="operatorName" label="操作人" width="120" />
                <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
              </el-table>
              <div v-if="!loadingSettlementHistory && settlementHistory.length === 0" class="table-empty">
                <el-empty description="暂无结算记录" :image-size="60" />
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </main>

    <!-- 供应商结算对话框：修复 #2/#8 传入当前未结款项，供动态限额和提示展示 -->
    <SupplierSettlementDialog
      v-model="showSettlementDialog"
      :unsettled-amount="stats?.unsettledAmount"
      @success="handleSettlementSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  TitleComponent,
  MarkPointComponent,
  MarkLineComponent
} from 'echarts/components'
import { init, use, graphic, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import {
  Tickets,
  Clock,
  CircleCheck,
  Wallet,
  Money,
  TrendCharts,
  PieChart as PieIcon,
  OfficeBuilding,
  User,
  Histogram,
  DataAnalysis,
  Refresh
} from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { useDashboardStore } from '@/stores/dashboard'
import { useUserStore } from '@/stores/user'
import AppHeader from '@/components/AppHeader.vue'
import SpotlightCard from '@/components/bits/SpotlightCard.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import CountUp from '@/components/bits/CountUp.vue'
import SupplierSettlementDialog from '@/components/SupplierSettlementDialog.vue'
import { getSettlementHistory, type SupplierSettlementResponse } from '@/api/supplierSettlement'

// 注册 ECharts 核心组件
use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  TitleComponent,
  MarkPointComponent,
  MarkLineComponent,
  CanvasRenderer
])

// 主题配色盘
const THEME_COLORS = [
  '#12715b', // 翡翠主绿
  '#d69a2d', // 琥珀金
  '#2a9d8f', // 蓝绿
  '#e76f51', // 珊瑚橙
  '#457b9d', // 靛青蓝
  '#e63946', // 绯红
  '#8338ec', // 优雅紫
  '#3a86ff', // 天蓝
  '#06d6a0', // 碧绿
  '#f4a261'  // 暖橙
]

const dashboardStore = useDashboardStore()
const { stats, loading } = storeToRefs(dashboardStore)
const userStore = useUserStore()
const { role } = storeToRefs(userStore)

// 控制状态
const timeRange = ref<'7' | '30' | '90' | 'all'>('all')
const trendViewMode = ref<'composite' | 'amount' | 'created' | 'completed' | 'userStacked'>('composite')
const typeMetricMode = ref<'amount' | 'count'>('amount')
const companyMetricMode = ref<'amount' | 'count'>('amount')
const userMetricMode = ref<'count' | 'amount'>('count')

// 供应商结算相关状态
const showSettlementDialog = ref(false)
const settlementHistory = ref<SupplierSettlementResponse[]>([])
const loadingSettlementHistory = ref(false)

// 是否为管理员
const isAdmin = computed(() => role.value === 'ADMIN')

// DOM 根节点与图表 DOM 引用
const dashboardRootRef = ref<HTMLElement>()
let resizeObserver: ResizeObserver | null = null

const trendChartRef = ref<HTMLElement>()
const typeChartRef = ref<HTMLElement>()
const companyChartRef = ref<HTMLElement>()
const userPieChartRef = ref<HTMLElement>()
const amountRangeChartRef = ref<HTMLElement>()
const hourChartRef = ref<HTMLElement>()

// 图表实例列表
let trendChart: ECharts | null = null
let typeChart: ECharts | null = null
let companyChart: ECharts | null = null
let userPieChart: ECharts | null = null
let amountRangeChart: ECharts | null = null
let hourChart: ECharts | null = null

// KPI 衍生计算
const completionRate = computed(() => {
  if (!stats.value || !stats.value.totalInvoices || stats.value.totalInvoices === 0) return '0.0'
  const rate = (stats.value.completedInvoices / stats.value.totalInvoices) * 100
  return rate.toFixed(1)
})

const avgCompletedAmount = computed(() => {
  if (!stats.value || !stats.value.completedInvoices || stats.value.completedInvoices === 0) return '0.00'
  const avg = stats.value.totalAmount / stats.value.completedInvoices
  return avg.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
})

// 数据存在性判断
const hasUserStats = computed(() => (stats.value?.userStats?.length ?? 0) > 0)
const hasTimelineStats = computed(() =>
  stats.value?.userStats?.some(user => user.timeline.length > 0) ?? false
)
const hasTrendData = computed(() => {
  if (stats.value?.dailyTrend && stats.value.dailyTrend.length > 0) return true
  return hasTimelineStats.value
})
const hasTypeStats = computed(() => (stats.value?.typeStats?.length ?? 0) > 0 || hasUserStats.value)
const hasCompanyStats = computed(() => (stats.value?.companyTopStats?.length ?? 0) > 0 || hasUserStats.value)
const hasAmountRangeStats = computed(() => (stats.value?.amountRangeStats?.length ?? 0) > 0 || (stats.value?.totalInvoices ?? 0) > 0)
const hasHourStats = computed(() => (stats.value?.hourDistribution?.length ?? 0) > 0 || (stats.value?.totalInvoices ?? 0) > 0)

// 格式化金额
function formatMoney(amount: number): string {
  return (amount || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 日期连续补全工具函数
function formatDateString(d: Date): string {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function generateDateSequence(days: number): string[] {
  const dates: string[] = []
  const today = new Date()
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(today.getDate() - i)
    dates.push(formatDateString(d))
  }
  return dates
}

function generateContinuousDates(minDateStr: string, maxDateStr: string): string[] {
  const dates: string[] = []
  const cur = new Date(minDateStr)
  const end = new Date(maxDateStr)
  let count = 0
  while (cur <= end && count < 1000) {
    dates.push(formatDateString(cur))
    cur.setDate(cur.getDate() + 1)
    count++
  }
  return dates
}

// 统一图表通用 Tooltip 配置
const commonTooltip = {
  backgroundColor: 'rgba(255, 255, 255, 0.96)',
  borderColor: '#cfd7d4',
  borderWidth: 1,
  textStyle: { color: '#20252b', fontSize: 12 },
  extraCssText: 'box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12); border-radius: 8px; padding: 10px 14px;'
}

// 1. 初始化全景业务走势图 (双轴 / 复合 / 面积)
function initTrendChart() {
  if (!trendChartRef.value || !stats.value) return
  if (!trendChart) {
    trendChart = init(trendChartRef.value)
  }

  // 索引聚合数据点
  const dateMap = new Map<
    string,
    {
      completedAmount: number
      completedCount: number
      createdCount: number
      pendingCount: number
      cancelledCount: number
    }
  >()

  if (stats.value.dailyTrend && stats.value.dailyTrend.length > 0) {
    stats.value.dailyTrend.forEach(d => {
      dateMap.set(d.date, {
        completedAmount: d.completedAmount || 0,
        completedCount: d.completedCount || 0,
        createdCount: d.createdCount || 0,
        pendingCount: d.pendingCount ?? Math.max(0, (d.createdCount || 0) - (d.completedCount || 0) - (d.cancelledCount || 0)),
        cancelledCount: d.cancelledCount || 0
      })
    })
  } else if (stats.value.userStats && stats.value.userStats.length > 0) {
    stats.value.userStats.forEach(user => {
      user.timeline.forEach(t => {
        const cur = dateMap.get(t.date) || {
          completedAmount: 0,
          completedCount: 0,
          createdCount: 0,
          pendingCount: 0,
          cancelledCount: 0
        }
        cur.completedAmount += t.amount || 0
        cur.completedCount += t.count || 0
        cur.createdCount += t.count || 0
        dateMap.set(t.date, cur)
      })
    })
  }

  // 计算连续日期序列，避免中间无业务记录的日期断档产生失真
  let dates: string[] = []
  if (timeRange.value === '7') {
    dates = generateDateSequence(7)
  } else if (timeRange.value === '30') {
    dates = generateDateSequence(30)
  } else if (timeRange.value === '90') {
    dates = generateDateSequence(90)
  } else {
    // 全部 (基于已有日期跨度补齐连续日期)
    const existingDates = Array.from(dateMap.keys()).sort()
    if (existingDates.length > 0) {
      const minDate = existingDates[0]
      const maxDate = existingDates[existingDates.length - 1]
      dates = generateContinuousDates(minDate, maxDate)
    } else {
      dates = generateDateSequence(7)
    }
  }

  const completedAmounts = dates.map(d => dateMap.get(d)?.completedAmount || 0)
  const completedCounts = dates.map(d => dateMap.get(d)?.completedCount || 0)
  const createdCounts = dates.map(d => dateMap.get(d)?.createdCount || 0)

  let series: any[] = []
  let yAxis: any[] = []

  if (trendViewMode.value === 'composite') {
    // 双轴全景复合：左侧已开金额折线面积，右侧申请单数与已开单数双柱状对比
    yAxis = [
      {
        type: 'value',
        name: '已开金额 (¥)',
        axisLine: { show: false },
        splitLine: { lineStyle: { color: '#f0f3f2' } },
        axisLabel: { formatter: '¥{value}' }
      },
      {
        type: 'value',
        name: '发票单数 (单)',
        minInterval: 1,
        axisLine: { show: false },
        splitLine: { show: false },
        axisLabel: { formatter: '{value} 单' }
      }
    ]
    series = [
      {
        name: '已开金额 (¥)',
        type: 'line',
        yAxisIndex: 0,
        smooth: true,
        showSymbol: dates.length <= 15,
        symbolSize: 6,
        itemStyle: { color: '#12715b' },
        lineStyle: { width: 3, color: '#12715b' },
        areaStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(18, 113, 91, 0.35)' },
            { offset: 1, color: 'rgba(18, 113, 91, 0.02)' }
          ])
        },
        data: completedAmounts
      },
      {
        name: '申请单数 (单)',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 16,
        itemStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#2a9d8f' },
            { offset: 1, color: '#78aea1' }
          ]),
          borderRadius: [4, 4, 0, 0]
        },
        data: createdCounts
      },
      {
        name: '已开单数 (单)',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 16,
        itemStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#d69a2d' },
            { offset: 1, color: '#f5cc7f' }
          ]),
          borderRadius: [4, 4, 0, 0]
        },
        data: completedCounts
      }
    ]
  } else if (trendViewMode.value === 'amount') {
    yAxis = [
      {
        type: 'value',
        name: '开票金额 (¥)',
        splitLine: { lineStyle: { color: '#f0f3f2' } },
        axisLabel: { formatter: '¥{value}' }
      }
    ]
    series = [
      {
        name: '已开票金额',
        type: 'line',
        smooth: true,
        showSymbol: true,
        symbolSize: 6,
        itemStyle: { color: '#12715b' },
        lineStyle: { width: 3, color: '#12715b' },
        markPoint: {
          data: [
            { type: 'max', name: '最高单日' },
            { type: 'min', name: '最低单日' }
          ]
        },
        areaStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(18, 113, 91, 0.4)' },
            { offset: 1, color: 'rgba(18, 113, 91, 0.02)' }
          ])
        },
        data: completedAmounts
      }
    ]
  } else if (trendViewMode.value === 'created') {
    yAxis = [
      {
        type: 'value',
        name: '申请单数 (单)',
        minInterval: 1,
        splitLine: { lineStyle: { color: '#f0f3f2' } }
      }
    ]
    series = [
      {
        name: '申请发票单数',
        type: 'bar',
        barMaxWidth: 30,
        itemStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#2a9d8f' },
            { offset: 1, color: '#78aea1' }
          ]),
          borderRadius: [4, 4, 0, 0]
        },
        markPoint: {
          data: [{ type: 'max', name: '申请最高峰' }]
        },
        data: createdCounts
      }
    ]
  } else if (trendViewMode.value === 'completed') {
    yAxis = [
      {
        type: 'value',
        name: '已开单数 (单)',
        minInterval: 1,
        splitLine: { lineStyle: { color: '#f0f3f2' } }
      }
    ]
    series = [
      {
        name: '已开发票单数',
        type: 'bar',
        barMaxWidth: 30,
        itemStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#d69a2d' },
            { offset: 1, color: '#f5cc7f' }
          ]),
          borderRadius: [4, 4, 0, 0]
        },
        markPoint: {
          data: [{ type: 'max', name: '开票最高峰' }]
        },
        data: completedCounts
      }
    ]
  } else {
    // 按用户堆叠趋势（多用户保护：最多独立呈现 TOP 7 用户，其余聚合为“其他用户”）
    yAxis = [{ type: 'value', splitLine: { lineStyle: { color: '#f0f3f2' } } }]
    const rawUsers = stats.value.userStats || []
    if (rawUsers.length > 8) {
      const sorted = [...rawUsers].sort(
        (a, b) => b.completedCount + b.pendingCount - (a.completedCount + a.pendingCount)
      )
      const top7 = sorted.slice(0, 7)
      const others = sorted.slice(7)
      const otherTimelineMap = new Map<string, number>()
      others.forEach(u => {
        u.timeline.forEach(t => {
          otherTimelineMap.set(t.date, (otherTimelineMap.get(t.date) || 0) + (t.count || 0))
        })
      })

      series = top7.map((user, idx) => ({
        name: user.username,
        type: 'line',
        smooth: true,
        data: dates.map(date => {
          const point = user.timeline.find(t => t.date === date)
          return point ? point.count : 0
        }),
        itemStyle: { color: THEME_COLORS[idx % THEME_COLORS.length] }
      }))
      series.push({
        name: '其他用户',
        type: 'line',
        smooth: true,
        data: dates.map(date => otherTimelineMap.get(date) || 0),
        itemStyle: { color: '#8898aa' }
      })
    } else {
      series = rawUsers.map((user, idx) => ({
        name: user.username,
        type: 'line',
        smooth: true,
        data: dates.map(date => {
          const point = user.timeline.find(t => t.date === date)
          return point ? point.count : 0
        }),
        itemStyle: { color: THEME_COLORS[idx % THEME_COLORS.length] }
      }))
    }
  }

  const option = {
    color: THEME_COLORS,
    tooltip: {
      ...commonTooltip,
      trigger: 'axis',
      axisPointer: { type: 'cross', crossStyle: { color: '#cfd7d4' } },
      formatter: (params: any[]) => {
        if (!params || params.length === 0) return ''
        const date = params[0].axisValue || params[0].name
        const item = dateMap.get(date) || {
          completedAmount: 0,
          completedCount: 0,
          createdCount: 0,
          pendingCount: 0,
          cancelledCount: 0
        }
        const pending = item.pendingCount || 0
        const cancelled = item.cancelledCount || 0
        return `
          <div style="font-weight: 600; margin-bottom: 6px; border-bottom: 1px solid #edf2f0; padding-bottom: 4px;">📅 日期: ${date}</div>
          <div style="display: flex; justify-content: space-between; gap: 16px; margin-bottom: 3px;">
            <span><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:#2a9d8f;margin-right:6px;"></span>申请发票数:</span>
            <b>${item.createdCount || 0} 单</b>
          </div>
          <div style="display: flex; justify-content: space-between; gap: 16px; margin-bottom: 3px;">
            <span><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:#d69a2d;margin-right:6px;"></span>已开发票数:</span>
            <b>${item.completedCount || 0} 单</b>
          </div>
          ${cancelled > 0 ? `
          <div style="display: flex; justify-content: space-between; gap: 16px; margin-bottom: 3px;">
            <span><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:#909399;margin-right:6px;"></span>已取消申请:</span>
            <b style="color: #909399;">${cancelled} 单</b>
          </div>` : ''}
          <div style="display: flex; justify-content: space-between; gap: 16px; margin-bottom: 3px;">
            <span><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:#12715b;margin-right:6px;"></span>已开票金额:</span>
            <b>¥${formatMoney(item.completedAmount || 0)}</b>
          </div>
          ${pending > 0 ? `
          <div style="margin-top: 4px; padding-top: 4px; border-top: 1px dashed #e2e7e5; color: #d69a2d; font-size: 11px;">
            ⏳ 待开票处理: <b>${pending} 单</b>
          </div>` : (item.createdCount > 0 ? `
          <div style="margin-top: 4px; padding-top: 4px; border-top: 1px dashed #e2e7e5; color: #12715b; font-size: 11px;">
            ✅ 当日发票已全开完${cancelled > 0 ? `（含 ${cancelled} 单已取消）` : ''}
          </div>` : '')}
        `
      }
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemGap: 16
    },
    grid: {
      top: 45,
      left: 15,
      right: trendViewMode.value === 'composite' ? 25 : 15,
      bottom: 40,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#cfd7d4' } },
      axisLabel: {
        color: '#74808b',
        rotate: dates.length > 12 ? 30 : 0
      }
    },
    yAxis,
    series
  }

  trendChart.setOption(option, true)
}

// 2. 初始化开票类目/项目分布图 (南丁格尔玫瑰图 / 环形饼图)
function initTypeChart() {
  if (!typeChartRef.value || !stats.value) return
  if (!typeChart) {
    typeChart = init(typeChartRef.value)
  }

  let data: Array<{ name: string; value: number; count: number; amount: number }> = []

  if (stats.value.typeStats && stats.value.typeStats.length > 0) {
    data = stats.value.typeStats.map(item => ({
      name: item.invoiceType || '其他',
      value: typeMetricMode.value === 'amount' ? item.amount : item.count,
      count: item.count,
      amount: item.amount
    }))
  } else {
    // 降级使用默认类目
    data = [
      {
        name: '技术服务费',
        value: typeMetricMode.value === 'amount' ? stats.value.totalAmount : stats.value.completedInvoices,
        count: stats.value.completedInvoices,
        amount: stats.value.totalAmount
      }
    ]
  }

  const option = {
    color: THEME_COLORS,
    tooltip: {
      ...commonTooltip,
      trigger: 'item',
      formatter: (params: any) => {
        const d = params?.data || {}
        return `
          <div style="font-weight: 600; margin-bottom: 4px;">${d.name || '其他'}</div>
          <div>开票金额: <b>¥${formatMoney(d.amount)}</b></div>
          <div>发票笔数: <b>${d.count || 0} 笔</b></div>
          <div>占比: <b>${params.percent || 0}%</b></div>
        `
      }
    },
    legend: {
      orient: 'horizontal',
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8
    },
    series: [
      {
        name: '开票类目',
        type: 'pie',
        radius: ['38%', '68%'],
        center: ['50%', '45%'],
        roseType: data.length > 3 ? 'radius' : undefined,
        itemStyle: {
          borderRadius: 6,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: true,
          formatter: '{b}: {d}%',
          color: '#4d5863'
        },
        emphasis: {
          label: { show: true, fontWeight: 'bold' },
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.2)'
          }
        },
        data
      }
    ]
  }

  typeChart.setOption(option, true)
}

// 3. 初始化企业抬头开票排行 TOP 10 (横向柱状图)
function initCompanyChart() {
  if (!companyChartRef.value || !stats.value) return
  if (!companyChart) {
    companyChart = init(companyChartRef.value)
  }

  let companies: Array<{ name: string; amount: number; count: number }> = []

  if (stats.value.companyTopStats && stats.value.companyTopStats.length > 0) {
    const sorted = [...stats.value.companyTopStats].sort((a, b) =>
      companyMetricMode.value === 'amount'
        ? a.amount - b.amount
        : a.count - b.count
    )
    companies = sorted.slice(Math.max(0, sorted.length - 10)).map(c => ({
      name: c.companyName,
      amount: c.amount,
      count: c.count
    }))
  } else {
    // 模拟或根据 userStats 构造数据
    companies = [
      { name: '暂无企业数据', amount: 0, count: 0 }
    ]
  }

  const names = companies.map(c => c.name)
  const values = companies.map(c =>
    companyMetricMode.value === 'amount' ? c.amount : c.count
  )

  const option = {
    tooltip: {
      ...commonTooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any[]) => {
        if (!params || !params[0]) return ''
        const idx = params[0].dataIndex
        const c = companies[idx]
        if (!c) return ''
        return `
          <div style="font-weight: 600; margin-bottom: 4px;">${c.name}</div>
          <div>开票金额: <b>¥${formatMoney(c.amount)}</b></div>
          <div>开票单数: <b>${c.count} 笔</b></div>
        `
      }
    },
    grid: {
      top: 15,
      left: 10,
      right: 45,
      bottom: 10,
      containLabel: true
    },
    xAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#f0f3f2' } },
      axisLabel: {
        formatter: (val: number) =>
          companyMetricMode.value === 'amount' ? `¥${val}` : `${val}单`
      }
    },
    yAxis: {
      type: 'category',
      data: names,
      axisLine: { lineStyle: { color: '#cfd7d4' } },
      axisTick: { show: false },
      axisLabel: {
        width: 140,
        overflow: 'truncate',
        color: '#4d5863'
      }
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 18,
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: (params: any) => {
            // 前 3 名给予金色/橙色高光，其余采用经典翡翠绿
            const isTop3 = params.dataIndex >= companies.length - 3
            if (isTop3) {
              return new graphic.LinearGradient(1, 0, 0, 0, [
                { offset: 0, color: '#d69a2d' },
                { offset: 1, color: '#f5cc7f' }
              ])
            }
            return new graphic.LinearGradient(1, 0, 0, 0, [
              { offset: 0, color: '#12715b' },
              { offset: 1, color: '#4b9584' }
            ])
          }
        },
        label: {
          show: true,
          position: 'right',
          color: '#4d5863',
          formatter: (params: any) =>
            companyMetricMode.value === 'amount'
              ? `¥${formatMoney(params.value)}`
              : `${params.value}单`
        },
        data: values
      }
    ]
  }

  companyChart.setOption(option, true)
}

// 4. 初始化用户发票分布与贡献占比 (精致环形图)
function initUserPieChart() {
  if (!userPieChartRef.value || !stats.value) return
  if (!userPieChart) {
    userPieChart = init(userPieChartRef.value)
  }

  const rawUsers = stats.value.userStats || []
  const formattedData = rawUsers.map(user => ({
    name: user.username,
    value:
      userMetricMode.value === 'count'
        ? user.completedCount + user.pendingCount
        : user.totalAmount,
    completed: user.completedCount,
    pending: user.pendingCount,
    totalAmount: user.totalAmount,
    totalCount: user.completedCount + user.pendingCount
  }))

  let data = formattedData
  if (formattedData.length > 8) {
    const sorted = [...formattedData].sort((a, b) => b.value - a.value)
    const top7 = sorted.slice(0, 7)
    const others = sorted.slice(7)
    const otherAgg = others.reduce(
      (acc, cur) => ({
        name: '其他用户',
        value: acc.value + cur.value,
        completed: acc.completed + cur.completed,
        pending: acc.pending + cur.pending,
        totalAmount: acc.totalAmount + cur.totalAmount,
        totalCount: acc.totalCount + cur.totalCount
      }),
      { name: '其他用户', value: 0, completed: 0, pending: 0, totalAmount: 0, totalCount: 0 }
    )
    data = [...top7, otherAgg]
  }

  const totalValue = data.reduce((acc, cur) => acc + cur.value, 0)

  const option = {
    color: THEME_COLORS,
    tooltip: {
      ...commonTooltip,
      trigger: 'item',
      formatter: (params: any) => {
        const d = params?.data || {}
        return `
          <div style="font-weight: 600; margin-bottom: 4px;">${d.name || ''}</div>
          <div>开票金额: <b>¥${formatMoney(d.totalAmount)}</b></div>
          <div>已开发票: <b>${d.completed || 0} 笔</b></div>
          <div>待开发票: <b>${d.pending || 0} 笔</b></div>
          <div>发票总计: <b>${d.totalCount || 0} 笔</b></div>
          <div>当前占比: <b>${params.percent || 0}%</b></div>
        `
      }
    },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: '2%',
      top: 'middle',
      icon: 'circle',
      itemGap: 10,
      formatter: (name: string) => {
        const item = data.find(d => d.name === name)
        if (!item) return name
        const ratio = totalValue > 0 ? ((item.value / totalValue) * 100).toFixed(1) : '0'
        return `${name.padEnd(10, ' ')} ${ratio}%`
      }
    },
    series: [
      {
        name: '用户发票贡献',
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['38%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 6,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 'bold',
            formatter: '{b}\n{d}%'
          },
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.2)'
          }
        },
        data
      }
    ]
  }

  userPieChart.setOption(option, true)
}

// 5. 初始化发票金额区间画像 (柱状直方图 + 趋势折线)
function initAmountRangeChart() {
  if (!amountRangeChartRef.value || !stats.value) return
  if (!amountRangeChart) {
    amountRangeChart = init(amountRangeChartRef.value)
  }

  let ranges = stats.value.amountRangeStats || []
  if (ranges.length === 0) {
    ranges = [
      { rangeLabel: '< 500元', minAmount: 0, maxAmount: 500, count: 0, amount: 0 },
      { rangeLabel: '500 - 2,000元', minAmount: 500, maxAmount: 2000, count: 0, amount: 0 },
      { rangeLabel: '2,000 - 5,000元', minAmount: 2000, maxAmount: 5000, count: 0, amount: 0 },
      { rangeLabel: '5,000 - 10,000元', minAmount: 5000, maxAmount: 10000, count: 0, amount: 0 },
      { rangeLabel: '≥ 10,000元', minAmount: 10000, maxAmount: null, count: 0, amount: 0 }
    ]
  }

  const labels = ranges.map(r => r.rangeLabel)
  const counts = ranges.map(r => r.count)
  const amounts = ranges.map(r => r.amount)

  const option = {
    tooltip: {
      ...commonTooltip,
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params: any[]) => {
        if (!params || !params[0]) return ''
        const idx = params[0].dataIndex
        const r = ranges[idx]
        if (!r) return ''
        return `
          <div style="font-weight: 600; margin-bottom: 4px;">区间: ${r.rangeLabel}</div>
          <div>发票数量: <b>${r.count} 笔</b></div>
          <div>区间金额合计: <b>¥${formatMoney(r.amount)}</b></div>
        `
      }
    },
    grid: {
      top: 45,
      left: 15,
      right: 25,
      bottom: 25,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: labels,
      axisLine: { lineStyle: { color: '#cfd7d4' } },
      axisLabel: { color: '#4d5863' }
    },
    yAxis: [
      {
        type: 'value',
        name: '发票笔数 (单)',
        minInterval: 1,
        splitLine: { lineStyle: { color: '#f0f3f2' } }
      },
      {
        type: 'value',
        name: '区间金额 (¥)',
        splitLine: { show: false },
        axisLabel: { formatter: '¥{value}' }
      }
    ],
    series: [
      {
        name: '发票笔数',
        type: 'bar',
        barMaxWidth: 32,
        yAxisIndex: 0,
        itemStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#2a9d8f' },
            { offset: 1, color: '#78aea1' }
          ]),
          borderRadius: [4, 4, 0, 0]
        },
        data: counts
      },
      {
        name: '金额合计',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        itemStyle: { color: '#d69a2d' },
        lineStyle: { width: 3, color: '#d69a2d' },
        data: amounts
      }
    ]
  }

  amountRangeChart.setOption(option, true)
}

// 6. 初始化 24 小时开票申请热度峰值 (时段分布折线/面积图)
function initHourChart() {
  if (!hourChartRef.value || !stats.value) return
  if (!hourChart) {
    hourChart = init(hourChartRef.value)
  }

  const hours = Array.from({ length: 24 }, (_, i) => `${i.toString().padStart(2, '0')}:00`)
  const hourMap = new Map<number, number>()

  if (stats.value.hourDistribution && stats.value.hourDistribution.length > 0) {
    stats.value.hourDistribution.forEach(h => hourMap.set(h.hour, h.count))
  }

  const counts = Array.from({ length: 24 }, (_, i) => hourMap.get(i) || 0)

  const option = {
    tooltip: {
      ...commonTooltip,
      trigger: 'axis',
      formatter: (params: any[]) => {
        if (!params || !params[0]) return ''
        const item = params[0]
        return `
          <div style="font-weight: 600; margin-bottom: 4px;">时段: ${item.name}</div>
          <div>申请发票数: <b>${item.value || 0} 笔</b></div>
        `
      }
    },
    grid: {
      top: 30,
      left: 15,
      right: 15,
      bottom: 25,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: hours,
      axisLine: { lineStyle: { color: '#cfd7d4' } },
      axisLabel: {
        color: '#74808b',
        interval: 2
      }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f3f2' } }
    },
    series: [
      {
        name: '申请发票数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        itemStyle: { color: '#12715b' },
        lineStyle: { width: 3, color: '#12715b' },
        markPoint: {
          data: [{ type: 'max', name: '最高峰时段' }]
        },
        areaStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(18, 113, 91, 0.4)' },
            { offset: 1, color: 'rgba(18, 113, 91, 0.02)' }
          ])
        },
        data: counts
      }
    ]
  }

  hourChart.setOption(option, true)
}

// 刷新所有图表
function renderAllCharts() {
  nextTick(() => {
    initTrendChart()
    initTypeChart()
    initCompanyChart()
    initUserPieChart()
    initAmountRangeChart()
    initHourChart()
  })
}

// 加载数据
async function loadData() {
  try {
    await dashboardStore.fetchStats()
  } catch {
    return
  }
  renderAllCharts()
}

// 加载供应商结算历史记录
async function loadSettlementHistory() {
  if (!isAdmin.value) return

  loadingSettlementHistory.value = true
  try {
    settlementHistory.value = await getSettlementHistory()
  } catch (error: any) {
    console.error('加载结算历史失败:', error)
  } finally {
    loadingSettlementHistory.value = false
  }
}

// 处理结算成功：修复 #7 改为 Promise.all，确保两个刷新请求并行完成后数据一致
async function handleSettlementSuccess() {
  await Promise.all([loadData(), loadSettlementHistory()])
}

// 格式化日期
function formatDate(dateString: string) {
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 监听各个维度的视图与指标切换
watch(timeRange, () => initTrendChart())
watch(trendViewMode, () => initTrendChart())
watch(typeMetricMode, () => initTypeChart())
watch(companyMetricMode, () => initCompanyChart())
watch(userMetricMode, () => initUserPieChart())

// 窗口尺寸自适应
function handleResize() {
  if (trendChart && !trendChart.isDisposed()) trendChart.resize()
  if (typeChart && !typeChart.isDisposed()) typeChart.resize()
  if (companyChart && !companyChart.isDisposed()) companyChart.resize()
  if (userPieChart && !userPieChart.isDisposed()) userPieChart.resize()
  if (amountRangeChart && !amountRangeChart.isDisposed()) amountRangeChart.resize()
  if (hourChart && !hourChart.isDisposed()) hourChart.resize()
}

onMounted(() => {
  loadData()
  if (isAdmin.value) {
    loadSettlementHistory()
  }
  window.addEventListener('resize', handleResize)
  if (typeof ResizeObserver !== 'undefined' && dashboardRootRef.value) {
    resizeObserver = new ResizeObserver(() => handleResize())
    resizeObserver.observe(dashboardRootRef.value)
  }
})

onBeforeUnmount(() => {
  if (trendChart && !trendChart.isDisposed()) trendChart.dispose()
  if (typeChart && !typeChart.isDisposed()) typeChart.dispose()
  if (companyChart && !companyChart.isDisposed()) companyChart.dispose()
  if (userPieChart && !userPieChart.isDisposed()) userPieChart.dispose()
  if (amountRangeChart && !amountRangeChart.isDisposed()) amountRangeChart.dispose()
  if (hourChart && !hourChart.isDisposed()) hourChart.dispose()
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.dashboard-page {
  padding-bottom: 40px;
}

/* 顶部操作与筛选栏 */
.dashboard-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  gap: 16px;
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--color-primary-soft, #e9f4f0);
  color: var(--color-primary, #12715b);
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
}

.live-dot {
  width: 8px;
  height: 8px;
  background: #12715b;
  border-radius: 50%;
  animation: live-pulse 2s infinite ease-in-out;
}

@keyframes live-pulse {
  0% { transform: scale(0.9); opacity: 1; }
  50% { transform: scale(1.3); opacity: 0.5; }
  100% { transform: scale(0.9); opacity: 1; }
}

.toolbar-hint {
  font-size: 13px;
  color: var(--color-text-muted, #74808b);
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.refresh-btn {
  font-weight: 500;
}

.quick-action-btn {
  font-weight: 600;
  border-radius: 6px;
}

/* 关键指标 KPI 卡片群 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid var(--color-border, #e2e7e5);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  padding: 18px 20px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.08);
}

.stat-card-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.stat-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  font-size: 20px;
}

.stat-icon.primary {
  background: #e9f4f0;
  color: #12715b;
}

.stat-icon.warning {
  background: #fff6df;
  color: #d69a2d;
}

.stat-icon.success {
  background: #eaf8ee;
  color: #2a9d8f;
}

.stat-icon.accent {
  background: #fdf0d5;
  color: #b66d0b;
}

.stat-icon.cyan {
  background: #e6f6f7;
  color: #0077b6;
}

.stat-card-settled .stat-icon {
  background: #eaf8ee;
  color: #12715b;
}

.stat-card-unsettled .stat-icon {
  background: #fff6df;
  color: #d69a2d;
}

.stat-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 13px;
  color: var(--color-text-secondary, #4d5863);
  font-weight: 500;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #20252b;
  letter-spacing: -0.5px;
}

.stat-value.warning-text {
  color: #d69a2d;
}

.stat-value.success-text {
  color: #12715b;
}

.stat-footer-pill {
  font-size: 12px;
  color: var(--color-text-muted, #74808b);
  background: #f8faf9;
  padding: 4px 10px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
}

.stat-footer-pill strong {
  color: #20252b;
  margin-left: 2px;
}

.stat-footer-pill.warning-pill {
  background: #fff9ed;
  color: #b66d0b;
}

.stat-footer-pill.success-pill {
  background: #edf8f3;
  color: #12715b;
}

/* 图表区域网格 */
.dashboard-charts-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.charts-row {
  row-gap: 20px;
}

.chart-card {
  border-radius: 12px;
  border: 1px solid var(--color-border, #e2e7e5);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: var(--color-primary-soft, #e9f4f0);
  color: var(--color-primary, #12715b);
  border-radius: 8px;
  font-size: 18px;
}

.title-text {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #20252b;
}

.subtitle-text {
  margin: 2px 0 0 0;
  font-size: 12px;
  color: #74808b;
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.chart-container {
  width: 100%;
  height: 360px;
}

.main-trend-chart {
  height: 360px;
}

.chart-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 360px;
}

.table-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px 20px;
}

@media (max-width: 768px) {
  .dashboard-toolbar {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .toolbar-right {
    width: 100%;
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .toolbar-right .quick-action-btn,
  .toolbar-right .refresh-btn {
    flex: 1;
    min-width: 0;
  }

  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .header-controls {
    width: 100%;
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .header-controls :deep(.el-radio-group) {
    display: flex;
    width: 100%;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    flex-wrap: nowrap;
  }

  .header-controls :deep(.el-radio-button) {
    flex: 1 0 auto;
  }

  .header-controls :deep(.el-radio-button__inner) {
    width: 100%;
    padding: 6px 10px;
    font-size: 12px;
    white-space: nowrap;
  }

  .chart-container,
  .chart-empty {
    height: 270px;
  }
}
</style>

