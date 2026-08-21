<template>
  <div class="workspace-shell">
    <AppHeader title="我的发票" />

    <main class="workspace-content">
      <AnimatedContent tag="section" class="stats-grid" :distance="10">
        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon neutral"><Tickets /></span>
            <div class="stat-copy">
              <span class="stat-label">申请总数</span>
              <strong class="stat-value"><CountUp :value="invoices.length" /></strong>
            </div>
            <p class="stat-note">全部开票记录</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon warning"><Clock /></span>
            <div class="stat-copy">
              <span class="stat-label">待开票</span>
              <strong class="stat-value"><CountUp :value="pendingCount" /></strong>
            </div>
            <p class="stat-note">等待管理员处理</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon"><CircleCheck /></span>
            <div class="stat-copy">
              <span class="stat-label">已开票</span>
              <strong class="stat-value"><CountUp :value="completedCount" /></strong>
            </div>
            <p class="stat-note">可下载电子凭证</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon"><Wallet /></span>
            <div class="stat-copy">
              <span class="stat-label">已开票金额</span>
              <strong class="stat-value amount-stat"><CountUp :value="completedAmount" :decimals="2" prefix="¥" /></strong>
            </div>
            <p class="stat-note">已完成申请合计</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon success"><Coin /></span>
            <div class="stat-copy">
              <span class="stat-label">我的额度</span>
              <strong class="stat-value quota-stat"><CountUp :value="quotaBalance" :decimals="2" prefix="¥" /></strong>
            </div>
            <p class="stat-note">可用于开票的余额</p>
          </div>
        </SpotlightCard>
      </AnimatedContent>

      <AnimatedContent tag="section" class="surface-panel records-panel" :delay="80">
        <div class="panel-header">
          <div class="panel-heading">
            <span class="panel-heading-icon"><List /></span>
            <div>
              <h2>发票记录</h2>
              <p>查看申请进度与电子凭证</p>
            </div>
          </div>
          <div class="panel-actions">
            <span class="result-count"><i></i>{{ invoices.length }} 条记录</span>
            <el-button
              type="primary"
              :icon="Plus"
              class="submit-action-button"
              @click="showSubmitDialog"
            >
              提交申请
            </el-button>
            <el-button
              class="batch-import-button"
              :icon="Upload"
              :disabled="submitting"
              @click="showBatchImportDialog"
            >
              批量导入
            </el-button>
          </div>
        </div>

        <div v-if="!loading && invoices.length === 0" class="table-empty-state">
          <span><Files /></span>
          <strong>暂无发票记录</strong>
        </div>
        <div v-else class="table-scroll desktop-records">
          <el-table :data="invoices" v-loading="loading" class="records-table">
            <el-table-column prop="id" label="申请编号" width="112">
              <template #default="{ row }">
                <span class="invoice-id">{{ formatInvoiceId(row.id) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="companyName" label="公司名称" min-width="220">
              <template #default="{ row }">
                <div class="company-cell">
                  <span class="company-avatar">{{ getCompanyInitial(row.companyName) }}</span>
                  <strong class="company-name">{{ row.companyName }}</strong>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="taxNumber" label="税号" min-width="190">
              <template #default="{ row }">
                <span class="tax-number-cell">{{ row.taxNumber }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="amount" label="开票金额" width="130" align="right">
              <template #default="{ row }">
                <span class="money-cell">{{ formatCurrency(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="invoiceType" label="开票类型" width="120">
              <template #default="{ row }">
                <el-tag size="small" type="info" effect="plain">{{ row.invoiceType || '技术服务费' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span>{{ row.remark || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="开票状态" width="124" align="center">
              <template #default="{ row }">
                <el-tag class="status-tag" :class="row.status === 'COMPLETED' ? 'is-completed' : 'is-pending'">
                  <i class="status-dot"></i>
                  {{ row.status === 'COMPLETED' ? '已开票' : '待开票' }}
                </el-tag>
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
            <el-table-column label="操作" width="188" align="center" fixed="right">
              <template #default="{ row }">
                <div v-if="row.downloadable && row.fileExists" class="record-actions">
                  <el-button
                    class="record-action-button"
                    type="primary"
                    size="small"
                    :icon="ZoomIn"
                    :loading="previewingId === row.id"
                    @click="handlePreview(row)"
                  >
                    查看
                  </el-button>
                  <el-button
                    class="record-action-button"
                    type="primary"
                    plain
                    size="small"
                    :icon="Download"
                    @click="handleDownload(row)"
                  >
                    下载
                  </el-button>
                </div>
                <span v-else class="empty-action"><i></i>等待开票</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="invoices.length > 0" v-loading="loading" class="mobile-records">
          <article v-for="row in invoices" :key="row.id" class="invoice-record-card">
            <div class="record-card-header">
              <div class="company-cell">
                <span class="company-avatar">{{ getCompanyInitial(row.companyName) }}</span>
                <div class="record-company-copy">
                  <strong class="company-name">{{ row.companyName }}</strong>
                  <span>{{ formatInvoiceId(row.id) }}</span>
                </div>
              </div>
              <el-tag class="status-tag" :class="row.status === 'COMPLETED' ? 'is-completed' : 'is-pending'">
                <i class="status-dot"></i>
                {{ row.status === 'COMPLETED' ? '已开票' : '待开票' }}
              </el-tag>
            </div>

            <dl class="record-card-details">
              <div>
                <dt>税号</dt>
                <dd class="tax-number-cell">{{ row.taxNumber }}</dd>
              </div>
              <div>
                <dt>开票金额</dt>
                <dd class="money-cell">{{ formatCurrency(row.amount) }}</dd>
              </div>
              <div>
                <dt>开票类型</dt>
                <dd>{{ row.invoiceType || '技术服务费' }}</dd>
              </div>
              <div v-if="row.remark">
                <dt>备注</dt>
                <dd>{{ row.remark }}</dd>
              </div>
              <div>
                <dt>申请时间</dt>
                <dd>{{ formatDate(row.createdAt) }}</dd>
              </div>
            </dl>

            <div v-if="row.downloadable && row.fileExists" class="mobile-record-actions">
              <el-button
                type="primary"
                :icon="ZoomIn"
                :loading="previewingId === row.id"
                @click="handlePreview(row)"
              >
                查看发票
              </el-button>
              <el-button type="primary" plain :icon="Download" @click="handleDownload(row)">
                下载文件
              </el-button>
            </div>
            <div v-else class="record-pending-note">
              <Clock />
              <span>管理员处理完成后，可在这里查看和下载发票</span>
            </div>
          </article>
        </div>
      </AnimatedContent>
    </main>

    <!-- 提交发票申请弹窗 -->
    <el-dialog
      v-model="submitDialogVisible"
      title="提交发票申请"
      width="520px"
      class="submit-invoice-dialog"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="公司名称" prop="companyName">
          <el-input v-model="form.companyName" :prefix-icon="OfficeBuilding" placeholder="请输入公司名称" />
        </el-form-item>
        <el-form-item label="税号" prop="taxNumber">
          <el-input
            v-model="form.taxNumber"
            :prefix-icon="Postcard"
            placeholder="15-20 位大写字母或数字"
            maxlength="20"
            @input="normalizeTaxNumber"
          />
        </el-form-item>
        <el-form-item label="开票金额" prop="amount">
          <el-input-number
            v-model="form.amount"
            :min="0.01"
            :max="9999999999.99"
            :precision="2"
            :step="100"
            controls-position="right"
            class="amount-input"
          />
        </el-form-item>
        <el-form-item label="开票类型" prop="invoiceType">
          <el-select v-model="form.invoiceType" placeholder="请选择开票类型" class="type-select">
            <el-option label="技术服务费" value="技术服务费" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注（选填）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="submitDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="submitting"
            @click="handleSubmit"
          >
            {{ submitting ? '正在提交' : '提交申请' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 发票图片预览弹窗 -->
    <el-dialog
      v-model="previewVisible"
      :title="previewTitle"
      width="90%"
      class="invoice-preview-dialog"
      destroy-on-close
      @close="onPreviewClose"
      @closed="onPreviewClosed"
    >
      <div class="preview-body">
        <div v-if="previewError" class="preview-error">
          <span class="preview-error-icon"><PictureRounded /></span>
          <p>图片加载失败，请稍后重试或使用下载功能</p>
        </div>
        <img
          v-else-if="previewSrc"
          :src="previewSrc"
          class="preview-image"
          alt="发票图片"
          @error="previewError = true"
        />
        <div v-else class="preview-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中…</span>
        </div>
      </div>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <InvoiceBatchImportDialog
      v-model="batchImportVisible"
      @success="handleBatchImportSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import {
  CircleCheck,
  Clock,
  Coin,
  Download,
  Files,
  List,
  Loading,
  OfficeBuilding,
  PictureRounded,
  Plus,
  Postcard,
  Promotion,
  Tickets,
  Upload,
  Wallet,
  ZoomIn
} from '@element-plus/icons-vue'
import { invoiceApi, type Invoice, type InvoiceRequest } from '@/api/invoice'
import { quotaApi } from '@/api/quota'
import AppHeader from '@/components/AppHeader.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import CountUp from '@/components/bits/CountUp.vue'
import SpotlightCard from '@/components/bits/SpotlightCard.vue'
import InvoiceBatchImportDialog from '@/components/InvoiceBatchImportDialog.vue'
import { saveBlobResponse } from '@/utils/download'
import { generateIdempotencyKey } from '@/utils/idempotency'
import { ApiRequestError } from '@/utils/request'

const formRef = ref<FormInstance>()
const loading = ref(false)
const submitting = ref(false)
const invoices = ref<Invoice[]>([])
const pendingIdempotencyKey = ref<string | null>(null)

// 批量导入相关
const batchImportVisible = ref(false)

// 额度相关状态
const quotaBalance = ref(0)

// 预览相关状态
const previewVisible = ref(false)
const previewingId = ref<number | null>(null)
const previewSrc = ref<string | null>(null)
const previewTitle = ref('')
const previewError = ref(false)
let previewController: AbortController | null = null
let previewRequestId = 0

const form = reactive<InvoiceRequest>({
  companyName: '',
  taxNumber: '',
  amount: 0.01,
  invoiceType: '技术服务费',
  remark: ''
})

const pendingCount = computed(() => invoices.value.filter(invoice => invoice.status === 'PENDING').length)
const completedCount = computed(() => invoices.value.filter(invoice => invoice.status === 'COMPLETED').length)
const completedAmount = computed(() => invoices.value
  .filter(invoice => invoice.status === 'COMPLETED')
  .reduce((total, invoice) => total + Number(invoice.amount), 0))

const rules = {
  companyName: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
  taxNumber: [
    { required: true, message: '请输入税号', trigger: 'blur' },
    { pattern: /^[A-Z0-9]{15,20}$/, message: '税号格式不正确（15-20位大写字母或数字）', trigger: 'blur' }
  ],
  amount: [
    { required: true, message: '请输入开票金额', trigger: 'blur' },
    { type: 'number', min: 0.01, max: 9999999999.99, message: '开票金额必须在有效范围内', trigger: 'change' }
  ],
  invoiceType: [{ required: true, message: '请选择开票类型', trigger: 'change' }]
}

const formatCurrency = (amount: number) => new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  minimumFractionDigits: 2
}).format(Number(amount))

const formatDate = (value: string) => {
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
  const formatted = formatDate(value)
  const separatorIndex = formatted.lastIndexOf(' ')
  if (separatorIndex < 0) return { date: formatted, time: '' }
  return {
    date: formatted.slice(0, separatorIndex),
    time: formatted.slice(separatorIndex + 1)
  }
}

const formatInvoiceId = (id: number) => `#${String(id).padStart(4, '0')}`

const getCompanyInitial = (companyName: string) => companyName.trim().charAt(0) || '企'

const normalizeTaxNumber = (value: string) => {
  form.taxNumber = value.toUpperCase().replace(/[^A-Z0-9]/g, '')
}

const loadInvoices = async () => {
  loading.value = true
  try {
    invoices.value = await invoiceApi.getMyInvoices()
  } catch (error) {
    console.error('加载发票列表失败', error)
  } finally {
    loading.value = false
  }
}

const loadQuota = async () => {
  try {
    const quota = await quotaApi.getMyQuota()
    quotaBalance.value = quota.balance
  } catch (error) {
    console.error('加载额度信息失败', error)
  }
}

// 提交申请弹窗状态
const submitDialogVisible = ref(false)

const showSubmitDialog = () => {
  submitDialogVisible.value = true
}

const handleSubmit = async () => {
  if (submitting.value) return
  submitting.value = true
  try {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) {
      ElMessage.warning('请检查并完善申请信息')
      return
    }

    // 检查额度是否充足
    if (form.amount > quotaBalance.value) {
      ElMessage.error(`额度不足，当前余额 ¥${quotaBalance.value.toFixed(2)}，需要 ¥${form.amount.toFixed(2)}`)
      return
    }

    const idempotencyKey = pendingIdempotencyKey.value || generateIdempotencyKey()
    pendingIdempotencyKey.value = idempotencyKey
    await invoiceApi.createInvoice({
      companyName: form.companyName.trim(),
      taxNumber: form.taxNumber,
      amount: form.amount,
      invoiceType: form.invoiceType,
      remark: form.remark?.trim() || undefined
    }, idempotencyKey)
    ElMessage.success('提交成功')
    pendingIdempotencyKey.value = null
    formRef.value?.resetFields()
    submitDialogVisible.value = false
    await loadInvoices()
    await loadQuota() // 刷新额度
  } catch (error) {
    // 保留幂等键，网络失败后再次提交会复用同一请求
    console.error('提交发票申请失败', error)
    if (!(error instanceof ApiRequestError)) {
      ElMessage.error('提交失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}

const handlePreview = async (row: Invoice) => {
  if (previewingId.value !== null) return
  const requestId = ++previewRequestId
  previewController = new AbortController()
  previewingId.value = row.id
  previewTitle.value = `发票预览 — ${row.companyName}`
  previewError.value = false
  previewSrc.value = null
  previewVisible.value = true

  try {
    const response = await invoiceApi.previewInvoice(row.id, previewController.signal)
    const blob = response.data
    const objectUrl = URL.createObjectURL(blob)
    if (requestId !== previewRequestId || !previewVisible.value) {
      URL.revokeObjectURL(objectUrl)
      return
    }
    releasePreviewUrl()
    previewSrc.value = objectUrl
  } catch {
    if (requestId === previewRequestId && !previewController?.signal.aborted) {
      previewError.value = true
    }
  } finally {
    if (requestId === previewRequestId) {
      previewController = null
      previewingId.value = null
    }
  }
}

const releasePreviewUrl = () => {
  if (previewSrc.value) {
    URL.revokeObjectURL(previewSrc.value)
    previewSrc.value = null
  }
}

const onPreviewClose = () => {
  previewRequestId += 1
  previewController?.abort()
  previewController = null
  previewingId.value = null
  releasePreviewUrl()
}

const onPreviewClosed = () => {
  previewError.value = false
}

const handleDownload = async (row: Invoice) => {
  try {
    const response = await invoiceApi.downloadInvoice(row.id)
    saveBlobResponse(response, row.fileName || `发票_${row.companyName}_${row.id}`)
    ElMessage.success('下载成功')
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

const showBatchImportDialog = () => {
  batchImportVisible.value = true
}

const handleBatchImportSuccess = async () => {
  await loadInvoices()
}

watch(
  () => [form.companyName, form.taxNumber, form.amount, form.invoiceType, form.remark],
  () => {
    if (!submitting.value) pendingIdempotencyKey.value = null
  }
)

onMounted(() => {
  loadInvoices()
  loadQuota()
})
onBeforeUnmount(onPreviewClose)
</script>

<style scoped>
.panel-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.submit-action-button {
  box-shadow: 0 4px 12px rgba(18, 113, 91, 0.2);
}

.amount-input,
.type-select {
  width: 100%;
}

.batch-import-button {
  color: var(--color-primary);
  background: var(--color-primary-soft);
  border-color: #c8ded7;
}

.batch-import-button:hover,
.batch-import-button:focus-visible {
  color: #fff;
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
}

.result-count {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 7px;
  padding: 6px 10px;
  color: var(--color-text-secondary);
  background: var(--color-primary-soft);
  border: 1px solid #d6e8e2;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 650;
}

.result-count i {
  width: 6px;
  height: 6px;
  background: var(--color-primary);
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgba(18, 113, 91, 0.1);
}

.records-panel {
  overflow: hidden;
}

.records-table :deep(.el-table__row td) {
  transition: background-color 180ms ease;
}

.records-table :deep(.el-table__row:hover td) {
  background: #f5faf8 !important;
}

.invoice-id {
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

.company-cell {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.company-avatar {
  display: grid;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  place-items: center;
  color: var(--color-primary);
  background: linear-gradient(145deg, #edf7f4, #dfeee9);
  border: 1px solid #d7e8e2;
  border-radius: 7px;
  font-size: 13px;
  font-weight: 700;
}

.company-name {
  min-width: 0;
  overflow: hidden;
  color: var(--color-text);
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.status-tag :deep(.el-tag__content) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 6px;
  height: 6px;
  background: currentColor;
  border-radius: 50%;
  box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 12%, transparent);
}

.record-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  white-space: nowrap;
}

.record-actions :deep(.record-action-button.el-button) {
  min-width: 72px;
  min-height: 34px;
  margin: 0;
  padding: 7px 11px;
}

.empty-action {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.empty-action i {
  width: 6px;
  height: 6px;
  background: #aab4b0;
  border-radius: 50%;
}

.mobile-records {
  display: none;
}

.amount-stat {
  font-size: 22px;
}

/* 提交申请弹窗 */
:global(.submit-invoice-dialog) {
  max-width: 520px;
}

:global(.submit-invoice-dialog .el-dialog__body) {
  padding: 16px 24px 8px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* 预览弹窗 */
:global(.invoice-preview-dialog) {
  max-width: 960px;
}

:global(.invoice-preview-dialog .el-dialog__body) {
  padding: 12px 20px 20px;
}

.preview-body {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
}

.preview-image {
  display: block;
  max-width: 100%;
  max-height: 80vh;
  border-radius: 8px;
  object-fit: contain;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: var(--color-text-muted);
  font-size: 14px;
}

.preview-loading .el-icon {
  font-size: 28px;
}

.preview-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: var(--color-text-muted);
  text-align: center;
}

.preview-error-icon {
  font-size: 40px;
  opacity: 0.5;
}

.preview-error p {
  font-size: 14px;
  margin: 0;
}

@media (max-width: 1180px) {
  .user-work-grid {
    grid-template-columns: 1fr;
  }

  .form-panel {
    position: static;
  }
}

@media (max-width: 720px) {
  .records-panel .panel-header {
    align-items: center;
    flex-direction: row;
  }

  .desktop-records {
    display: none;
  }

  .mobile-records {
    display: grid;
    gap: 12px;
    padding: 14px;
    background: #f7f9f8;
  }

  .invoice-record-card {
    padding: 16px;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: 8px;
    box-shadow: 0 5px 16px rgba(24, 39, 34, 0.05);
  }

  .record-card-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    padding-bottom: 14px;
    border-bottom: 1px solid var(--color-border);
  }

  .record-company-copy {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 3px;
  }

  .record-company-copy > span {
    color: var(--color-text-muted);
    font-family: 'SFMono-Regular', Consolas, monospace;
    font-size: 10px;
  }

  .record-card-details {
    display: grid;
    gap: 13px;
    margin: 15px 0;
  }

  .record-card-details > div {
    display: grid;
    grid-template-columns: 76px minmax(0, 1fr);
    align-items: baseline;
    gap: 10px;
  }

  .record-card-details dt {
    color: var(--color-text-muted);
    font-size: 11px;
    font-weight: 600;
  }

  .record-card-details dd {
    min-width: 0;
    margin: 0;
    overflow: hidden;
    color: var(--color-text-secondary);
    font-size: 13px;
    text-align: right;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .record-card-details .money-cell {
    color: var(--color-text);
    font-size: 15px;
  }

  .mobile-record-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .mobile-record-actions :deep(.el-button) {
    width: 100%;
    min-width: 0;
    margin: 0;
  }

  .record-pending-note {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 11px;
    color: #8d641d;
    background: #fff8e8;
    border: 1px solid #f2e3bd;
    border-radius: 6px;
    font-size: 11px;
    line-height: 1.5;
  }

  .record-pending-note svg {
    flex: 0 0 auto;
    width: 15px;
    height: 15px;
  }
}
</style>
