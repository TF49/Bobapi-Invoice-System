<template>
  <div class="workspace-shell">
    <AppHeader title="充值申请审核" />

    <main class="workspace-content">
      <!-- 顶部数据概览卡片 -->
      <AnimatedContent tag="section" class="stats-grid recharge-stats-grid" :distance="10">
        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon neutral"><Tickets /></span>
            <div class="stat-copy">
              <span class="stat-label">申请总数</span>
              <strong class="stat-value"><CountUp :value="stats.total" /></strong>
            </div>
            <p class="stat-note">全部充值记录</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card" :class="{ 'stat-card--alert': stats.pendingCount > 0 }">
          <div class="stat-card-content">
            <span class="stat-icon" :class="stats.pendingCount > 0 ? 'warning' : 'neutral'"><Clock /></span>
            <div class="stat-copy">
              <span class="stat-label">待审核笔数</span>
              <strong class="stat-value" :class="{ 'warning-stat': stats.pendingCount > 0 }">
                <CountUp :value="stats.pendingCount" />
              </strong>
            </div>
            <p class="stat-note">{{ stats.pendingCount > 0 ? '需人工审核确认' : '暂无待审核申请' }}</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon" :class="stats.pendingAmount > 0 ? 'warning' : 'neutral'"><Wallet /></span>
            <div class="stat-copy">
              <span class="stat-label">待审充值额度</span>
              <strong class="stat-value amount-stat" :class="{ 'warning-stat': stats.pendingAmount > 0 }">
                <CountUp :value="stats.pendingAmount" :decimals="2" prefix="¥" />
              </strong>
            </div>
            <p class="stat-note">待审核的额度总额</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon"><CircleCheck /></span>
            <div class="stat-copy">
              <span class="stat-label">累计入账额度</span>
              <strong class="stat-value amount-stat approved-stat">
                <CountUp :value="stats.approvedAmount" :decimals="2" prefix="¥" />
              </strong>
            </div>
            <p class="stat-note">审核已通过并到账</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon neutral"><Coin /></span>
            <div class="stat-copy">
              <span class="stat-label">累计实收手续费</span>
              <strong class="stat-value amount-stat">
                <CountUp :value="stats.totalFee" :decimals="2" prefix="¥" />
              </strong>
            </div>
            <p class="stat-note">已审核通过的手续费</p>
          </div>
        </SpotlightCard>
      </AnimatedContent>

      <!-- 充值申请表格面板 -->
      <AnimatedContent tag="section" class="surface-panel requests-panel" :distance="10" :delay="60">
        <div class="panel-header">
          <div class="panel-heading">
            <span class="panel-heading-icon"><Wallet /></span>
            <div>
              <h2>充值申请审核</h2>
              <p>核验用户转账付款凭证，确认后系统自动为用户增加开票额度或盖章钱包</p>
            </div>
          </div>

          <div class="table-tools">
            <span class="result-count"><i></i>共 {{ filteredRequests.length }} 条</span>

            <el-input
              v-model="searchKeyword"
              placeholder="搜索申请人、备注或单号"
              :prefix-icon="Search"
              clearable
              class="search-input"
            />

            <div class="filter-control">
              <span class="filter-label"><Tickets /> 充值业务</span>
              <el-select
                v-model="filterType"
                placeholder="全部业务"
                class="business-select"
              >
                <el-option label="全部业务" value="ALL" />
                <el-option label="发票额度" value="INVOICE" />
                <el-option label="盖章钱包" value="SEAL" />
              </el-select>
            </div>

            <el-tooltip content="刷新数据" placement="top">
              <el-button
                class="refresh-button"
                :icon="Refresh"
                circle
                :loading="loading"
                @click="loadRequests"
              />
            </el-tooltip>
          </div>
        </div>

        <!-- 快捷状态分段切换 Tab -->
        <div class="status-tab-bar">
          <button
            type="button"
            class="status-tab-btn"
            :class="{ 'is-active': filterStatus === 'ALL' }"
            @click="filterStatus = 'ALL'"
          >
            全部申请
            <span class="tab-badge">{{ allRequests.length }}</span>
          </button>
          <button
            type="button"
            class="status-tab-btn"
            :class="{ 'is-active': filterStatus === 'PENDING' }"
            @click="filterStatus = 'PENDING'"
          >
            待审核
            <span
              class="tab-badge warning-badge"
              :class="{ 'pulse-badge': stats.pendingCount > 0 }"
            >
              {{ stats.pendingCount }}
            </span>
          </button>
          <button
            type="button"
            class="status-tab-btn"
            :class="{ 'is-active': filterStatus === 'APPROVED' }"
            @click="filterStatus = 'APPROVED'"
          >
            已通过
            <span class="tab-badge success-badge">{{ stats.approvedCount }}</span>
          </button>
          <button
            type="button"
            class="status-tab-btn"
            :class="{ 'is-active': filterStatus === 'REJECTED' }"
            @click="filterStatus = 'REJECTED'"
          >
            已拒绝
            <span class="tab-badge danger-badge">{{ stats.rejectedCount }}</span>
          </button>
        </div>

        <!-- 空状态展示 -->
        <div v-if="!loading && filteredRequests.length === 0" class="table-empty-state">
          <div class="empty-icon-box">
            <el-icon :size="38"><Bell /></el-icon>
          </div>
          <strong>{{ emptyTitle }}</strong>
          <p class="empty-desc">{{ emptyDescription }}</p>
          <el-button
            v-if="searchKeyword || filterStatus !== 'ALL'"
            size="small"
            plain
            @click="resetFilters"
          >
            重置筛选条件
          </el-button>
        </div>

        <!-- 桌面端表格视图 -->
        <div v-else class="table-scroll desktop-records">
          <el-table
            :data="paginatedRequests"
            v-loading="loading"
            class="requests-table"
            row-key="id"
          >
            <el-table-column prop="id" label="申请编号" width="118">
              <template #default="{ row }">
                <el-tooltip content="点击复制编号" placement="top">
                  <span
                    class="request-id clickable-id"
                    @click="copyText(`#${String(row.id).padStart(4, '0')}`)"
                  >
                    #{{ String(row.id).padStart(4, '0') }}
                  </span>
                </el-tooltip>
              </template>
            </el-table-column>

            <el-table-column prop="username" label="申请人" width="165">
              <template #default="{ row }">
                <div class="user-cell">
                  <span class="user-avatar">{{ (row.username || 'U').slice(0, 1).toUpperCase() }}</span>
                  <div class="user-cell-meta">
                    <strong class="user-name" :title="row.username">{{ row.username || '-' }}</strong>
                    <small class="user-id">UID: {{ row.userId }}</small>
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column prop="feeAmount" label="手续费" width="130" align="right">
              <template #default="{ row }">
                <div v-if="row.feeAmount != null" class="fee-cell">
                  <span class="money-cell fee-amount">{{ formatCurrency(row.feeAmount) }}</span>
                  <span
                    class="fee-rate-tag"
                    :class="{ 'is-seal': row.requestType === 'SEAL' }"
                    :title="row.requestType === 'SEAL' ? '盖章钱包等额兑换，免收手续费' : '发票额度充值手续费率 3%'"
                  >
                    {{ getFeeRate(row.feeAmount, row.amount, row.requestType) }}
                  </span>
                </div>
                <el-tag v-else size="small" type="info" effect="plain" class="history-tag">历史记录</el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="amount" label="到账额度 / 类型" width="175" align="right">
              <template #default="{ row }">
                <div class="quota-cell">
                  <span class="money-cell amount-positive">+{{ formatCurrency(row.amount) }}</span>
                  <div class="quota-type-wrapper">
                    <span v-if="row.requestType === 'SEAL'" class="type-pill seal">
                      盖章钱包
                    </span>
                    <span v-else class="type-pill invoice">
                      发票额度
                    </span>
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column prop="status" label="状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag class="status-tag" :class="getStatusClass(row.status)">
                  <i class="status-dot" :class="{ 'is-pulsing': row.status === 'PENDING' }" />
                  {{ getStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="screenshotUrl" label="充值凭证" width="125" align="center">
              <template #default="{ row }">
                <div v-if="row.screenshotUrl" class="proof-thumb-wrapper">
                  <el-image
                    :src="getImageUrl(row.screenshotUrl)"
                    :preview-src-list="[getImageUrl(row.screenshotUrl)]"
                    preview-teleported
                    fit="cover"
                    class="table-screenshot-thumb"
                  >
                    <template #placeholder>
                      <div class="thumb-loading"><el-icon class="is-loading"><Loading /></el-icon></div>
                    </template>
                    <template #error>
                      <div class="thumb-error"><el-icon><Picture /></el-icon></div>
                    </template>
                  </el-image>
                  <span class="thumb-zoom-hint" title="点击放大凭证">
                    <el-icon><ZoomIn /></el-icon>
                  </span>
                </div>
                <span v-else class="text-muted no-proof-tag">无凭证</span>
              </template>
            </el-table-column>

            <el-table-column prop="remark" label="用户备注" min-width="150">
              <template #default="{ row }">
                <el-tooltip
                  v-if="row.remark"
                  :content="row.remark"
                  placement="top"
                  :show-after="300"
                >
                  <span class="remark-cell">{{ row.remark }}</span>
                </el-tooltip>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>

            <el-table-column label="审核信息" min-width="160">
              <template #default="{ row }">
                <div v-if="row.status === 'APPROVED'" class="audit-info-cell">
                  <span class="reviewer-tag"><el-icon><User /></el-icon> {{ row.reviewedByName || '系统管理员' }}</span>
                  <small v-if="row.reviewedAt" class="audit-time">{{ formatDateParts(row.reviewedAt).date }}</small>
                </div>
                <div v-else-if="row.status === 'REJECTED'" class="audit-info-cell is-rejected">
                  <el-tooltip v-if="row.adminRemark" :content="`拒绝原因：${row.adminRemark}`" placement="top">
                    <span class="reject-reason-tag"><el-icon><CircleClose /></el-icon> {{ row.adminRemark }}</span>
                  </el-tooltip>
                  <span v-else class="reject-reason-tag">已拒绝</span>
                  <small v-if="row.reviewedByName" class="reviewer-name">{{ row.reviewedByName }}</small>
                </div>
                <span v-else class="text-muted pending-hint">等待处理</span>
              </template>
            </el-table-column>

            <el-table-column prop="createdAt" label="申请时间" width="150">
              <template #default="{ row }">
                <div class="date-cell">
                  <span>{{ formatDateParts(row.createdAt).date }}</span>
                  <small>{{ formatDateParts(row.createdAt).time }}</small>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="130" fixed="right" align="center">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'PENDING'"
                  type="primary"
                  size="small"
                  :icon="Check"
                  class="review-primary-btn"
                  @click="handleReview(row)"
                >
                  审核申请
                </el-button>
                <el-button
                  v-else
                  size="small"
                  plain
                  :icon="View"
                  class="detail-view-btn"
                  @click="handleViewDetail(row)"
                >
                  查看详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 移动端充值审核卡片列表 -->
        <div v-if="filteredRequests.length > 0" v-loading="loading" class="mobile-records">
          <article v-for="row in paginatedRequests" :key="row.id" class="recharge-record-card">
            <div class="record-card-header">
              <div class="record-user-info">
                <span class="user-avatar-tag">{{ (row.username || 'U').slice(0, 1).toUpperCase() }}</span>
                <div class="record-user-copy">
                  <strong class="record-username">{{ row.username || '-' }}</strong>
                  <span class="record-id">#{{ String(row.id).padStart(4, '0') }}</span>
                </div>
              </div>
              <el-tag class="status-tag" :class="getStatusClass(row.status)">
                <i class="status-dot" :class="{ 'is-pulsing': row.status === 'PENDING' }" />
                {{ getStatusLabel(row.status) }}
              </el-tag>
            </div>

            <div class="record-card-body">
              <div class="record-detail-row">
                <span class="detail-label">手续费 (实付)</span>
                <strong v-if="row.feeAmount != null" class="money-cell">{{ formatCurrency(row.feeAmount) }}</strong>
                <span v-else class="text-muted">历史记录</span>
              </div>
              <div class="record-detail-row">
                <span class="detail-label">到账额度 / 类型</span>
                <div class="mobile-amount-cell">
                  <strong class="money-cell amount-positive">+{{ formatCurrency(row.amount) }}</strong>
                  <span :class="['type-pill', row.requestType === 'SEAL' ? 'seal' : 'invoice']">
                    {{ row.requestType === 'SEAL' ? '盖章钱包' : '发票额度' }}
                  </span>
                </div>
              </div>

              <div v-if="row.screenshotUrl" class="record-detail-row full-width proof-row">
                <span class="detail-label">转账凭证截图</span>
                <el-image
                  :src="getImageUrl(row.screenshotUrl)"
                  :preview-src-list="[getImageUrl(row.screenshotUrl)]"
                  preview-teleported
                  fit="cover"
                  class="mobile-screenshot-thumb"
                />
              </div>

              <div v-if="row.remark" class="record-detail-row full-width">
                <span class="detail-label">用户备注</span>
                <span class="remark-cell">{{ row.remark }}</span>
              </div>

              <div v-if="row.status === 'REJECTED' && row.adminRemark" class="record-detail-row full-width reject-alert-row">
                <span class="detail-label">拒绝原因</span>
                <span class="danger-text">{{ row.adminRemark }}</span>
              </div>

              <div class="record-detail-row full-width">
                <span class="detail-label">申请时间</span>
                <span class="time-text">{{ formatDateTime(row.createdAt) }}</span>
              </div>
            </div>

            <div class="record-card-actions">
              <el-button
                v-if="row.status === 'PENDING'"
                type="primary"
                size="small"
                :icon="Check"
                @click="handleReview(row)"
              >
                立即审核
              </el-button>
              <el-button
                v-else
                type="info"
                size="small"
                plain
                :icon="View"
                @click="handleViewDetail(row)"
              >
                查看详情
              </el-button>
            </div>
          </article>
        </div>

        <!-- 底部分页栏 -->
        <div v-if="filteredRequests.length > 0" class="pagination-bar">
          <span class="pagination-total">共 {{ filteredRequests.length }} 条记录</span>
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="filteredRequests.length"
            layout="sizes, prev, pager, next, jumper"
            background
            @size-change="handlePageSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </AnimatedContent>
    </main>

    <!-- 审核弹窗 -->
    <el-dialog
      v-model="reviewDialogVisible"
      :title="`审核充值申请 #${String(currentRequest?.id || '').padStart(4, '0')}`"
      width="860px"
      class="review-dialog"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div v-if="currentRequest" class="review-dialog-grid">
        <!-- 左侧：转账凭证大图区域 -->
        <div class="dialog-proof-column">
          <div class="proof-header">
            <span class="proof-title">转账凭证原图</span>
            <a
              v-if="currentRequest.screenshotUrl"
              :href="getImageUrl(currentRequest.screenshotUrl)"
              target="_blank"
              rel="noopener noreferrer"
              class="proof-external-link"
            >
              在新窗口打开 <el-icon><TopRight /></el-icon>
            </a>
          </div>
          <div class="proof-card">
            <el-image
              v-if="currentRequest.screenshotUrl"
              :src="getImageUrl(currentRequest.screenshotUrl)"
              :preview-src-list="[getImageUrl(currentRequest.screenshotUrl)]"
              preview-teleported
              fit="contain"
              class="proof-image"
            >
              <template #placeholder>
                <div class="proof-loading">
                  <el-icon class="is-loading" :size="24"><Loading /></el-icon>
                  <span>加载凭证中...</span>
                </div>
              </template>
              <template #error>
                <div class="proof-error">
                  <el-icon :size="28"><Picture /></el-icon>
                  <span>凭证图片加载失败</span>
                </div>
              </template>
            </el-image>
            <div v-else class="proof-empty">
              <el-icon :size="36"><Picture /></el-icon>
              <strong>未上传转账凭证</strong>
              <small>用户提交申请时未附带凭证</small>
            </div>
          </div>
          <span class="proof-helper-tip">提示：点击图片可全屏缩放查看转账单号与明细</span>
        </div>

        <!-- 右侧：核验信息与表单 -->
        <div class="dialog-info-column">
          <!-- 财务概览核验卡 -->
          <div class="info-card">
            <div class="info-user-header">
              <span class="user-avatar-tag large">{{ (currentRequest.username || 'U').slice(0, 1).toUpperCase() }}</span>
              <div class="user-info-text">
                <strong class="user-highlight">{{ currentRequest.username || '-' }}</strong>
                <span class="user-sub-id">UID: {{ currentRequest.userId }} · 申请时间: {{ formatDateTime(currentRequest.createdAt) }}</span>
              </div>
            </div>

            <div class="financial-strip">
              <div class="strip-item">
                <span class="strip-label">手续费 (实付)</span>
                <strong v-if="currentRequest.feeAmount != null" class="strip-value">{{ formatCurrency(currentRequest.feeAmount) }}</strong>
                <span v-else class="strip-value text-muted">历史记录</span>
              </div>
              <div class="strip-divider" />
              <div class="strip-item highlight">
                <div class="strip-label-row">
                  <span class="strip-label">到账充值额度</span>
                  <span :class="['type-pill', currentRequest.requestType === 'SEAL' ? 'seal' : 'invoice']">
                    {{ currentRequest.requestType === 'SEAL' ? '盖章钱包' : '发票额度' }}
                  </span>
                </div>
                <strong class="strip-value amount-badge">+{{ formatCurrency(currentRequest.amount) }}</strong>
              </div>
            </div>

            <div v-if="currentRequest.remark" class="user-remark-box">
              <span class="remark-box-label">用户备注：</span>
              <p class="remark-box-content">{{ currentRequest.remark }}</p>
            </div>
          </div>

          <!-- 审核决策表单 -->
          <el-form
            ref="reviewFormRef"
            :model="reviewForm"
            :rules="computedReviewRules"
            label-position="top"
            class="review-action-form"
          >
            <!-- 视觉化决策单选卡片 -->
            <div class="decision-cards">
              <div
                class="decision-card approve-card"
                :class="{ 'is-selected': reviewForm.status === 'APPROVED' }"
                @click="reviewForm.status = 'APPROVED'"
              >
                <div class="decision-radio-indicator">
                  <el-icon v-if="reviewForm.status === 'APPROVED'"><Check /></el-icon>
                </div>
                <div class="decision-content">
                  <strong>通过并入账</strong>
                  <p>核验到账无误，额度将即时累加至用户可用额度</p>
                </div>
              </div>

              <div
                class="decision-card reject-card"
                :class="{ 'is-selected': reviewForm.status === 'REJECTED' }"
                @click="reviewForm.status = 'REJECTED'"
              >
                <div class="decision-radio-indicator">
                  <el-icon v-if="reviewForm.status === 'REJECTED'"><Close /></el-icon>
                </div>
                <div class="decision-content">
                  <strong>拒绝申请</strong>
                  <p>不增加额度，需填写拒绝原因通知用户</p>
                </div>
              </div>
            </div>

            <!-- 快捷拒绝原因选择器 (当选择拒绝时呈现) -->
            <div v-if="reviewForm.status === 'REJECTED'" class="quick-rejection-tags">
              <span class="quick-tags-title">快捷填入原因：</span>
              <button
                v-for="tag in quickRejectTags"
                :key="tag"
                type="button"
                class="quick-tag-chip"
                @click="fillRejectReason(tag)"
              >
                + {{ tag }}
              </button>
            </div>

            <el-form-item
              :label="reviewForm.status === 'REJECTED' ? '拒绝原因（必填）' : '审核备注（可选）'"
              prop="adminRemark"
            >
              <el-input
                v-model="reviewForm.adminRemark"
                type="textarea"
                :rows="3"
                :placeholder="reviewForm.status === 'REJECTED' ? '请填写具体的拒绝原因（如：转账流水号未查到、实付金额与手续费不符等）' : '可填写后台审核说明或转账凭证号备查'"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
          </el-form>
        </div>
      </div>

      <template #footer>
        <div class="dialog-actions">
          <el-button @click="reviewDialogVisible = false">取消</el-button>
          <el-button
            :type="reviewForm.status === 'APPROVED' ? 'primary' : 'danger'"
            :loading="reviewing"
            :icon="reviewForm.status === 'APPROVED' ? Check : Close"
            class="submit-review-btn"
            @click="handleConfirmReview"
          >
            {{ reviewForm.status === 'APPROVED' ? '确认通过并增加额度' : '确认拒绝该申请' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="`充值申请详情 #${String(currentRequest?.id || '').padStart(4, '0')}`"
      width="840px"
      class="detail-dialog"
      destroy-on-close
    >
      <div v-if="currentRequest" class="detail-dialog-container">
        <!-- 顶部审核状态横幅 -->
        <div
          class="status-banner"
          :class="{
            'is-approved': currentRequest.status === 'APPROVED',
            'is-rejected': currentRequest.status === 'REJECTED',
            'is-pending': currentRequest.status === 'PENDING'
          }"
        >
          <div class="banner-icon-box">
            <el-icon v-if="currentRequest.status === 'APPROVED'" :size="20"><CircleCheck /></el-icon>
            <el-icon v-else-if="currentRequest.status === 'REJECTED'" :size="20"><CircleClose /></el-icon>
            <el-icon v-else :size="20"><Clock /></el-icon>
          </div>
          <div class="banner-text">
            <strong>
              {{ currentRequest.status === 'APPROVED' ? '该申请已审核通过并入账' : currentRequest.status === 'REJECTED' ? '该充值申请已被拒绝' : '该充值申请等待管理员审核' }}
            </strong>
            <span v-if="currentRequest.status === 'APPROVED'">
              审核人：{{ currentRequest.reviewedByName || '系统管理员' }} · 审核时间：{{ formatDateTime(currentRequest.reviewedAt) }}
            </span>
            <span v-else-if="currentRequest.status === 'REJECTED'">
              拒绝原因：{{ currentRequest.adminRemark || '未详细说明' }}（审核人：{{ currentRequest.reviewedByName || '-' }}，时间：{{ formatDateTime(currentRequest.reviewedAt) }}）
            </span>
            <span v-else>
              申请提交于 {{ formatDateTime(currentRequest.createdAt) }}，请核验到账后进行处理
            </span>
          </div>
        </div>

        <div class="review-dialog-grid">
          <!-- 左侧：转账凭证 -->
          <div class="dialog-proof-column">
            <div class="proof-header">
              <span class="proof-title">转账凭证原图</span>
              <a
                v-if="currentRequest.screenshotUrl"
                :href="getImageUrl(currentRequest.screenshotUrl)"
                target="_blank"
                rel="noopener noreferrer"
                class="proof-external-link"
              >
                查看原图 <el-icon><TopRight /></el-icon>
              </a>
            </div>
            <div class="proof-card">
              <el-image
                v-if="currentRequest.screenshotUrl"
                :src="getImageUrl(currentRequest.screenshotUrl)"
                :preview-src-list="[getImageUrl(currentRequest.screenshotUrl)]"
                preview-teleported
                fit="contain"
                class="proof-image"
              />
              <div v-else class="proof-empty">
                <el-icon :size="32"><Picture /></el-icon>
                <span>未上传转账凭证</span>
              </div>
            </div>
          </div>

          <!-- 右侧：完整详情卡片 -->
          <div class="dialog-info-column">
            <div class="info-card detail-full-card">
              <div class="info-row">
                <span class="info-label">申请编号</span>
                <span class="info-value request-id">#{{ String(currentRequest.id).padStart(4, '0') }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">申请用户</span>
                <strong class="info-value">{{ currentRequest.username || '-' }} (UID: {{ currentRequest.userId }})</strong>
              </div>
              <div class="info-row">
                <span class="info-label">实付手续费</span>
                <strong v-if="currentRequest.feeAmount != null" class="info-value">{{ formatCurrency(currentRequest.feeAmount) }}</strong>
                <span v-else class="info-value text-muted">历史记录</span>
              </div>
              <div class="info-row">
                <span class="info-label">充值业务</span>
                <span :class="['type-pill', currentRequest.requestType === 'SEAL' ? 'seal' : 'invoice']">
                  {{ currentRequest.requestType === 'SEAL' ? '盖章钱包' : '发票额度' }}
                </span>
              </div>
              <div class="info-row">
                <span class="info-label">申请到账额度</span>
                <strong class="info-value amount-badge">+{{ formatCurrency(currentRequest.amount) }}</strong>
              </div>
              <div class="info-row">
                <span class="info-label">审核状态</span>
                <el-tag class="status-tag" :class="getStatusClass(currentRequest.status)">
                  <i class="status-dot" />
                  {{ getStatusLabel(currentRequest.status) }}
                </el-tag>
              </div>
              <div class="info-row">
                <span class="info-label">用户备注</span>
                <span class="info-value">{{ currentRequest.remark || '无备注' }}</span>
              </div>
              <div v-if="currentRequest.adminRemark" class="info-row">
                <span class="info-label">{{ currentRequest.status === 'REJECTED' ? '拒绝原因' : '审核备注' }}</span>
                <span class="info-value" :class="{ 'danger-text': currentRequest.status === 'REJECTED' }">
                  {{ currentRequest.adminRemark }}
                </span>
              </div>
              <div class="info-row">
                <span class="info-label">审核人员</span>
                <span class="info-value">{{ currentRequest.reviewedByName || '-' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">提交时间</span>
                <span class="info-value text-muted">{{ formatDateTime(currentRequest.createdAt) }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">审核时间</span>
                <span class="info-value text-muted">{{ currentRequest.reviewedAt ? formatDateTime(currentRequest.reviewedAt) : '-' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-actions">
          <el-button
            v-if="currentRequest?.status === 'PENDING'"
            type="primary"
            :icon="Check"
            @click="openReviewFromDetail"
          >
            去审核此申请
          </el-button>
          <el-button @click="detailDialogVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  Bell,
  Check,
  CircleCheck,
  CircleClose,
  Clock,
  Close,
  Coin,
  Loading,
  Picture,
  Refresh,
  Search,
  Tickets,
  TopRight,
  User,
  View,
  Wallet,
  ZoomIn
} from '@element-plus/icons-vue'
import { rechargeRequestApi, type RechargeRequest, type ReviewRechargeRequest } from '@/api/rechargeRequest'
import { getImageUrl } from '@/utils/imageUrl'
import AppHeader from '@/components/AppHeader.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import SpotlightCard from '@/components/bits/SpotlightCard.vue'
import CountUp from '@/components/bits/CountUp.vue'

const route = useRoute()
const loading = ref(false)
const allRequests = ref<RechargeRequest[]>([])
const filterStatus = ref('ALL')
const filterType = ref('ALL')
const searchKeyword = ref('')

const page = ref(1)
const pageSize = ref(10)

// 快捷拒绝原因模版
const quickRejectTags = [
  '未查收到该笔转账',
  '转账金额与手续费不符',
  '转账凭证模糊不清',
  '凭证信息与申请不一致',
  '重复提交的充值申请'
]

// 数据概览指标计算
const stats = computed(() => {
  const total = allRequests.value.length
  let pendingCount = 0
  let pendingAmount = 0
  let approvedCount = 0
  let approvedAmount = 0
  let totalFee = 0
  let rejectedCount = 0

  for (const r of allRequests.value) {
    const amt = Number(r.amount) || 0
    const fee = Number(r.feeAmount) || 0

    if (r.status === 'PENDING') {
      pendingCount++
      pendingAmount += amt
    } else if (r.status === 'APPROVED') {
      approvedCount++
      approvedAmount += amt
      totalFee += fee
    } else if (r.status === 'REJECTED') {
      rejectedCount++
    }
  }

  return {
    total,
    pendingCount,
    pendingAmount,
    approvedCount,
    approvedAmount,
    totalFee,
    rejectedCount
  }
})

// 过滤后的申请记录
const filteredRequests = computed(() => {
  return allRequests.value.filter(req => {
    // 状态筛选
    if (filterStatus.value && filterStatus.value !== 'ALL') {
      if (req.status !== filterStatus.value) return false
    }

    // 业务类型筛选
    if (filterType.value && filterType.value !== 'ALL') {
      const type = req.requestType || 'INVOICE'
      if (type !== filterType.value) return false
    }

    // 关键词筛选
    const kw = searchKeyword.value.trim().toLowerCase()
    if (kw) {
      const idStr = String(req.id)
      const paddedIdStr = `#${idStr.padStart(4, '0')}`.toLowerCase()
      const username = (req.username || '').toLowerCase()
      const remark = (req.remark || '').toLowerCase()
      const adminRemark = (req.adminRemark || '').toLowerCase()

      const matched =
        username.includes(kw) ||
        remark.includes(kw) ||
        adminRemark.includes(kw) ||
        idStr.includes(kw) ||
        paddedIdStr.includes(kw)

      if (!matched) return false
    }

    return true
  })
})

// 分页列表
const paginatedRequests = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredRequests.value.slice(start, start + pageSize.value)
})

// 空状态文案
const emptyTitle = computed(() => {
  if (searchKeyword.value.trim()) return '未找到匹配的充值申请'
  if (filterStatus.value === 'PENDING') return '暂无待审核申请'
  if (filterStatus.value === 'APPROVED') return '暂无已通过申请'
  if (filterStatus.value === 'REJECTED') return '暂无已拒绝申请'
  return '暂无充值申请记录'
})

const emptyDescription = computed(() => {
  if (searchKeyword.value.trim()) return `关键词「${searchKeyword.value}」未匹配到任何结果，请尝试其他词`
  if (filterStatus.value !== 'ALL') return '当前筛选状态下没有任何充值记录'
  return '用户提交充值申请后将在此处统一展示与审核'
})

// 重置筛选
const resetFilters = () => {
  filterStatus.value = 'ALL'
  filterType.value = 'ALL'
  searchKeyword.value = ''
  page.value = 1
}

watch([filterStatus, filterType, searchKeyword], () => {
  page.value = 1
})

watch(filteredRequests, (newList) => {
  const maxPage = Math.ceil(newList.length / pageSize.value) || 1
  if (page.value > maxPage) {
    page.value = maxPage
  }
})

const handlePageSizeChange = (val: number) => {
  pageSize.value = val
  page.value = 1
}

const handleCurrentChange = (val: number) => {
  page.value = val
}

// 加载申请列表
const loadRequests = async () => {
  loading.value = true
  try {
    const data = await rechargeRequestApi.getAllRequests()
    allRequests.value = data || []
  } catch (error: any) {
    ElMessage.error(error.message || '加载充值申请失败')
  } finally {
    loading.value = false
  }
}

// 复制工具函数
const copyText = async (text: string) => {
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      ElMessage.success(`已复制 ${text}`)
    } else {
      const input = document.createElement('input')
      input.value = text
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      document.body.removeChild(input)
      ElMessage.success(`已复制 ${text}`)
    }
  } catch {
    ElMessage.info(`单号：${text}`)
  }
}

const getStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已拒绝'
  }
  return labels[status] || status
}

const getStatusClass = (status: string) => {
  const classes: Record<string, string> = {
    PENDING: 'is-pending',
    APPROVED: 'is-completed',
    REJECTED: 'is-danger'
  }
  return classes[status] || ''
}

const formatCurrency = (amount: number) =>
  new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2
  }).format(Number(amount))

const getFeeRate = (fee?: number | null, amount?: number | null, requestType?: string) => {
  if (requestType === 'SEAL') return '1:1'
  if (fee == null || !amount || amount <= 0) return '3%'
  const percent = (Number(fee) / Number(amount)) * 100
  return `${Number(percent.toFixed(1))}%`
}

const formatDateTime = (value?: string | null) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(date)
}

const formatDateParts = (value?: string | null) => {
  if (!value) return { date: '-', time: '' }
  const formatted = formatDateTime(value)
  const separatorIndex = formatted.lastIndexOf(' ')
  if (separatorIndex < 0) return { date: formatted, time: '' }
  return {
    date: formatted.slice(0, separatorIndex),
    time: formatted.slice(separatorIndex + 1)
  }
}

// 审核相关
const reviewDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const currentRequest = ref<RechargeRequest | null>(null)
const reviewing = ref(false)
const reviewFormRef = ref<FormInstance>()

const reviewForm = ref<ReviewRechargeRequest>({
  status: 'APPROVED',
  adminRemark: ''
})

const computedReviewRules = computed<FormRules>(() => ({
  status: [{ required: true, message: '请选择审核结果', trigger: 'change' }],
  adminRemark: reviewForm.value.status === 'REJECTED'
    ? [{ required: true, message: '请填写拒绝原因，以便通知用户', trigger: 'blur' }]
    : []
}))

const handleReview = (row: RechargeRequest) => {
  currentRequest.value = row
  reviewForm.value = {
    status: 'APPROVED',
    adminRemark: ''
  }
  reviewDialogVisible.value = true
  reviewFormRef.value?.clearValidate()
}

// 切换审核结果时清空旧的校验提示
watch(() => reviewForm.value.status, () => {
  reviewFormRef.value?.clearValidate()
})

const handleViewDetail = (row: RechargeRequest) => {
  currentRequest.value = row
  detailDialogVisible.value = true
}

const openReviewFromDetail = () => {
  if (!currentRequest.value) return
  detailDialogVisible.value = false
  handleReview(currentRequest.value)
}

const fillRejectReason = (tag: string) => {
  const current = reviewForm.value.adminRemark || ''
  if (!current.trim()) {
    reviewForm.value.adminRemark = tag
  } else if (!current.includes(tag)) {
    reviewForm.value.adminRemark = `${current}；${tag}`
  }
}

const handleConfirmReview = async () => {
  if (!reviewFormRef.value || !currentRequest.value) return

  await reviewFormRef.value.validate(async (valid) => {
    if (!valid) return

    reviewing.value = true
    try {
      await rechargeRequestApi.reviewRequest(currentRequest.value!.id, reviewForm.value)
      ElMessage.success(
        reviewForm.value.status === 'APPROVED'
          ? '审核通过，已为用户即时增加开票额度'
          : '已拒绝该充值申请'
      )
      reviewDialogVisible.value = false

      // 跨页面与顶栏消息广播通知刷新
      if (typeof window !== 'undefined') {
        try {
          const channel = new BroadcastChannel('bobapi-recharge-events')
          channel.postMessage({ type: 'RECHARGE_REVIEWED' })
          channel.close()
        } catch (_) {}
        window.dispatchEvent(new CustomEvent('recharge-reviewed'))
      }

      await loadRequests()
    } catch (error: any) {
      ElMessage.error(error.message || '审核失败，请重试')
    } finally {
      reviewing.value = false
    }
  })
}

// 响应路由参数变化（例如顶栏通知直接跳转定位到待审核）
watch(() => route.query.status, (newStatus) => {
  if (typeof newStatus === 'string' && ['PENDING', 'APPROVED', 'REJECTED', 'ALL'].includes(newStatus)) {
    filterStatus.value = newStatus
  }
})

let rechargeChannel: BroadcastChannel | null = null

onMounted(() => {
  if (route.query.status && ['PENDING', 'APPROVED', 'REJECTED'].includes(String(route.query.status))) {
    filterStatus.value = String(route.query.status)
  }
  loadRequests()
  if (typeof window !== 'undefined') {
    window.addEventListener('recharge-created', loadRequests)
    try {
      rechargeChannel = new BroadcastChannel('bobapi-recharge-events')
      rechargeChannel.onmessage = (event) => {
        if (event.data?.type === 'RECHARGE_REVIEWED' || event.data?.type === 'RECHARGE_CREATED') {
          loadRequests()
        }
      }
    } catch (_) {}
  }
})

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('recharge-created', loadRequests)
  }
  if (rechargeChannel) {
    rechargeChannel.close()
    rechargeChannel = null
  }
})
</script>

<style scoped>
/* 统计卡片微调 */
.recharge-stats-grid {
  margin-bottom: 20px;
}

.stat-card--alert {
  border-color: #fde68a;
  background: linear-gradient(180deg, #fffbeb, #ffffff);
}

.warning-stat {
  color: var(--color-warning, #b66d0b);
}

.approved-stat {
  color: #12715b;
}

.amount-stat {
  font-size: 22px;
}

/* 主面板与工具栏 */
.requests-panel {
  overflow: hidden;
  border-radius: 8px;
}

.table-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.result-count {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  color: var(--color-text-secondary);
  background: var(--color-primary-soft);
  border: 1px solid #d6e8e2;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.result-count i {
  width: 6px;
  height: 6px;
  background: var(--color-primary);
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgba(18, 113, 91, 0.12);
}

.search-input {
  width: 220px;
}

.filter-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.business-select {
  width: 135px;
}

.refresh-button {
  width: 38px;
  height: 38px;
  padding: 0;
  display: grid;
  place-items: center;
}

/* 状态切换 Tab */
.status-tab-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  background: var(--color-surface-muted, #f8faf9);
  border-bottom: 1px solid var(--color-border);
  overflow-x: auto;
}

.status-tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 20px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 180ms ease;
  white-space: nowrap;
}

.status-tab-btn:hover {
  background: rgba(18, 113, 91, 0.06);
  color: var(--color-primary);
}

.status-tab-btn.is-active {
  background: var(--color-surface);
  color: var(--color-primary);
  border-color: var(--color-border-strong);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.tab-badge {
  padding: 1px 7px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 700;
  background: #e5e7eb;
  color: #4b5563;
}

.tab-badge.warning-badge {
  background: #fef3c7;
  color: #b45309;
}

.tab-badge.success-badge {
  background: #d1fae5;
  color: #065f46;
}

.tab-badge.danger-badge {
  background: #fee2e2;
  color: #991b1b;
}

.pulse-badge {
  animation: pulse-badge-glow 2s infinite;
}

@keyframes pulse-badge-glow {
  0% {
    box-shadow: 0 0 0 0 rgba(217, 119, 6, 0.5);
  }
  70% {
    box-shadow: 0 0 0 5px rgba(217, 119, 6, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(217, 119, 6, 0);
  }
}

/* 空状态 */
.table-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 260px;
  padding: 40px 20px;
  text-align: center;
  gap: 10px;
}

.empty-icon-box {
  display: grid;
  place-items: center;
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: #f0fdf4;
  color: var(--color-primary);
  margin-bottom: 4px;
}

.table-empty-state strong {
  font-size: 15px;
  color: var(--color-text);
}

.empty-desc {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-muted);
  max-width: 360px;
}

/* 表格单元格美化 */
.requests-table :deep(.el-table__row td) {
  transition: background-color 180ms ease;
  padding: 12px 0;
}

.requests-table :deep(.el-table__row:hover td) {
  background: #f5faf8 !important;
}

.request-id {
  display: inline-flex;
  padding: 4px 7px;
  color: #556560;
  background: #f1f5f3;
  border: 1px solid #dfe7e4;
  border-radius: 5px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.clickable-id {
  cursor: pointer;
  transition: all 140ms ease;
}

.clickable-id:hover {
  background: var(--color-primary-soft);
  color: var(--color-primary);
  border-color: #bad3cb;
  transform: translateY(-1px);
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #e9f4f0, #d5eae2);
  color: var(--color-primary);
  font-weight: 700;
  font-size: 13px;
  border: 1px solid #c2ded4;
  flex-shrink: 0;
}

.user-cell-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.user-name {
  color: var(--color-text);
  font-size: 13px;
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-id {
  font-size: 11px;
  color: var(--color-text-muted);
}

.fee-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.fee-amount {
  font-weight: 600;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
  font-size: 13px;
}

.fee-rate-tag {
  font-size: 11px;
  color: var(--color-text-muted);
  background: #f1f5f3;
  padding: 0 4px;
  border-radius: 3px;
}

.fee-rate-tag.is-seal {
  background: var(--color-accent-soft, #fff6df);
  color: #b66d0b;
  border: 1px solid rgba(214, 154, 45, 0.25);
  font-weight: 600;
}

.history-tag {
  font-size: 11px;
}

.quota-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
  white-space: nowrap;
}

.amount-positive {
  color: var(--color-primary);
  font-weight: 700;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  letter-spacing: -0.01em;
}

.quota-type-wrapper {
  display: flex;
  justify-content: flex-end;
  line-height: 1;
}

.type-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 1px 7px;
  height: 18px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.type-pill.invoice {
  background: var(--color-primary-soft, #e9f4f0);
  color: var(--color-primary, #12715b);
  border: 1px solid rgba(18, 113, 91, 0.22);
}

.type-pill.seal {
  background: var(--color-accent-soft, #fff6df);
  color: #b66d0b;
  border: 1px solid rgba(214, 154, 45, 0.28);
}

.strip-label-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.mobile-amount-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 状态标签 */
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid transparent;
}

.status-tag.is-pending {
  color: #b45309;
  background: #fef3c7;
  border-color: #fde68a;
}

.status-tag.is-completed {
  color: #065f46;
  background: #d1fae5;
  border-color: #a7f3d0;
}

.status-tag.is-danger {
  color: #991b1b;
  background: #fee2e2;
  border-color: #fecaca;
}

.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-dot.is-pulsing {
  animation: pulse-dot 1.8s infinite;
}

@keyframes pulse-dot {
  0% {
    box-shadow: 0 0 0 0 rgba(217, 119, 6, 0.7);
  }
  70% {
    box-shadow: 0 0 0 5px rgba(217, 119, 6, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(217, 119, 6, 0);
  }
}

/* 凭证缩略图 */
.proof-thumb-wrapper {
  position: relative;
  display: inline-block;
  width: 48px;
  height: 48px;
  border-radius: 7px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  background: #f8faf9;
  cursor: zoom-in;
  transition: transform 160ms ease, box-shadow 160ms ease;
}

.proof-thumb-wrapper:hover {
  transform: scale(1.08);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  border-color: #bad3cb;
}

.proof-thumb-wrapper:hover .thumb-zoom-hint {
  opacity: 1;
}

.table-screenshot-thumb {
  width: 100%;
  height: 100%;
  display: block;
}

.thumb-zoom-hint {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.35);
  color: #fff;
  font-size: 16px;
  opacity: 0;
  transition: opacity 140ms ease;
  pointer-events: none;
}

.thumb-loading,
.thumb-error {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  color: var(--color-text-muted);
}

.no-proof-tag {
  font-size: 12px;
}

.remark-cell {
  color: var(--color-text);
  font-size: 13px;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

/* 审核信息单元格 */
.audit-info-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
}

.reviewer-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-secondary);
  font-weight: 550;
}

.audit-time {
  color: var(--color-text-muted);
  font-size: 11px;
}

.audit-info-cell.is-rejected .reject-reason-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #dc2626;
  font-weight: 600;
  max-width: 170px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reviewer-name {
  color: var(--color-text-muted);
  font-size: 11px;
}

.pending-hint {
  font-size: 12px;
  color: #d97706;
}

.date-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
  font-size: 12px;
}

.date-cell small {
  color: var(--color-text-muted);
  font-size: 11px;
}

.review-primary-btn {
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(18, 113, 91, 0.2);
}

.detail-view-btn {
  font-weight: 600;
}

/* 分页栏 */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-top: 1px solid var(--color-border);
}

.pagination-total {
  font-size: 13px;
  color: var(--color-text-secondary);
}

/* 移动端卡片视图 */
.mobile-records {
  display: none;
}

/* 弹窗设计 */
.review-dialog :deep(.el-dialog__header),
.detail-dialog :deep(.el-dialog__header) {
  padding: 18px 24px 14px;
  margin-right: 0;
  border-bottom: 1px solid var(--color-border);
}

.review-dialog :deep(.el-dialog__title),
.detail-dialog :deep(.el-dialog__title) {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
}

.review-dialog :deep(.el-dialog__body),
.detail-dialog :deep(.el-dialog__body) {
  padding: 20px 24px;
}

.review-dialog :deep(.el-dialog__footer),
.detail-dialog :deep(.el-dialog__footer) {
  padding: 14px 24px 18px;
  border-top: 1px solid var(--color-border);
}

.review-dialog-grid {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 24px;
}

.dialog-proof-column {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.proof-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.proof-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text);
}

.proof-external-link {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: var(--color-primary);
  text-decoration: none;
  font-weight: 600;
}

.proof-external-link:hover {
  text-decoration: underline;
}

.proof-card {
  height: 380px;
  border-radius: 10px;
  border: 1px solid var(--color-border);
  background: #0f172a;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
  box-shadow: inset 0 2px 6px rgba(0, 0, 0, 0.25);
}

.proof-image {
  width: 100%;
  height: 100%;
  cursor: zoom-in;
}

.proof-loading,
.proof-error,
.proof-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #94a3b8;
  font-size: 12px;
  text-align: center;
  padding: 20px;
}

.proof-empty strong {
  font-size: 14px;
  color: #cbd5e1;
}

.proof-helper-tip {
  font-size: 11px;
  color: var(--color-text-muted);
}

.dialog-info-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-card {
  background: var(--color-surface-soft, #f4f7f6);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-user-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border);
}

.user-avatar-tag.large {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: linear-gradient(135deg, #e6f4f0, #c8e8dd);
  color: var(--color-primary);
  font-size: 16px;
  font-weight: 700;
  border: 1px solid #b7dacf;
}

.user-info-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-highlight {
  font-size: 15px;
  color: var(--color-text);
  font-weight: 700;
}

.user-sub-id {
  font-size: 12px;
  color: var(--color-text-muted);
}

.financial-strip {
  display: grid;
  grid-template-columns: 1fr auto 1.3fr;
  align-items: center;
  background: #ffffff;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px 16px;
}

.strip-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.strip-item.highlight {
  text-align: right;
}

.strip-label {
  font-size: 11px;
  color: var(--color-text-muted);
  font-weight: 600;
}

.strip-value {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

.amount-badge {
  font-size: 18px;
  color: #12715b;
}

.strip-divider {
  width: 1px;
  height: 32px;
  background: var(--color-border);
  margin: 0 16px;
}

.user-remark-box {
  background: #ffffff;
  border: 1px dashed var(--color-border-strong);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
}

.remark-box-label {
  color: var(--color-text-muted);
  font-weight: 600;
  margin-right: 4px;
}

.remark-box-content {
  margin: 4px 0 0;
  color: var(--color-text);
  line-height: 1.5;
  white-space: pre-wrap;
}

/* 视觉化决策单选卡片 */
.decision-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 14px;
}

.decision-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  border-radius: 8px;
  border: 2px solid var(--color-border);
  background: var(--color-surface);
  cursor: pointer;
  transition: all 180ms ease;
  user-select: none;
}

.decision-card:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
}

.decision-card.approve-card.is-selected {
  border-color: #10b981;
  background: #f0fdf4;
}

.decision-card.reject-card.is-selected {
  border-color: #ef4444;
  background: #fef2f2;
}

.decision-radio-indicator {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid #cbd5e1;
  margin-top: 2px;
  flex-shrink: 0;
  color: #fff;
  font-size: 12px;
  transition: all 160ms ease;
}

.approve-card.is-selected .decision-radio-indicator {
  border-color: #10b981;
  background: #10b981;
}

.reject-card.is-selected .decision-radio-indicator {
  border-color: #ef4444;
  background: #ef4444;
}

.decision-content strong {
  display: block;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 2px;
}

.decision-content p {
  margin: 0;
  font-size: 11px;
  color: var(--color-text-muted);
  line-height: 1.4;
}

/* 快捷拒绝标签 */
.quick-rejection-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  padding: 10px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 6px;
}

.quick-tags-title {
  font-size: 11px;
  color: #c2410c;
  font-weight: 700;
}

.quick-tag-chip {
  padding: 3px 8px;
  border-radius: 4px;
  border: 1px solid #fed7aa;
  background: #ffffff;
  color: #9a3412;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all 140ms ease;
}

.quick-tag-chip:hover {
  background: #ffedd5;
  border-color: #fb923c;
  transform: translateY(-1px);
}

/* 详情弹窗样式 */
.detail-dialog-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-banner {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  border-radius: 8px;
  border: 1px solid transparent;
}

.status-banner.is-approved {
  background: #ecfdf5;
  border-color: #a7f3d0;
  color: #065f46;
}

.status-banner.is-rejected {
  background: #fef2f2;
  border-color: #fecaca;
  color: #991b1b;
}

.status-banner.is-pending {
  background: #fffbeb;
  border-color: #fde68a;
  color: #92400e;
}

.banner-icon-box {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: currentColor;
  color: #fff;
  flex-shrink: 0;
}

.banner-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.banner-text strong {
  font-size: 14px;
  font-weight: 700;
}

.banner-text span {
  font-size: 12px;
  opacity: 0.9;
}

.detail-full-card {
  gap: 12px;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.info-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.info-label {
  color: var(--color-text-muted);
  font-weight: 500;
}

.info-value {
  color: var(--color-text);
  font-weight: 600;
}

.danger-text {
  color: #dc2626;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.submit-review-btn {
  font-weight: 600;
}

/* 响应式样式适配 */
@media (max-width: 900px) {
  .table-tools {
    width: 100%;
    margin-top: 10px;
  }

  .search-input {
    flex: 1;
    min-width: 160px;
  }
}

@media (max-width: 768px) {
  .desktop-records {
    display: none;
  }

  .mobile-records {
    display: grid;
    gap: 12px;
    padding: 14px;
    background: #f7f9f8;
  }

  .recharge-record-card {
    padding: 14px 16px;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: 8px;
    box-shadow: 0 4px 12px rgba(24, 39, 34, 0.04);
  }

  .record-card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 10px;
    border-bottom: 1px solid var(--color-border);
  }

  .record-user-info {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .user-avatar-tag {
    display: grid;
    place-items: center;
    width: 30px;
    height: 30px;
    border-radius: 50%;
    background: var(--color-primary-soft, #e9f4f0);
    color: var(--color-primary, #12715b);
    font-size: 12px;
    font-weight: 700;
  }

  .record-user-copy {
    display: flex;
    flex-direction: column;
    gap: 1px;
  }

  .record-username {
    font-size: 13px;
    color: var(--color-text);
  }

  .record-id {
    font-size: 11px;
    color: var(--color-text-muted);
    font-family: monospace;
  }

  .record-card-body {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px 12px;
    margin: 10px 0;
  }

  .record-detail-row {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .record-detail-row.full-width {
    grid-column: 1 / -1;
  }

  .detail-label {
    color: var(--color-text-muted);
    font-size: 11px;
    font-weight: 600;
  }

  .time-text {
    font-size: 12px;
    color: var(--color-text-secondary);
  }

  .mobile-screenshot-thumb {
    width: 64px;
    height: 64px;
    border-radius: 6px;
    border: 1px solid var(--color-border);
    cursor: pointer;
    margin-top: 4px;
  }

  .record-card-actions {
    display: flex;
    padding-top: 10px;
    border-top: 1px dashed var(--color-border);
  }

  .record-card-actions .el-button {
    width: 100%;
    height: 36px;
  }

  .review-dialog-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .proof-card {
    height: 240px;
  }

  .decision-cards {
    grid-template-columns: 1fr;
  }

  .financial-strip {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .strip-divider {
    display: none;
  }

  .strip-item.highlight {
    text-align: left;
  }

  .dialog-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
    width: 100%;
  }

  .dialog-actions .el-button {
    width: 100%;
    margin-left: 0 !important;
  }

  :deep(.el-dialog.review-dialog),
  :deep(.el-dialog.detail-dialog) {
    width: 94% !important;
    max-width: 94vw !important;
    margin: 16px auto !important;
  }

  :deep(.review-dialog .el-dialog__body),
  :deep(.detail-dialog .el-dialog__body) {
    padding: 16px;
  }
}
</style>