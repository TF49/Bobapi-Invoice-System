<template>
  <div class="workspace-shell">
    <AppHeader title="充值申请审核" />

    <main class="workspace-content">
      <AnimatedContent tag="section" class="surface-panel requests-panel" :distance="10">
        <div class="panel-header">
          <div class="panel-heading">
            <span class="panel-heading-icon"><Bell /></span>
            <div>
              <h2>充值申请列表</h2>
              <p>审核用户提交的充值申请</p>
            </div>
          </div>
          <div class="filter-control">
            <span class="filter-label"><Filter /> 筛选状态</span>
            <el-select
              v-model="filterStatus"
              placeholder="全部状态"
              clearable
              style="width: 140px"
              @change="loadRequests"
            >
              <el-option label="全部状态" value="" />
              <el-option label="待审核" value="PENDING" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已拒绝" value="REJECTED" />
            </el-select>
          </div>
        </div>

        <div v-if="!loading && requests.length === 0" class="table-empty-state">
          <span><Bell /></span>
          <strong>暂无充值申请</strong>
        </div>

        <div v-else class="table-scroll desktop-records">
          <el-table :data="paginatedRequests" v-loading="loading" class="requests-table">
            <el-table-column prop="id" label="申请编号" width="112">
              <template #default="{ row }">
                <span class="request-id">#{{ String(row.id).padStart(4, '0') }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="username" label="申请人" width="120">
              <template #default="{ row }">
                <span class="username-cell">{{ row.username || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="feeAmount" label="手续费" width="130" align="right">
              <template #default="{ row }">
                <span v-if="row.feeAmount != null" class="money-cell">{{ formatCurrency(row.feeAmount) }}</span>
                <span v-else class="text-muted">历史记录</span>
              </template>
            </el-table-column>
            <el-table-column prop="amount" label="申请额度" width="140" align="right">
              <template #default="{ row }">
                <span class="money-cell amount-positive">{{ formatCurrency(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag class="status-tag" :class="getStatusClass(row.status)">
                  <i class="status-dot" />
                  {{ getStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="screenshotUrl" label="充值截图" width="120" align="center">
              <template #default="{ row }">
                <el-image
                  v-if="row.screenshotUrl"
                  :src="getImageUrl(row.screenshotUrl)"
                  :preview-src-list="[getImageUrl(row.screenshotUrl)]"
                  preview-teleported
                  fit="cover"
                  class="table-screenshot-thumb"
                />
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="用户备注" min-width="150">
              <template #default="{ row }">
                <span class="remark-cell">{{ row.remark || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="申请时间" width="168">
              <template #default="{ row }">
                <div class="date-cell">
                  <span>{{ formatDateParts(row.createdAt).date }}</span>
                  <small>{{ formatDateParts(row.createdAt).time }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'PENDING'"
                  type="primary"
                  size="small"
                  @click="handleReview(row)"
                >
                  审核
                </el-button>
                <el-button
                  v-else
                  type="info"
                  size="small"
                  plain
                  @click="handleViewDetail(row)"
                >
                  详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 移动端充值审核卡片列表 -->
        <div v-if="requests.length > 0" v-loading="loading" class="mobile-records">
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
                <i class="status-dot" />
                {{ getStatusLabel(row.status) }}
              </el-tag>
            </div>
            <div class="record-card-body">
              <div class="record-detail-row">
                <span class="detail-label">手续费</span>
                <strong v-if="row.feeAmount != null" class="money-cell">{{ formatCurrency(row.feeAmount) }}</strong>
                <span v-else class="text-muted">历史记录</span>
              </div>
              <div class="record-detail-row">
                <span class="detail-label">申请额度</span>
                <strong class="money-cell amount-positive">{{ formatCurrency(row.amount) }}</strong>
              </div>
              <div v-if="row.screenshotUrl" class="record-detail-row">
                <span class="detail-label">充值凭证</span>
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
                @click="handleReview(row)"
              >
                审核申请
              </el-button>
              <el-button
                v-else
                type="info"
                size="small"
                plain
                @click="handleViewDetail(row)"
              >
                查看详情
              </el-button>
            </div>
          </article>
        </div>

        <div v-if="requests.length > 0" class="pagination-bar">
          <span>共 {{ requests.length }} 条记录</span>
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="requests.length"
            layout="sizes, prev, pager, next, jumper"
            background
            @size-change="handlePageSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </AnimatedContent>
    </main>

    <!-- 审核对话框 -->
    <el-dialog
      v-model="reviewDialogVisible"
      title="审核充值申请"
      width="820px"
      class="review-dialog"
      :close-on-click-modal="false"
    >
      <div v-if="currentRequest" class="review-dialog-grid">
        <!-- 左侧：转账凭证大图区域 -->
        <div class="dialog-proof-column">
          <div class="proof-header">
            <span class="proof-title">转账凭证截图</span>
            <span class="proof-tip">点击图片可全屏放大</span>
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
                <div class="proof-loading">加载凭证中...</div>
              </template>
              <template #error>
                <div class="proof-error">
                  <el-icon :size="28"><Picture /></el-icon>
                  <span>凭证图片加载失败</span>
                </div>
              </template>
            </el-image>
            <div v-else class="proof-empty">
              <el-icon :size="32"><Picture /></el-icon>
              <span>未上传转账凭证</span>
            </div>
          </div>
        </div>

        <!-- 右侧：申请信息与审核表单 -->
        <div class="dialog-info-column">
          <div class="info-card">
            <div class="info-row">
              <span class="info-label">申请用户</span>
              <strong class="info-value user-highlight">{{ currentRequest.username || '-' }}</strong>
            </div>
            <div class="info-row">
              <span class="info-label">手续费</span>
              <strong v-if="currentRequest.feeAmount != null" class="info-value">{{ formatCurrency(currentRequest.feeAmount) }}</strong>
              <span v-else class="info-value text-muted">历史记录</span>
            </div>
            <div class="info-row">
              <span class="info-label">申请额度</span>
              <strong class="info-value amount-badge">{{ formatCurrency(currentRequest.amount) }}</strong>
            </div>
            <div class="info-row">
              <span class="info-label">申请时间</span>
              <span class="info-value text-muted">{{ formatDateTime(currentRequest.createdAt) }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">用户备注</span>
              <span class="info-value">{{ currentRequest.remark || '无备注' }}</span>
            </div>
          </div>

          <el-form
            ref="reviewFormRef"
            :model="reviewForm"
            :rules="computedReviewRules"
            label-position="top"
            class="review-action-form"
          >
            <el-form-item label="审核结果" prop="status">
              <el-radio-group v-model="reviewForm.status" class="review-status-group">
                <el-radio-button value="APPROVED">通过并入账</el-radio-button>
                <el-radio-button value="REJECTED">拒绝申请</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item
              :label="reviewForm.status === 'REJECTED' ? '拒绝原因（必填）' : '审核备注（可选）'"
              prop="adminRemark"
            >
              <el-input
                v-model="reviewForm.adminRemark"
                type="textarea"
                :rows="3"
                :placeholder="reviewForm.status === 'REJECTED' ? '请详细填写拒绝原因（如：转账流水号不符、凭证不清晰等）' : '可填写充值审核备注信息'"
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
            @click="handleConfirmReview"
          >
            {{ reviewForm.status === 'APPROVED' ? '确认通过入账' : '确认拒绝' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="充值申请详情"
      width="780px"
      class="detail-dialog"
    >
      <div v-if="currentRequest" class="review-dialog-grid">
        <!-- 左侧：转账凭证 -->
        <div class="dialog-proof-column">
          <div class="proof-header">
            <span class="proof-title">转账凭证截图</span>
            <span class="proof-tip">点击图片放大</span>
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

        <!-- 右侧：完整详情 -->
        <div class="dialog-info-column">
          <div class="info-card detail-full-card">
            <div class="info-row">
              <span class="info-label">申请编号</span>
              <span class="info-value">#{{ String(currentRequest.id).padStart(4, '0') }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">申请用户</span>
              <strong class="info-value">{{ currentRequest.username || '-' }}</strong>
            </div>
            <div class="info-row">
              <span class="info-label">手续费</span>
              <strong v-if="currentRequest.feeAmount != null" class="info-value">{{ formatCurrency(currentRequest.feeAmount) }}</strong>
              <span v-else class="info-value text-muted">历史记录</span>
            </div>
            <div class="info-row">
              <span class="info-label">申请额度</span>
              <strong class="info-value amount-badge">{{ formatCurrency(currentRequest.amount) }}</strong>
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
              <span class="info-value">{{ currentRequest.remark || '无' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">审核反馈</span>
              <span class="info-value" :class="{ 'danger-text': currentRequest.status === 'REJECTED' }">
                {{ currentRequest.adminRemark || '无' }}
              </span>
            </div>
            <div class="info-row">
              <span class="info-label">审核人</span>
              <span class="info-value">{{ currentRequest.reviewedByName || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">申请时间</span>
              <span class="info-value text-muted">{{ formatDateTime(currentRequest.createdAt) }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">审核时间</span>
              <span class="info-value text-muted">{{ currentRequest.reviewedAt ? formatDateTime(currentRequest.reviewedAt) : '-' }}</span>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Bell, Filter, Picture } from '@element-plus/icons-vue'
import { rechargeRequestApi, type RechargeRequest, type ReviewRechargeRequest } from '@/api/rechargeRequest'
import { getImageUrl } from '@/utils/imageUrl'
import AppHeader from '@/components/AppHeader.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'

const loading = ref(false)
const requests = ref<RechargeRequest[]>([])
const filterStatus = ref('')

const page = ref(1)
const pageSize = ref(10)

const paginatedRequests = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return requests.value.slice(start, start + pageSize.value)
})

watch(requests, (newList) => {
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

const loadRequests = async () => {
  loading.value = true
  page.value = 1
  try {
    if (filterStatus.value === 'PENDING') {
      requests.value = await rechargeRequestApi.getPendingRequests()
    } else {
      requests.value = await rechargeRequestApi.getAllRequests(filterStatus.value || undefined)
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载充值申请失败')
  } finally {
    loading.value = false
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

const formatDateTime = (value: string) => {
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

const formatDateParts = (value: string) => {
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
}

const handleViewDetail = (row: RechargeRequest) => {
  currentRequest.value = row
  detailDialogVisible.value = true
}

const handleConfirmReview = async () => {
  if (!reviewFormRef.value || !currentRequest.value) return

  await reviewFormRef.value.validate(async (valid) => {
    if (!valid) return

    reviewing.value = true
    try {
      await rechargeRequestApi.reviewRequest(currentRequest.value!.id, reviewForm.value)
      ElMessage.success(reviewForm.value.status === 'APPROVED' ? '审核通过，已为用户增加额度' : '已拒绝该充值申请')
      reviewDialogVisible.value = false
      // 通知顶栏刷新铃铛未读数
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

onMounted(() => {
  loadRequests()
})
</script>

<style scoped>
.request-id {
  display: inline-flex;
  padding: 4px 7px;
  color: #61716b;
  background: #f1f5f3;
  border: 1px solid #e1e9e6;
  border-radius: 5px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.02em;
}

.username-cell {
  font-weight: 600;
  color: var(--color-text);
}

.money-cell {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.amount-positive {
  color: #12715b;
}

.text-muted {
  color: var(--color-text-muted);
}

.remark-cell {
  color: var(--color-text);
  font-size: 13px;
}

.screenshot-link {
  color: var(--color-primary);
  text-decoration: none;
}

.screenshot-link:hover {
  text-decoration: underline;
}

.status-tag.is-pending {
  color: #b58900;
  background: #fdf8e8;
}

.status-tag.is-completed {
  color: #12715b;
  background: #e8f5f1;
}

.status-tag.is-danger {
  color: #c9463d;
  background: #fdf2f1;
}

.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  border-radius: 50%;
  background: currentColor;
}

.date-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.date-cell small {
  color: var(--color-text-muted);
  font-size: 11px;
}

.requests-panel {
  overflow: hidden;
}

.requests-table :deep(.el-table__row td) {
  transition: background-color 180ms ease;
}

.requests-table :deep(.el-table__row:hover td) {
  background: #f5faf8 !important;
}

.mobile-records {
  display: none;
}

.table-screenshot-thumb {
  width: 52px;
  height: 52px;
  border-radius: 6px;
  border: 1px solid var(--color-border);
  cursor: pointer;
  transition: transform 160ms ease, box-shadow 160ms ease;
}

.table-screenshot-thumb:hover {
  transform: scale(1.06);
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1);
}

/* 弹窗双栏布局 */
.review-dialog-grid {
  display: grid;
  grid-template-columns: 340px 1fr;
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
  font-weight: 600;
  color: var(--color-text);
}

.proof-tip {
  font-size: 11px;
  color: var(--color-text-muted);
}

.proof-card {
  height: 360px;
  border-radius: 10px;
  border: 1px solid var(--color-border);
  background: #f8faf9;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
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
  color: var(--color-text-muted);
  font-size: 12px;
}

.dialog-info-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-card {
  background: var(--color-surface-soft, #f4f7f6);
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-full-card {
  gap: 12px;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}

.info-label {
  color: var(--color-text-muted);
  font-weight: 500;
}

.info-value {
  color: var(--color-text);
  font-weight: 550;
}

.user-highlight {
  font-size: 14px;
  color: var(--color-text);
}

.amount-badge {
  font-size: 16px;
  font-weight: 700;
  color: #12715b;
}

.review-action-form {
  margin-top: 4px;
}

.review-status-group :deep(.el-radio-button__inner) {
  padding: 8px 18px;
  font-weight: 600;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.danger-text {
  color: #c9463d;
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
    width: 28px;
    height: 28px;
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
    width: 54px;
    height: 54px;
    border-radius: 6px;
    border: 1px solid var(--color-border);
    cursor: pointer;
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

  /* 审核弹窗移动端转单栏 */
  .review-dialog-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .proof-card {
    height: 220px;
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

  .review-status-group {
    display: flex;
    width: 100%;
  }

  .review-status-group :deep(.el-radio-button) {
    flex: 1;
  }

  .review-status-group :deep(.el-radio-button__inner) {
    width: 100%;
    padding: 8px 12px;
    font-size: 13px;
  }
}
</style>
