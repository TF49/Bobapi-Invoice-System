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
          <span class="toolbar-hint">汇总系统发票申请、开票走势、类目分布、资金额度与履约时效全景分析</span>
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
            @click="openGeneralSettlement"
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
              <span>{{ (stats?.unsettledAmount || 0) < 0 ? '预结待抵扣' : '待结算' }}</span>
            </div>
          </div>
        </SpotlightCard>
      </AnimatedContent>

      <!-- 图表区域 -->
      <div class="dashboard-charts-grid" v-loading="loading">
        <!-- ROW 1: 全景业务走势复合图 + 开票类目分布 (优化图一：解决小标签重叠与空心问题，支持环形/玫瑰/排行三种视图) -->
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

          <!-- 2. 开票类目/项目分布 (优化图一) -->
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
                    <el-radio-group v-model="typeViewMode" size="small">
                      <el-radio-button value="donut">环形</el-radio-button>
                      <el-radio-button value="rose">玫瑰</el-radio-button>
                      <el-radio-button value="rank">排行</el-radio-button>
                    </el-radio-group>
                    <el-radio-group v-model="typeMetricMode" size="small">
                      <el-radio-button value="amount">按金额</el-radio-button>
                      <el-radio-button value="count">按笔数</el-radio-button>
                    </el-radio-group>
                  </div>
                </div>
              </template>

              <!-- 环形/玫瑰图视图 -->
              <div v-show="hasTypeStats && typeViewMode !== 'rank'" ref="typeChartRef" class="chart-container"></div>

              <!-- 排行列表视图（彻底解决极小占比 0.25%/0.7% 重叠并提供进度条详情） -->
              <div v-show="hasTypeStats && typeViewMode === 'rank'" class="category-rank-container">
                <div class="rank-list-header">
                  <span>排名 / 类目名称</span>
                  <span>金额 & 笔数 (占比)</span>
                </div>
                <div class="category-rank-scroll">
                  <div
                    v-for="(item, idx) in formattedTypeRankList"
                    :key="item.name"
                    class="category-rank-item"
                  >
                    <div class="rank-left">
                      <span class="rank-badge" :class="'rank-' + (idx + 1)">{{ idx + 1 }}</span>
                      <span class="cat-dot" :style="{ background: item.color }"></span>
                      <span class="cat-name" :title="item.name">{{ item.name }}</span>
                    </div>
                    <div class="rank-right">
                      <div class="rank-values">
                        <strong class="rank-amount">¥{{ formatMoney(item.amount) }}</strong>
                        <span class="rank-count">{{ item.count }} 笔</span>
                        <span class="rank-percent-pill">{{ item.percent }}%</span>
                      </div>
                      <div class="cat-progress-bg">
                        <div
                          class="cat-progress-bar"
                          :style="{ width: item.percent + '%', background: item.color }"
                        ></div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div v-show="!hasTypeStats" class="chart-empty">
                <el-empty description="暂无类目统计数据" :image-size="80" />
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- ROW 2: 全系统生态分析：渠道与票种画像 (NEW) + 开票履约服务时效 SLA (NEW) -->
        <el-row :gutter="20" class="charts-row">
          <!-- 3. 提交渠道与发票票种画像 (双环模型) -->
          <el-col :xs="24" :lg="12">
            <el-card class="chart-card">
              <template #header>
                <div class="card-header">
                  <div class="header-title">
                    <span class="header-icon"><Connection /></span>
                    <div>
                      <h3 class="title-text">提交渠道与发票票种画像</h3>
                      <p class="subtitle-text">API自动化与网页人工录入、增值税专票/普票与红冲审计</p>
                    </div>
                  </div>
                  <div class="header-controls">
                    <div class="header-pill-badge primary">
                      <span>API 渗透率: <strong>{{ apiChannelRate }}%</strong></span>
                    </div>
                    <div class="header-pill-badge" :class="redFlushPillClass">
                      <span>红冲率: <strong>{{ Number(stats?.redFlushStats?.redFlushRate || 0).toFixed(1) }}%</strong></span>
                    </div>
                  </div>
                </div>
              </template>
              <div v-show="hasChannelOrCategoryStats" ref="channelCategoryChartRef" class="chart-container"></div>
              <div v-show="!hasChannelOrCategoryStats" class="chart-empty">
                <el-empty description="暂无渠道与票种分布数据" :image-size="80" />
              </div>
            </el-card>
          </el-col>

          <!-- 4. 开票履约服务时效 SLA 分析 -->
          <el-col :xs="24" :lg="12">
            <el-card class="chart-card">
              <template #header>
                <div class="card-header">
                  <div class="header-title">
                    <span class="header-icon"><Timer /></span>
                    <div>
                      <h3 class="title-text">开票履约服务时效 (SLA)</h3>
                      <p class="subtitle-text">从申请提交至发票完成开具的响应耗时分布与履约率</p>
                    </div>
                  </div>
                  <div class="header-controls">
                    <div class="header-pill-badge success">
                      <span>2h 极速达成率: <strong>{{ Number(stats?.fulfillmentStats?.fastCompletionRate || 0).toFixed(1) }}%</strong></span>
                    </div>
                    <div class="header-pill-badge">
                      <span>平均耗时: <strong>{{ formattedAvgDuration }}</strong></span>
                    </div>
                  </div>
                </div>
              </template>
              <div v-show="hasFulfillmentStats" ref="fulfillmentChartRef" class="chart-container"></div>
              <div v-show="!hasFulfillmentStats" class="chart-empty">
                <el-empty description="暂无开票时效统计数据" :image-size="80" />
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- ROW 3: 平台资金与额度流动走势 (NEW) + 企业抬头开票排行 TOP 10 -->
        <el-row :gutter="20" class="charts-row">
          <!-- 5. 平台资金与额度流动走势 -->
          <el-col :xs="24" :lg="12">
            <el-card class="chart-card">
              <template #header>
                <div class="card-header">
                  <div class="header-title">
                    <span class="header-icon"><Coin /></span>
                    <div>
                      <h3 class="title-text">资金与额度收支流水流动</h3>
                      <p class="subtitle-text">近 30 天用户充值入账与发票消费扣减每日双向对比</p>
                    </div>
                  </div>
                  <div class="header-controls">
                    <div class="header-pill-badge success">
                      <span>审批通过率: <strong>{{ Number(stats?.fundFlowStats?.approvalRate || 100).toFixed(1) }}%</strong></span>
                    </div>
                    <div v-if="(stats?.fundFlowStats?.pendingRecharges || 0) > 0" class="header-pill-badge warning">
                      <span>待审: <strong>{{ stats?.fundFlowStats?.pendingRecharges }} 笔</strong></span>
                    </div>
                  </div>
                </div>
              </template>
              <div v-show="hasFundFlowStats" ref="fundFlowChartRef" class="chart-container"></div>
              <div v-show="!hasFundFlowStats" class="chart-empty">
                <el-empty description="暂无资金流水流动数据" :image-size="80" />
              </div>
            </el-card>
          </el-col>

          <!-- 6. 企业抬头开票排行 TOP 10 -->
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
        </el-row>

        <!-- ROW 4: 用户发票分布与贡献 + 发票金额区间画像 -->
        <el-row :gutter="20" class="charts-row">
          <!-- 7. 用户发票分布与贡献占比 -->
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

          <!-- 8. 发票金额区间画像 -->
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
        </el-row>

        <!-- ROW 5: 24小时时段申请热度峰值 -->
        <el-row :gutter="20" class="charts-row">
          <el-col :xs="24">
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

        <!-- ROW 5: 供应商资金结算与对账中心 (优化图二：融合月度对账 ECharts 与紧凑流水表，消灭全宽空白) -->
        <el-row v-if="isAdmin" :gutter="20" class="charts-row">
          <el-col :xs="24">
            <el-card class="chart-card settlement-hub-card">
              <template #header>
                <div class="card-header">
                  <div class="header-title">
                    <span class="header-icon"><Money /></span>
                    <div>
                      <h3 class="title-text">供应商资金结算与对账中心</h3>
                      <p class="subtitle-text">已开票成本核算、供应商结款履约与实时对账走势</p>
                    </div>
                  </div>
                  <div class="header-controls">
                    <el-button type="success" size="small" class="quick-action-btn" @click="openGeneralSettlement">
                      <el-icon><Money /></el-icon>
                      <span>发起结算</span>
                    </el-button>
                    <el-button size="small" @click="loadSettlementHistory">
                      <el-icon><Refresh /></el-icon>
                      <span>刷新记录</span>
                    </el-button>
                  </div>
                </div>
              </template>

              <!-- 顶部对账 KPI 概要条 -->
              <div class="settlement-kpi-bar">
                <div class="settlement-kpi-item">
                  <span class="kpi-sub-label">累计已开金额</span>
                  <span class="kpi-sub-val">¥{{ formatMoney(stats?.totalAmount || 0) }}</span>
                </div>
                <div class="settlement-kpi-divider"></div>
                <div class="settlement-kpi-item">
                  <span class="kpi-sub-label">累计已结款项</span>
                  <span class="kpi-sub-val success">¥{{ formatMoney(stats?.totalSettledAmount || 0) }}</span>
                </div>
                <div class="settlement-kpi-divider"></div>
                <div class="settlement-kpi-item">
                  <span class="kpi-sub-label">当前待结/结余</span>
                  <span class="kpi-sub-val" :class="(stats?.unsettledAmount || 0) < 0 ? 'accent' : 'warning'">
                    ¥{{ formatMoney(stats?.unsettledAmount || 0) }}
                    <el-tag size="small" :type="(stats?.unsettledAmount || 0) < 0 ? 'success' : 'warning'" effect="plain">
                      {{ (stats?.unsettledAmount || 0) < 0 ? '预结待抵扣' : '待付款' }}
                    </el-tag>
                  </span>
                </div>
                <div class="settlement-kpi-divider"></div>
                <div class="settlement-kpi-item progress-item">
                  <div class="progress-info">
                    <span class="kpi-sub-label">结算履约率</span>
                    <span class="kpi-sub-val">{{ settlementProgressRate }}%</span>
                  </div>
                  <el-progress
                    :percentage="Number(settlementProgressRate)"
                    :stroke-width="8"
                    color="#12715b"
                    :show-text="false"
                  />
                </div>
              </div>

              <!-- 四大出票通道已开额度明细（供结款核对参考） -->
              <div class="settlement-channel-matrix">
                <div class="matrix-header">
                  <span class="matrix-title">出票主体与票种已开额度明细 (上游结款依据)</span>
                  <span class="matrix-hint">按出票主体公司与发票票种分类统计的已开具票面金额与参考费率</span>
                </div>
                <div class="channel-card-grid">
                  <div
                    v-for="item in sellerCategoryStatsList"
                    :key="item.key"
                    class="channel-stat-subcard"
                    :class="[item.key, (item.sellerCompany || '').toLowerCase()]"
                  >
                    <div class="subcard-header">
                      <div class="subcard-title-group">
                        <span class="company-tag" :class="item.sellerCompany === 'TECH' ? 'tech-tag' : 'default-tag'">
                          {{ item.sellerCompanyName }}
                        </span>
                        <span class="category-name">{{ item.categoryName }}</span>
                      </div>
                      <div class="subcard-right-group">
                        <el-tag size="small" effect="plain" class="rate-badge">
                          费率 {{ (item.feeRate * 100).toFixed(0) }}%
                        </el-tag>
                        <el-button
                          type="primary"
                          link
                          size="small"
                          class="channel-settle-btn"
                          title="针对该通道已开额度发起结算"
                          @click.stop="triggerChannelSettlement(item)"
                        >
                          结算
                        </el-button>
                      </div>
                    </div>

                    <div class="subcard-amount-box">
                      <span class="currency">¥</span>
                      <strong class="amount-val">{{ formatMoney(item.completedAmount) }}</strong>
                    </div>

                    <div class="subcard-meta-row">
                      <span class="meta-cell">单数: <b>{{ item.completedCount }} 笔</b></span>
                      <span class="meta-dot">/</span>
                      <span class="meta-cell">占比: <b>{{ Number(item.amountRatio || 0).toFixed(1) }}%</b></span>
                      <span class="meta-dot">/</span>
                      <span class="meta-cell" :title="'扣除额度: ¥' + formatMoney(item.deductedQuota)">
                        扣额: <b>¥{{ formatMoney(item.deductedQuota) }}</b>
                      </span>
                    </div>

                    <div class="subcard-progress-track">
                      <div
                        class="subcard-progress-bar"
                        :style="{ width: `${Math.max(item.completedAmount > 0 ? 2 : 0, Math.min(item.amountRatio || 0, 100))}%` }"
                      ></div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 对账中心主体：左侧月度对账走势图 + 右侧结算流水记录 -->
              <el-row :gutter="20" class="settlement-hub-body">
                <!-- 左侧：月度结算与开票对账走势图 (ECharts) -->
                <el-col :xs="24" :lg="13">
                  <div class="settlement-chart-panel">
                    <div class="panel-sub-header">
                      <span class="sub-title">月度开票 vs 结算结款走势</span>
                      <span class="sub-hint">近 6 个月开票金额与结算结款对照</span>
                    </div>
                    <div v-show="hasSettlementTrends" ref="settlementTrendChartRef" class="chart-container settlement-chart"></div>
                    <div v-show="!hasSettlementTrends" class="chart-empty settlement-chart">
                      <el-empty description="暂无月度结算对账数据" :image-size="60" />
                    </div>
                  </div>
                </el-col>

                <!-- 右侧：精美结算流水明细表（带分页与状态徽标） -->
                <el-col :xs="24" :lg="11">
                  <div class="settlement-table-panel">
                    <div class="panel-sub-header">
                      <span class="sub-title">历史结算操作记录 (共 {{ settlementHistory.length }} 笔)</span>
                      <span class="sub-hint">管理员操作历史与对账附言</span>
                    </div>
                    <el-table
                      :data="paginatedSettlements"
                      v-loading="loadingSettlementHistory"
                      stripe
                      size="small"
                      class="settlement-compact-table"
                    >
                      <el-table-column prop="createdAt" label="结算时间" width="145">
                        <template #default="{ row }">
                          <span class="time-cell">{{ formatDate(row.createdAt) }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column prop="settlementAmount" label="结算金额" width="130">
                        <template #default="{ row }">
                          <span class="amount-badge">¥{{ formatMoney(row.settlementAmount) }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column prop="operatorName" label="操作人" width="90">
                        <template #default="{ row }">
                          <el-tag size="small" type="info" effect="light">{{ row.operatorName || '管理员' }}</el-tag>
                        </template>
                      </el-table-column>
                      <el-table-column prop="remark" label="备注" min-width="110" show-overflow-tooltip>
                        <template #default="{ row }">
                          <span class="remark-cell">{{ row.remark || '-' }}</span>
                        </template>
                      </el-table-column>
                    </el-table>
                    <div class="settlement-pagination" v-if="settlementHistory.length > 5">
                      <el-pagination
                        v-model:current-page="settlementCurrentPage"
                        :page-size="5"
                        small
                        layout="prev, pager, next"
                        :total="settlementHistory.length"
                      />
                    </div>
                    <div v-if="!loadingSettlementHistory && settlementHistory.length === 0" class="table-empty">
                      <el-empty description="暂无结算记录" :image-size="60" />
                    </div>
                  </div>
                </el-col>
              </el-row>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </main>

    <!-- 供应商结算对话框 -->
    <SupplierSettlementDialog
      v-model="showSettlementDialog"
      :unsettled-amount="stats?.unsettledAmount"
      :channels="sellerCategoryStatsList"
      :initial-remark="quickSettlementRemark"
      :initial-amount="quickSettlementAmount"
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
  Refresh,
  Connection,
  Timer,
  Coin
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
const typeViewMode = ref<'donut' | 'rose' | 'rank'>('donut')
const companyMetricMode = ref<'amount' | 'count'>('amount')
const userMetricMode = ref<'count' | 'amount'>('count')

// 供应商结算相关状态
const showSettlementDialog = ref(false)
const quickSettlementRemark = ref('')
const quickSettlementAmount = ref<number | undefined>(undefined)
const settlementHistory = ref<SupplierSettlementResponse[]>([])
const loadingSettlementHistory = ref(false)
const settlementCurrentPage = ref(1)

function openGeneralSettlement() {
  quickSettlementRemark.value = ''
  quickSettlementAmount.value = undefined
  showSettlementDialog.value = true
}

function triggerChannelSettlement(item: any) {
  quickSettlementRemark.value = `[${item.label}] 款项结算`
  quickSettlementAmount.value = item.completedAmount > 0 ? item.completedAmount : undefined
  showSettlementDialog.value = true
}

// 是否为管理员
const isAdmin = computed(() => role.value === 'ADMIN')

// 结算记录分页
const paginatedSettlements = computed(() => {
  const start = (settlementCurrentPage.value - 1) * 5
  return settlementHistory.value.slice(start, start + 5)
})

// DOM 根节点与图表 DOM 引用
const dashboardRootRef = ref<HTMLElement>()
let resizeObserver: ResizeObserver | null = null

const trendChartRef = ref<HTMLElement>()
const typeChartRef = ref<HTMLElement>()
const companyChartRef = ref<HTMLElement>()
const userPieChartRef = ref<HTMLElement>()
const amountRangeChartRef = ref<HTMLElement>()
const hourChartRef = ref<HTMLElement>()
const channelCategoryChartRef = ref<HTMLElement>()
const fulfillmentChartRef = ref<HTMLElement>()
const fundFlowChartRef = ref<HTMLElement>()
const settlementTrendChartRef = ref<HTMLElement>()

// 图表实例列表（严格保持前 6 个实例索引与历史测试一致）
let trendChart: ECharts | null = null
let typeChart: ECharts | null = null
let companyChart: ECharts | null = null
let userPieChart: ECharts | null = null
let amountRangeChart: ECharts | null = null
let hourChart: ECharts | null = null
let channelCategoryChart: ECharts | null = null
let fulfillmentChart: ECharts | null = null
let fundFlowChart: ECharts | null = null
let settlementTrendChart: ECharts | null = null

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

// 供应商结算履约进度率
const settlementProgressRate = computed(() => {
  const total = stats.value?.totalAmount || 0
  const settled = stats.value?.totalSettledAmount || 0
  if (total <= 0) return settled > 0 ? '100.0' : '0.0'
  const rate = Math.min((settled / total) * 100, 100)
  return rate.toFixed(1)
})

// 供应商四大出票通道已开额度细分统计列表（普通公司普票/专票、科技公司普票/专票）
const sellerCategoryStatsList = computed(() => {
  const list = stats.value?.sellerCategoryStats
  if (list && list.length > 0) {
    return list
  }
  return [
    {
      key: 'default_normal',
      label: '普通公司·普票',
      sellerCompany: 'DEFAULT',
      sellerCompanyName: '普通公司',
      invoiceCategory: 'NORMAL',
      categoryName: '普通发票',
      completedAmount: 0,
      completedCount: 0,
      deductedQuota: 0,
      feeRate: 0.03,
      amountRatio: 0
    },
    {
      key: 'default_special',
      label: '普通公司·专票',
      sellerCompany: 'DEFAULT',
      sellerCompanyName: '普通公司',
      invoiceCategory: 'VAT_SPECIAL',
      categoryName: '增值税专用发票',
      completedAmount: 0,
      completedCount: 0,
      deductedQuota: 0,
      feeRate: 0.09,
      amountRatio: 0
    },
    {
      key: 'tech_normal',
      label: '科技公司·普票',
      sellerCompany: 'TECH',
      sellerCompanyName: '科技公司',
      invoiceCategory: 'NORMAL',
      categoryName: '普通发票',
      completedAmount: 0,
      completedCount: 0,
      deductedQuota: 0,
      feeRate: 0.04,
      amountRatio: 0
    },
    {
      key: 'tech_special',
      label: '科技公司·专票',
      sellerCompany: 'TECH',
      sellerCompanyName: '科技公司',
      invoiceCategory: 'VAT_SPECIAL',
      categoryName: '增值税专用发票',
      completedAmount: 0,
      completedCount: 0,
      deductedQuota: 0,
      feeRate: 0.1,
      amountRatio: 0
    }
  ]
})

// API 渠道渗透率
const apiChannelRate = computed(() => {
  const channels = stats.value?.channelStats || []
  if (channels.length === 0) return '0.0'
  const total = channels.reduce((sum, c) => sum + c.count, 0)
  if (total === 0) return '0.0'
  const api = channels.find(c => c.channel === 'API')?.count || 0
  return ((api / total) * 100).toFixed(1)
})

// 平均出票时长展示
const formattedAvgDuration = computed(() => {
  const mins = stats.value?.fulfillmentStats?.avgMinutes || 0
  if (mins <= 0) return '< 1 分钟'
  if (mins < 60) return `${Math.round(mins)} 分钟`
  const hours = (mins / 60).toFixed(1)
  return `${hours} 小时`
})

// 红冲标签样式类
const redFlushPillClass = computed(() => {
  const rate = stats.value?.redFlushStats?.redFlushRate || 0
  if (rate > 5) return 'header-pill-badge warning'
  return 'header-pill-badge'
})

// 类目排行榜格式化列表 (支持极小值清晰显示)
const formattedTypeRankList = computed(() => {
  const rawList = stats.value?.typeStats || []
  if (rawList.length === 0) {
    return [
      {
        name: '技术服务费',
        amount: stats.value?.totalAmount || 0,
        count: stats.value?.completedInvoices || 0,
        percent: '100.0',
        color: THEME_COLORS[0]
      }
    ]
  }

  const isAmount = typeMetricMode.value === 'amount'
  const totalVal = rawList.reduce((acc, cur) => acc + (isAmount ? cur.amount : cur.count), 0)

  return [...rawList]
    .sort((a, b) => (isAmount ? b.amount - a.amount : b.count - a.count))
    .map((item, idx) => {
      const val = isAmount ? item.amount : item.count
      const pct = totalVal > 0 ? ((val / totalVal) * 100).toFixed(2) : '0.00'
      return {
        name: item.invoiceType || '其他',
        amount: item.amount,
        count: item.count,
        percent: pct,
        color: THEME_COLORS[idx % THEME_COLORS.length]
      }
    })
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
const hasChannelOrCategoryStats = computed(() =>
  (stats.value?.channelStats?.length ?? 0) > 0 || (stats.value?.categoryStats?.length ?? 0) > 0 || (stats.value?.totalInvoices ?? 0) > 0
)
const hasFulfillmentStats = computed(() => (stats.value?.fulfillmentStats != null) || (stats.value?.completedInvoices ?? 0) > 0)
const hasFundFlowStats = computed(() => (stats.value?.fundFlowStats?.dailyFlows?.length ?? 0) > 0 || (stats.value?.totalInvoices ?? 0) > 0)
const hasSettlementTrends = computed(() => (stats.value?.settlementTrends?.length ?? 0) > 0 || settlementHistory.value.length > 0)

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
  extraCssText: 'box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08); border-radius: 8px;'
}

// 1. 初始化全景业务走势图 (双轴 / 柱状 / 折线)
function initTrendChart() {
  if (!trendChartRef.value || !stats.value) return
  if (!trendChart) {
    trendChart = init(trendChartRef.value)
  }

  const dateMap = new Map<
    string,
    {
      completedAmount: number
      createdCount: number
      completedCount: number
      pendingCount: number
      cancelledCount: number
    }
  >()

  if (stats.value.dailyTrend && stats.value.dailyTrend.length > 0) {
    stats.value.dailyTrend.forEach(d => {
      dateMap.set(d.date, {
        completedAmount: d.completedAmount,
        createdCount: d.createdCount,
        completedCount: d.completedCount,
        pendingCount: d.pendingCount ?? 0,
        cancelledCount: d.cancelledCount ?? 0
      })
    })
  } else {
    stats.value.userStats?.forEach(user => {
      user.timeline?.forEach(point => {
        const existing = dateMap.get(point.date) || {
          completedAmount: 0,
          createdCount: 0,
          completedCount: 0,
          pendingCount: 0,
          cancelledCount: 0
        }
        existing.completedAmount += point.amount
        existing.completedCount += point.count
        existing.createdCount += point.count
        dateMap.set(point.date, existing)
      })
    })
  }

  const sortedDates = Array.from(dateMap.keys()).sort()
  let continuousDates: string[] = []

  if (timeRange.value === 'all') {
    if (sortedDates.length > 0) {
      continuousDates = generateContinuousDates(sortedDates[0], sortedDates[sortedDates.length - 1])
    } else {
      continuousDates = generateDateSequence(7)
    }
  } else {
    const days = parseInt(timeRange.value)
    continuousDates = generateDateSequence(days)
  }

  const seriesDataMap = new Map<string, { completedAmount: number; createdCount: number; completedCount: number; pendingCount: number; cancelledCount: number }>()
  continuousDates.forEach(d => {
    seriesDataMap.set(d, dateMap.get(d) || { completedAmount: 0, createdCount: 0, completedCount: 0, pendingCount: 0, cancelledCount: 0 })
  })

  const amounts = continuousDates.map(d => seriesDataMap.get(d)!.completedAmount)
  const createdCounts = continuousDates.map(d => seriesDataMap.get(d)!.createdCount)
  const completedCounts = continuousDates.map(d => seriesDataMap.get(d)!.completedCount)

  let series: any[] = []
  let yAxis: any[] = []

  if (trendViewMode.value === 'composite') {
    yAxis = [
      {
        type: 'value',
        name: '开票金额 (¥)',
        position: 'left',
        splitLine: { lineStyle: { color: '#f0f3f2' } },
        axisLabel: {
          formatter: (v: number) => (v >= 10000 ? `${(v / 10000).toFixed(1)}w` : `${v}`)
        }
      },
      {
        type: 'value',
        name: '发票单数 (单)',
        position: 'right',
        splitLine: { show: false },
        axisLabel: { formatter: '{value}' }
      }
    ]
    series = [
      {
        name: '已开金额 (¥)',
        type: 'line',
        yAxisIndex: 0,
        smooth: true,
        showSymbol: continuousDates.length <= 30,
        itemStyle: { color: '#12715b' },
        lineStyle: { width: 3, color: '#12715b' },
        areaStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(18, 113, 91, 0.28)' },
            { offset: 1, color: 'rgba(18, 113, 91, 0.02)' }
          ])
        },
        data: amounts
      },
      {
        name: '申请单数 (单)',
        type: 'bar',
        yAxisIndex: 1,
        itemStyle: { color: 'rgba(214, 154, 45, 0.55)', borderRadius: [4, 4, 0, 0] },
        data: createdCounts
      },
      {
        name: '已开单数 (单)',
        type: 'bar',
        yAxisIndex: 1,
        itemStyle: { color: 'rgba(18, 113, 91, 0.85)', borderRadius: [4, 4, 0, 0] },
        data: completedCounts
      }
    ]
  } else if (trendViewMode.value === 'amount') {
    yAxis = [{
      type: 'value',
      name: '金额 (¥)',
      splitLine: { lineStyle: { color: '#f0f3f2' } }
    }]
    series = [{
      name: '已开票金额',
      type: 'line',
      smooth: true,
      itemStyle: { color: '#12715b' },
      areaStyle: {
        color: new graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(18, 113, 91, 0.35)' },
          { offset: 1, color: 'rgba(18, 113, 91, 0.02)' }
        ])
      },
      data: amounts
    }]
  } else if (trendViewMode.value === 'created') {
    yAxis = [{ type: 'value', name: '申请单数', splitLine: { lineStyle: { color: '#f0f3f2' } } }]
    series = [{
      name: '申请发票单数',
      type: 'bar',
      itemStyle: { color: '#d69a2d', borderRadius: [4, 4, 0, 0] },
      data: createdCounts
    }]
  } else if (trendViewMode.value === 'completed') {
    yAxis = [{ type: 'value', name: '已开单数', splitLine: { lineStyle: { color: '#f0f3f2' } } }]
    series = [{
      name: '已开发票单数',
      type: 'bar',
      itemStyle: { color: '#12715b', borderRadius: [4, 4, 0, 0] },
      data: completedCounts
    }]
  } else if (trendViewMode.value === 'userStacked') {
    yAxis = [{ type: 'value', name: '已开金额 (¥)', splitLine: { lineStyle: { color: '#f0f3f2' } } }]
    const users = stats.value.userStats || []
    series = users.map((u, i) => {
      const uMap = new Map<string, number>()
      u.timeline.forEach(t => uMap.set(t.date, t.amount))
      return {
        name: u.username,
        type: 'line',
        stack: 'total',
        smooth: true,
        itemStyle: { color: THEME_COLORS[i % THEME_COLORS.length] },
        data: continuousDates.map(d => uMap.get(d) || 0)
      }
    })
  }

  const option = {
    tooltip: {
      ...commonTooltip,
      trigger: 'axis',
      axisPointer: { type: 'cross', label: { backgroundColor: '#12715b' } },
      formatter: (params: any) => {
        if (!params || !params.length) return ''
        const dateStr = params[0].axisValue
        const dayStat = dateMap.get(dateStr)
        let html = `<div style="font-weight: 600; margin-bottom: 6px; border-bottom: 1px solid #eef2f1; padding-bottom: 4px;">📅 ${dateStr}</div>`
        params.forEach((p: any) => {
          const val = typeof p.value === 'number' ? (p.seriesName.includes('金额') ? `¥${formatMoney(p.value)}` : `${p.value} 单`) : p.value
          html += `<div style="display: flex; justify-content: space-between; gap: 16px; margin: 3px 0;">
            <span>${p.marker} ${p.seriesName}:</span>
            <b>${val}</b>
          </div>`
        })
        if (dayStat && dayStat.cancelledCount > 0) {
          html += `<div style="color: #74808b; font-size: 11px; margin-top: 4px;">已取消申请: ${dayStat.cancelledCount} 单</div>`
        }
        if (dayStat && dayStat.createdCount > 0 && dayStat.pendingCount === 0) {
          html += `<div style="color: #12715b; font-size: 11px; margin-top: 2px;">✅ 当日发票已全开完${dayStat.cancelledCount > 0 ? `（含 ${dayStat.cancelledCount} 单已取消）` : ''}</div>`
        }
        return html
      }
    },
    legend: {
      top: 0,
      icon: 'circle',
      textStyle: { color: '#4d5863', fontSize: 12 }
    },
    grid: { left: '3%', right: '3%', bottom: '3%', top: '40px', containLabel: true },
    xAxis: {
      type: 'category',
      data: continuousDates,
      axisLine: { lineStyle: { color: '#cfd7d4' } },
      axisLabel: { color: '#74808b' }
    },
    yAxis,
    series
  }

  trendChart.setOption(option, true)
}

// 2. 初始化开票类目/项目分布图 (优化图一：解决小标签重叠与空心问题，支持环形/玫瑰/排行三种视图)
function initTypeChart() {
  if (!typeChartRef.value || !stats.value || typeViewMode.value === 'rank') return
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
    data = [
      {
        name: '技术服务费',
        value: typeMetricMode.value === 'amount' ? (stats.value.totalAmount || 0) : (stats.value.completedInvoices || 0),
        count: stats.value.completedInvoices || 0,
        amount: stats.value.totalAmount || 0
      }
    ]
  }

  const isAmount = typeMetricMode.value === 'amount'
  const totalVal = data.reduce((sum, d) => sum + d.value, 0)
  const centerMainText = isAmount ? `¥${formatMoney(totalVal)}` : `${totalVal} 笔`
  const centerSubText = isAmount ? '类目总金额' : '总开票单数'

  const option = {
    color: THEME_COLORS,
    title: {
      text: centerMainText,
      subtext: centerSubText,
      left: '50%',
      top: '42%',
      textAlign: 'center',
      textStyle: {
        fontSize: totalVal > 1000000 ? 15 : 18,
        fontWeight: 'bold',
        color: '#20252b'
      },
      subtextStyle: {
        fontSize: 11,
        color: '#74808b'
      }
    },
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
      itemHeight: 8,
      formatter: (name: string) => {
        const found = data.find(d => d.name === name)
        if (!found || totalVal <= 0) return name
        const pct = ((found.value / totalVal) * 100).toFixed(1)
        return `${name}  ${pct}%`
      }
    },
    series: [
      {
        name: '开票类目',
        type: 'pie',
        radius: typeViewMode.value === 'rose' ? ['26%', '70%'] : ['44%', '70%'],
        center: ['50%', '45%'],
        roseType: typeViewMode.value === 'rose' ? 'radius' : undefined,
        avoidLabelOverlap: true,
        minAngle: 6, // 关键修复：防止 0.25% 极端微小扇区坍缩为 0px 导致标签碰撞
        itemStyle: {
          borderRadius: 6,
          borderColor: '#fff',
          borderWidth: 2
        },
        labelLine: {
          length: 12,
          length2: 16,
          maxSurfaceAngle: 80,
          smooth: true
        },
        labelLayout: {
          hideOverlap: true,
          moveOverlap: 'shiftY'
        },
        label: {
          show: true,
          formatter: (params: any) => {
            // 对微小扇区优化显示，避免遮挡
            if ((params.percent || 0) < 1) {
              return `${params.name}\n${params.percent}%`
            }
            return `${params.name}: ${params.percent}%`
          },
          color: '#4d5863',
          fontSize: 11
        },
        emphasis: {
          label: { show: true, fontWeight: 'bold' },
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.18)'
          }
        },
        data
      }
    ]
  }

  typeChart.setOption(option, true)

  // 鼠标悬停扇区联动中心文案
  if (typeof (typeChart as any).off === 'function') {
    typeChart.off('mouseover')
    typeChart.off('mouseout')
  }
  if (typeof (typeChart as any).on === 'function') {
    typeChart.on('mouseover', (params: any) => {
      if (params.seriesType === 'pie' && typeViewMode.value === 'donut') {
        const d = params.data || {}
        typeChart?.setOption({
          title: {
            text: isAmount ? `¥${formatMoney(d.amount)}` : `${d.count || 0} 笔`,
            subtext: `${d.name || '其他'} (${params.percent || 0}%)`
          }
        })
      }
    })
    typeChart.on('mouseout', () => {
      if (typeViewMode.value === 'donut') {
        typeChart?.setOption({
          title: {
            text: centerMainText,
            subtext: centerSubText
          }
        })
      }
    })
  }
}

// 3. 初始化企业抬头开票排行 TOP 10 (条形图)
function initCompanyChart() {
  if (!companyChartRef.value || !stats.value) return
  if (!companyChart) {
    companyChart = init(companyChartRef.value)
  }

  let companies: Array<{ name: string; amount: number; count: number }> = []

  if (stats.value.companyTopStats && stats.value.companyTopStats.length > 0) {
    const sorted = [...stats.value.companyTopStats].sort((a, b) =>
      companyMetricMode.value === 'amount'
        ? b.amount - a.amount
        : b.count - a.count
    )
    companies = sorted.slice(0, 10).map(c => ({
      name: c.companyName || '未知企业',
      amount: c.amount || 0,
      count: c.count || 0
    }))
  }

  const reversed = [...companies].reverse()
  const names = reversed.map(c => c.name)
  const values = reversed.map(c => (companyMetricMode.value === 'amount' ? c.amount : c.count))

  const option = {
    tooltip: {
      ...commonTooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        if (!params || !params.length) return ''
        const p = params[0]
        const comp = reversed[p.dataIndex]
        return `
          <div style="font-weight: 600; margin-bottom: 4px;">🏢 ${comp.name}</div>
          <div>累计开票金额: <b>¥${formatMoney(comp.amount)}</b></div>
          <div>开票申请笔数: <b>${comp.count} 笔</b></div>
        `
      }
    },
    grid: { left: '3%', right: '8%', bottom: '3%', top: '20px', containLabel: true },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f0f3f2' } },
      axisLabel: {
        color: '#74808b',
        formatter: (v: number) => {
          if (companyMetricMode.value === 'amount') {
            return v >= 10000 ? `${(v / 10000).toFixed(0)}w` : `${v}`
          }
          return `${v}`
        }
      }
    },
    yAxis: {
      type: 'category',
      data: names,
      axisLine: { lineStyle: { color: '#cfd7d4' } },
      axisLabel: {
        color: '#4d5863',
        formatter: (val: string) => (val.length > 8 ? `${val.substring(0, 8)}...` : val)
      }
    },
    series: [
      {
        name: companyMetricMode.value === 'amount' ? '累计金额 (¥)' : '申请笔数',
        type: 'bar',
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: new graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#12715b' },
            { offset: 1, color: '#2a9d8f' }
          ])
        },
        label: {
          show: true,
          position: 'right',
          color: '#74808b',
          fontSize: 11,
          formatter: (p: any) => {
            return companyMetricMode.value === 'amount' ? `¥${formatMoney(p.value)}` : `${p.value} 笔`
          }
        },
        data: values
      }
    ]
  }

  companyChart.setOption(option, true)
}

// 4. 初始化用户发票分布与贡献占比 (圆环图)
function initUserPieChart() {
  if (!userPieChartRef.value || !stats.value) return
  if (!userPieChart) {
    userPieChart = init(userPieChartRef.value)
  }

  const rawUsers = stats.value.userStats || []
  const formattedData = rawUsers.map(user => ({
    name: user.username,
    value: userMetricMode.value === 'count' ? user.completedCount : user.totalAmount,
    count: user.completedCount,
    amount: user.totalAmount
  }))

  const sortedData = [...formattedData].sort((a, b) => b.value - a.value)
  let displayData = sortedData
  if (sortedData.length > 8) {
    const top7 = sortedData.slice(0, 7)
    const others = sortedData.slice(7)
    const othersValue = others.reduce((sum, item) => sum + item.value, 0)
    const othersCount = others.reduce((sum, item) => sum + item.count, 0)
    const othersAmount = others.reduce((sum, item) => sum + item.amount, 0)
    displayData = [
      ...top7,
      {
        name: '其他用户',
        value: othersValue,
        count: othersCount,
        amount: othersAmount
      }
    ]
  }

  const isCount = userMetricMode.value === 'count'

  const option = {
    color: THEME_COLORS,
    tooltip: {
      ...commonTooltip,
      trigger: 'item',
      formatter: (params: any) => {
        const d = params?.data || {}
        return `
          <div style="font-weight: 600; margin-bottom: 4px;">👤 ${d.name}</div>
          <div>开票金额: <b>¥${formatMoney(d.amount)}</b></div>
          <div>完成笔数: <b>${d.count} 笔</b></div>
          <div>贡献占比: <b>${params.percent || 0}%</b></div>
        `
      }
    },
    legend: {
      type: sortedData.length > 8 ? 'scroll' : 'plain',
      orient: 'horizontal',
      bottom: 0,
      icon: 'circle',
      textStyle: { color: '#4d5863', fontSize: 11 }
    },
    series: [
      {
        name: isCount ? '发票数贡献' : '金额贡献',
        type: 'pie',
        radius: ['40%', '68%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: {
          show: true,
          formatter: '{b}: {d}%',
          color: '#4d5863',
          fontSize: 11
        },
        data: displayData
      }
    ]
  }

  userPieChart.setOption(option, true)
}

// 5. 初始化发票金额区间画像 (柱状图)
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

  const option = {
    tooltip: {
      ...commonTooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        if (!params || !params.length) return ''
        const idx = params[0].dataIndex
        const r = ranges[idx]
        return `
          <div style="font-weight: 600; margin-bottom: 4px;">🎯 ${r.rangeLabel}</div>
          <div>发票单数: <b>${r.count} 笔</b></div>
          <div>累计金额: <b>¥${formatMoney(r.amount)}</b></div>
        `
      }
    },
    grid: { left: '3%', right: '3%', bottom: '3%', top: '20px', containLabel: true },
    xAxis: {
      type: 'category',
      data: labels,
      axisLine: { lineStyle: { color: '#cfd7d4' } },
      axisLabel: { color: '#4d5863', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      name: '笔数',
      splitLine: { lineStyle: { color: '#f0f3f2' } },
      axisLabel: { color: '#74808b' }
    },
    series: [
      {
        name: '发票笔数',
        type: 'bar',
        barWidth: '40%',
        itemStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#d69a2d' },
            { offset: 1, color: '#f4a261' }
          ]),
          borderRadius: [6, 6, 0, 0]
        },
        label: {
          show: true,
          position: 'top',
          color: '#4d5863',
          fontSize: 11,
          formatter: '{c} 笔'
        },
        data: counts
      }
    ]
  }

  amountRangeChart.setOption(option, true)
}

// 6. 初始化 24 小时开票申请热度峰值 (柱状图)
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
      axisPointer: { type: 'line' },
      formatter: (params: any) => {
        if (!params || !params.length) return ''
        const p = params[0]
        return `
          <div style="font-weight: 600; margin-bottom: 4px;">⏰ 时段: ${p.axisValue} - ${p.axisValue.replace(':00', ':59')}</div>
          <div>申请发票单数: <b>${p.value} 笔</b></div>
        `
      }
    },
    grid: { left: '3%', right: '3%', bottom: '3%', top: '20px', containLabel: true },
    xAxis: {
      type: 'category',
      data: hours,
      axisLine: { lineStyle: { color: '#cfd7d4' } },
      axisLabel: { color: '#74808b', interval: 2, fontSize: 10 }
    },
    yAxis: {
      type: 'value',
      name: '申请单数',
      splitLine: { lineStyle: { color: '#f0f3f2' } },
      axisLabel: { color: '#74808b' }
    },
    series: [
      {
        name: '申请热度',
        type: 'line',
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 3, color: '#12715b' },
        areaStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(18, 113, 91, 0.35)' },
            { offset: 1, color: 'rgba(18, 113, 91, 0.02)' }
          ])
        },
        data: counts
      }
    ]
  }

  hourChart.setOption(option, true)
}

// 7. 初始化提交渠道与发票票种画像图 (NEW: 双环模型：内环渠道API/网页，外环票种普票/专票)
function initChannelCategoryChart() {
  if (!channelCategoryChartRef.value || !stats.value) return
  if (!channelCategoryChart) {
    channelCategoryChart = init(channelCategoryChartRef.value)
  }

  const channelMap = {
    API: 'API 接口调用',
    MANUAL: '网页手动申请',
    UNKNOWN: '历史未标记'
  }

  const categoryMap = {
    NORMAL: '增值税普通发票',
    VAT_SPECIAL: '增值税专用发票'
  }

  const channelList = (stats.value.channelStats && stats.value.channelStats.length > 0)
    ? stats.value.channelStats.map(c => ({
        name: channelMap[c.channel as keyof typeof channelMap] || c.channel,
        value: c.count,
        amount: c.amount
      }))
    : [
        { name: 'API 接口调用', value: stats.value.completedInvoices || 0, amount: stats.value.totalAmount || 0 }
      ]

  const categoryList = (stats.value.categoryStats && stats.value.categoryStats.length > 0)
    ? stats.value.categoryStats.map(c => ({
        name: categoryMap[c.category as keyof typeof categoryMap] || c.category,
        value: c.count,
        amount: c.amount
      }))
    : [
        { name: '增值税普通发票', value: stats.value.completedInvoices || 0, amount: stats.value.totalAmount || 0 }
      ]

  const option = {
    tooltip: {
      ...commonTooltip,
      trigger: 'item',
      formatter: (params: any) => {
        const d = params?.data || {}
        return `
          <div style="font-weight: 600; margin-bottom: 4px;">${params.seriesName}: ${d.name}</div>
          <div>发票单数: <b>${d.value || 0} 单</b></div>
          <div>涉及金额: <b>¥${formatMoney(d.amount)}</b></div>
          <div>层级占比: <b>${params.percent || 0}%</b></div>
        `
      }
    },
    legend: {
      orient: 'horizontal',
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: '#4d5863', fontSize: 11 }
    },
    series: [
      {
        name: '提交渠道',
        type: 'pie',
        radius: ['20%', '42%'],
        center: ['50%', '45%'],
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: {
          position: 'inner',
          fontSize: 10,
          color: '#fff',
          formatter: '{b}\n{d}%'
        },
        data: channelList.map((item, idx) => ({
          ...item,
          itemStyle: { color: idx === 0 ? '#12715b' : '#d69a2d' }
        }))
      },
      {
        name: '发票票种',
        type: 'pie',
        radius: ['52%', '72%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: {
          formatter: '{b}: {d}%',
          color: '#4d5863',
          fontSize: 11
        },
        data: categoryList.map((item, idx) => ({
          ...item,
          itemStyle: { color: idx === 0 ? '#2a9d8f' : '#457b9d' }
        }))
      }
    ]
  }

  channelCategoryChart.setOption(option, true)
}

// 8. 初始化开票履约服务时效分析 (NEW: SLA 阶梯分布柱状图 + 累计达成率折线)
function initFulfillmentChart() {
  if (!fulfillmentChartRef.value || !stats.value) return
  if (!fulfillmentChart) {
    fulfillmentChart = init(fulfillmentChartRef.value)
  }

  const f = stats.value.fulfillmentStats || {
    under30Min: stats.value.completedInvoices || 0,
    under2Hour: 0,
    under6Hour: 0,
    under24Hour: 0,
    over24Hour: 0,
    avgMinutes: 15.0,
    fastCompletionRate: 100.0
  }

  const buckets = [
    { label: '< 30分钟', count: f.under30Min },
    { label: '30分-2小时', count: f.under2Hour },
    { label: '2-6小时', count: f.under6Hour },
    { label: '6-24小时', count: f.under24Hour },
    { label: '> 24小时', count: f.over24Hour }
  ]

  const total = buckets.reduce((sum, b) => sum + b.count, 0)
  let cumulative = 0
  const rates = buckets.map(b => {
    cumulative += b.count
    return total > 0 ? Number(((cumulative / total) * 100).toFixed(1)) : 0
  })

  const option = {
    tooltip: {
      ...commonTooltip,
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params: any) => {
        if (!params || !params.length) return ''
        const idx = params[0].dataIndex
        const b = buckets[idx]
        const pct = total > 0 ? ((b.count / total) * 100).toFixed(1) : '0.0'
        return `
          <div style="font-weight: 600; margin-bottom: 4px;">⏱️ 时效区间: ${b.label}</div>
          <div>本区间出票: <b>${b.count} 单</b> (${pct}%)</div>
          <div>累计 SLA 达成率: <b>${rates[idx]}%</b></div>
        `
      }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '35px', containLabel: true },
    xAxis: {
      type: 'category',
      data: buckets.map(b => b.label),
      axisLine: { lineStyle: { color: '#cfd7d4' } },
      axisLabel: { color: '#4d5863', fontSize: 11 }
    },
    yAxis: [
      {
        type: 'value',
        name: '开票单数',
        splitLine: { lineStyle: { color: '#f0f3f2' } },
        axisLabel: { color: '#74808b' }
      },
      {
        type: 'value',
        name: '累计达成率',
        min: 0,
        max: 100,
        splitLine: { show: false },
        axisLabel: { formatter: '{value}%', color: '#74808b' }
      }
    ],
    series: [
      {
        name: '开票出票单数',
        type: 'bar',
        barWidth: '38%',
        yAxisIndex: 0,
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#12715b' },
            { offset: 1, color: '#2a9d8f' }
          ])
        },
        data: buckets.map(b => b.count)
      },
      {
        name: '累计 SLA 履约率',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        itemStyle: { color: '#d69a2d' },
        lineStyle: { width: 3, color: '#d69a2d' },
        data: rates
      }
    ]
  }

  fulfillmentChart.setOption(option, true)
}

// 9. 初始化平台资金与额度流动走势图 (NEW: 充值入账 vs 发票扣减双向收支柱状图)
function initFundFlowChart() {
  if (!fundFlowChartRef.value || !stats.value) return
  if (!fundFlowChart) {
    fundFlowChart = init(fundFlowChartRef.value)
  }

  let flows = stats.value.fundFlowStats?.dailyFlows || []
  if (flows.length === 0) {
    const dates = generateDateSequence(14)
    flows = dates.map(d => ({
      date: d,
      rechargeAmount: 0,
      deductAmount: 0,
      netAmount: 0
    }))
  }

  const dates = flows.map(f => f.date)
  const recharges = flows.map(f => f.rechargeAmount)
  const deducts = flows.map(f => f.deductAmount)
  const nets = flows.map(f => f.netAmount)

  const option = {
    tooltip: {
      ...commonTooltip,
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params: any) => {
        if (!params || !params.length) return ''
        const dateStr = params[0].axisValue
        const item = flows.find(f => f.date === dateStr)
        return `
          <div style="font-weight: 600; margin-bottom: 4px;">📅 日期: ${dateStr}</div>
          <div style="color: #12715b;">充值入账: <b>+¥${formatMoney(item?.rechargeAmount || 0)}</b></div>
          <div style="color: #e76f51;">开票扣减: <b>-¥${formatMoney(item?.deductAmount || 0)}</b></div>
          <div style="color: #20252b; margin-top: 2px; border-top: 1px dashed #eee; padding-top: 2px;">
            净资金流动: <b>¥${formatMoney(item?.netAmount || 0)}</b>
          </div>
        `
      }
    },
    legend: {
      top: 0,
      icon: 'circle',
      textStyle: { color: '#4d5863', fontSize: 11 }
    },
    grid: { left: '3%', right: '3%', bottom: '3%', top: '35px', containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#cfd7d4' } },
      axisLabel: { color: '#74808b', fontSize: 10 }
    },
    yAxis: {
      type: 'value',
      name: '金额 (¥)',
      splitLine: { lineStyle: { color: '#f0f3f2' } },
      axisLabel: {
        color: '#74808b',
        formatter: (v: number) => (v >= 10000 ? `${(v / 10000).toFixed(1)}w` : `${v}`)
      }
    },
    series: [
      {
        name: '充值入账 (流入)',
        type: 'bar',
        stack: 'flow',
        itemStyle: { color: '#12715b', borderRadius: [4, 4, 0, 0] },
        data: recharges
      },
      {
        name: '开票抵扣 (消耗)',
        type: 'bar',
        stack: 'flow',
        itemStyle: { color: '#e76f51', borderRadius: [4, 4, 0, 0] },
        data: deducts
      },
      {
        name: '净流动结余',
        type: 'line',
        smooth: true,
        itemStyle: { color: '#3a86ff' },
        lineStyle: { width: 2.5, color: '#3a86ff' },
        data: nets
      }
    ]
  }

  fundFlowChart.setOption(option, true)
}

// 10. 初始化供应商开票与月度结算对账走势图 (NEW: 优化图二：柱状折线对比)
function initSettlementTrendChart() {
  if (!settlementTrendChartRef.value || !stats.value || !isAdmin.value) return
  if (!settlementTrendChart) {
    settlementTrendChart = init(settlementTrendChartRef.value)
  }

  let trends = stats.value.settlementTrends || []
  if (trends.length === 0) {
    const curYear = new Date().getFullYear()
    const curMonth = new Date().getMonth() + 1
    trends = Array.from({ length: 6 }, (_, i) => {
      const m = curMonth - (5 - i)
      const monthStr = `${curYear}-${String(m > 0 ? m : m + 12).padStart(2, '0')}`
      return {
        month: monthStr,
        invoicedAmount: 0,
        settledAmount: 0,
        unsettledBalance: 0
      }
    })
  }

  const months = trends.map(t => t.month)
  const invoiced = trends.map(t => t.invoicedAmount)
  const settled = trends.map(t => t.settledAmount)
  const balances = trends.map(t => t.unsettledBalance)

  const option = {
    tooltip: {
      ...commonTooltip,
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params: any) => {
        if (!params || !params.length) return ''
        const m = params[0].axisValue
        const t = trends.find(item => item.month === m)
        return `
          <div style="font-weight: 600; margin-bottom: 4px;">📆 月份: ${m}</div>
          <div style="color: #12715b;">已开票总额: <b>¥${formatMoney(t?.invoicedAmount || 0)}</b></div>
          <div style="color: #2a9d8f;">供应商结算: <b>¥${formatMoney(t?.settledAmount || 0)}</b></div>
          <div style="color: #d69a2d; margin-top: 2px; border-top: 1px dashed #eee; padding-top: 2px;">
            待结对账结余: <b>¥${formatMoney(t?.unsettledBalance || 0)}</b>
          </div>
        `
      }
    },
    legend: {
      top: 0,
      icon: 'circle',
      textStyle: { color: '#4d5863', fontSize: 11 }
    },
    grid: { left: '3%', right: '3%', bottom: '3%', top: '35px', containLabel: true },
    xAxis: {
      type: 'category',
      data: months,
      axisLine: { lineStyle: { color: '#cfd7d4' } },
      axisLabel: { color: '#74808b', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      name: '金额 (¥)',
      splitLine: { lineStyle: { color: '#f0f3f2' } },
      axisLabel: {
        color: '#74808b',
        formatter: (v: number) => (Math.abs(v) >= 10000 ? `${(v / 10000).toFixed(0)}w` : `${v}`)
      }
    },
    series: [
      {
        name: '当月开票额 (¥)',
        type: 'bar',
        itemStyle: { color: '#12715b', borderRadius: [4, 4, 0, 0] },
        data: invoiced
      },
      {
        name: '当月结算额 (¥)',
        type: 'bar',
        itemStyle: { color: '#2a9d8f', borderRadius: [4, 4, 0, 0] },
        data: settled
      },
      {
        name: '累计待结款项 (¥)',
        type: 'line',
        smooth: true,
        itemStyle: { color: '#d69a2d' },
        lineStyle: { width: 3, color: '#d69a2d' },
        data: balances
      }
    ]
  }

  settlementTrendChart.setOption(option, true)
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
    initChannelCategoryChart()
    initFulfillmentChart()
    initFundFlowChart()
    if (isAdmin.value) {
      initSettlementTrendChart()
    }
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

// 处理结算成功
async function handleSettlementSuccess() {
  await Promise.all([loadData(), loadSettlementHistory()])
}

// 格式化日期
function formatDate(dateString: string) {
  if (!dateString) return '-'
  try {
    const date = new Date(dateString)
    if (isNaN(date.getTime())) return dateString
    const y = date.getFullYear()
    const m = String(date.getMonth() + 1).padStart(2, '0')
    const d = String(date.getDate()).padStart(2, '0')
    const hh = String(date.getHours()).padStart(2, '0')
    const mm = String(date.getMinutes()).padStart(2, '0')
    return `${y}/${m}/${d} ${hh}:${mm}`
  } catch {
    return dateString
  }
}

// 监听各个维度的视图与指标切换
watch(timeRange, () => initTrendChart())
watch(trendViewMode, () => initTrendChart())
watch(typeMetricMode, () => {
  if (typeViewMode.value !== 'rank') initTypeChart()
})
watch(typeViewMode, (mode) => {
  if (mode !== 'rank') {
    nextTick(() => {
      initTypeChart()
      if (typeChart && !typeChart.isDisposed()) typeChart.resize()
    })
  }
})
watch(companyMetricMode, () => initCompanyChart())
watch(userMetricMode, () => initUserPieChart())

// 监听管理员状态变化，动态加载对账走势与流水
watch(isAdmin, (val) => {
  if (val) {
    loadSettlementHistory()
    nextTick(() => initSettlementTrendChart())
  }
})

// 监听结算历史更新，自适应保护分页
watch(settlementHistory, (newVal) => {
  const maxPage = Math.max(1, Math.ceil(newVal.length / 5))
  if (settlementCurrentPage.value > maxPage) {
    settlementCurrentPage.value = maxPage
  }
})

// 窗口尺寸自适应
function handleResize() {
  if (trendChart && !trendChart.isDisposed()) trendChart.resize()
  if (typeChart && !typeChart.isDisposed()) typeChart.resize()
  if (companyChart && !companyChart.isDisposed()) companyChart.resize()
  if (userPieChart && !userPieChart.isDisposed()) userPieChart.resize()
  if (amountRangeChart && !amountRangeChart.isDisposed()) amountRangeChart.resize()
  if (hourChart && !hourChart.isDisposed()) hourChart.resize()
  if (channelCategoryChart && !channelCategoryChart.isDisposed()) channelCategoryChart.resize()
  if (fulfillmentChart && !fulfillmentChart.isDisposed()) fulfillmentChart.resize()
  if (fundFlowChart && !fundFlowChart.isDisposed()) fundFlowChart.resize()
  if (settlementTrendChart && !settlementTrendChart.isDisposed()) settlementTrendChart.resize()
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
  if (channelCategoryChart && !channelCategoryChart.isDisposed()) channelCategoryChart.dispose()
  if (fulfillmentChart && !fulfillmentChart.isDisposed()) fulfillmentChart.dispose()
  if (fundFlowChart && !fundFlowChart.isDisposed()) fundFlowChart.dispose()
  if (settlementTrendChart && !settlementTrendChart.isDisposed()) settlementTrendChart.dispose()
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
  50% { transform: scale(1.3); opacity: 0.6; }
  100% { transform: scale(0.9); opacity: 1; }
}

.toolbar-hint {
  font-size: 13px;
  color: #74808b;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.quick-action-btn {
  font-weight: 500;
}

/* KPI 指标卡片群 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  padding: 16px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid var(--color-border, #e2e7e5);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.02);
  transition: all 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 14px rgba(0, 0, 0, 0.06);
}

.stat-card-featured {
  border-color: #d1e7de;
}

.stat-card-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stat-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.stat-icon.primary { background: #e9f4f0; color: #12715b; }
.stat-icon.warning { background: #fff9ed; color: #d69a2d; }
.stat-icon.success { background: #edf8f3; color: #12715b; }
.stat-icon.accent { background: #fdf6ec; color: #e6a23c; }
.stat-icon.cyan { background: #e6f7ff; color: #1890ff; }

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
  font-size: 24px;
  font-weight: 700;
  color: #20252b;
  letter-spacing: -0.5px;
}

.stat-value.warning-text { color: #d69a2d; }
.stat-value.success-text { color: #12715b; }

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

.header-pill-badge {
  font-size: 12px;
  background: #f8faf9;
  color: #4d5863;
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid #eef2f1;
}

.header-pill-badge.primary {
  background: #e9f4f0;
  color: #12715b;
  border-color: #d1e7de;
}

.header-pill-badge.success {
  background: #edf8f3;
  color: #12715b;
  border-color: #bfe7d5;
}

.header-pill-badge.warning {
  background: #fff9ed;
  color: #b66d0b;
  border-color: #f7e1b5;
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

/* 类目细分排行列表 (优化图一) */
.category-rank-container {
  height: 360px;
  display: flex;
  flex-direction: column;
}

.rank-list-header {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 12px;
  color: #74808b;
  border-bottom: 1px solid #edf1ef;
}

.category-rank-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.category-rank-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.rank-left {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 130px;
  flex-shrink: 0;
}

.rank-badge {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  background: #f0f3f2;
  color: #74808b;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.rank-badge.rank-1 { background: #e76f51; color: #fff; }
.rank-badge.rank-2 { background: #d69a2d; color: #fff; }
.rank-badge.rank-3 { background: #2a9d8f; color: #fff; }

.cat-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.cat-name {
  font-size: 13px;
  color: #20252b;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rank-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rank-values {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 12px;
}

.rank-amount {
  color: #20252b;
  font-size: 12px;
}

.rank-count {
  color: #74808b;
  font-size: 11px;
}

.rank-percent-pill {
  font-size: 11px;
  font-weight: 600;
  color: #12715b;
  background: #e9f4f0;
  padding: 1px 6px;
  border-radius: 4px;
}

.cat-progress-bg {
  width: 100%;
  height: 6px;
  background: #f0f3f2;
  border-radius: 3px;
  overflow: hidden;
}

.cat-progress-bar {
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease;
}

/* 供应商结算与对账中心 (优化图二) */
.settlement-hub-card {
  overflow: hidden;
}

.settlement-kpi-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fbfdfc;
  border: 1px solid #edf1ef;
  border-radius: 8px;
  padding: 14px 20px;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 16px;
}

.settlement-kpi-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.settlement-kpi-divider {
  width: 1px;
  height: 32px;
  background: #e2e7e5;
}

.kpi-sub-label {
  font-size: 12px;
  color: #74808b;
}

.kpi-sub-val {
  font-size: 18px;
  font-weight: 700;
  color: #20252b;
  display: flex;
  align-items: center;
  gap: 8px;
}

.kpi-sub-val.success { color: #12715b; }
.kpi-sub-val.warning { color: #d69a2d; }
.kpi-sub-val.accent { color: #2a9d8f; }

.settlement-kpi-item.progress-item {
  min-width: 160px;
  flex: 1;
  max-width: 240px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.progress-info .kpi-sub-val {
  font-size: 14px;
}

/* 供应商四大出票通道已开额度明细矩阵 */
.settlement-channel-matrix {
  margin-bottom: 20px;
  background: #fbfdfc;
  border: 1px solid #edf1ef;
  border-radius: 8px;
  padding: 14px 18px;
}

.matrix-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
  flex-wrap: wrap;
  gap: 8px;
}

.matrix-title {
  font-size: 13px;
  font-weight: 700;
  color: #20252b;
}

.matrix-hint {
  font-size: 12px;
  color: #74808b;
}

.channel-card-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.channel-stat-subcard {
  background: #ffffff;
  border: 1px solid #e5eae8;
  border-radius: 8px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  position: relative;
  transition: all 0.25s ease;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

.channel-stat-subcard:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(18, 113, 91, 0.08);
  border-color: #b9ddd3;
}

.subcard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.subcard-title-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.company-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
}

.company-tag.default-tag {
  background: #f0f7f5;
  color: #12715b;
  border: 1px solid #d2eae2;
}

.company-tag.tech-tag {
  background: #eff6ff;
  color: #2563eb;
  border: 1px solid #dbeafe;
}

.category-name {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.rate-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 0 6px;
  height: 20px;
  line-height: 18px;
}

.subcard-right-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.channel-settle-btn {
  font-size: 11px;
  padding: 0 2px;
  height: 20px;
  color: #12715b;
  font-weight: 500;
}

.channel-settle-btn:hover {
  color: #0c4d3e;
  text-decoration: underline;
}

.subcard-amount-box {
  display: flex;
  align-items: baseline;
  gap: 2px;
  margin-bottom: 8px;
}

.subcard-amount-box .currency {
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
}

.subcard-amount-box .amount-val {
  font-size: 19px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.3px;
  white-space: nowrap;
}

.subcard-meta-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 11px;
  color: #64748b;
  margin-bottom: 8px;
}

.subcard-meta-row .meta-cell b {
  color: #1e293b;
  font-weight: 600;
}

.meta-dot {
  color: #cbd5e1;
}

.subcard-progress-track {
  height: 4px;
  background: #f1f5f4;
  border-radius: 2px;
  overflow: hidden;
}

.subcard-progress-bar {
  height: 100%;
  border-radius: 2px;
  transition: width 0.4s ease;
}

/* 4个不同维度的色彩区分 */
.channel-stat-subcard.default_normal .subcard-progress-bar {
  background: #12715b;
}
.channel-stat-subcard.default_special .subcard-progress-bar {
  background: #0284c7;
}
.channel-stat-subcard.tech_normal .subcard-progress-bar {
  background: #6366f1;
}
.channel-stat-subcard.tech_special .subcard-progress-bar {
  background: #e76f51;
}

.settlement-hub-body {
  margin-top: 4px;
}

.settlement-chart-panel,
.settlement-table-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.panel-sub-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}

.sub-title {
  font-size: 14px;
  font-weight: 600;
  color: #20252b;
}

.sub-hint {
  font-size: 12px;
  color: #74808b;
}

.settlement-chart {
  height: 330px;
}

.settlement-compact-table {
  border: 1px solid #edf1ef;
  border-radius: 8px;
}

.time-cell {
  font-size: 12px;
  color: #4d5863;
}

.amount-badge {
  font-weight: 700;
  color: #12715b;
  font-size: 13px;
}

.remark-cell {
  font-size: 12px;
  color: #74808b;
}

.settlement-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
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

  .settlement-kpi-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .settlement-kpi-divider {
    display: none;
  }

  .settlement-kpi-item.progress-item {
    width: 100%;
    max-width: none;
  }

  .channel-card-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .chart-container,
  .chart-empty,
  .settlement-chart {
    height: 270px;
  }
}

@media (max-width: 540px) {
  .channel-card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
