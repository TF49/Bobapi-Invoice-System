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
      </AnimatedContent>

      <div class="user-work-grid">
        <AnimatedContent tag="section" class="surface-panel form-panel" :delay="80">
          <div class="panel-header">
            <div class="panel-heading">
              <span class="panel-heading-icon"><DocumentAdd /></span>
              <div>
                <h2>提交发票申请</h2>
                <p>填写本次开票信息</p>
              </div>
            </div>
          </div>

          <div class="panel-body">
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
              <el-button class="form-submit" type="primary" :icon="Promotion" :loading="submitting" @click="handleSubmit">
                提交申请
              </el-button>
            </el-form>
          </div>
        </AnimatedContent>

        <AnimatedContent tag="section" class="surface-panel records-panel" :delay="140">
          <div class="panel-header">
            <div class="panel-heading">
              <span class="panel-heading-icon"><List /></span>
              <div>
                <h2>发票记录</h2>
                <p>查看申请进度与电子凭证</p>
              </div>
            </div>
            <span class="result-count">共 {{ invoices.length }} 条</span>
          </div>

          <div v-if="!loading && invoices.length === 0" class="table-empty-state">
            <span><Files /></span>
            <strong>暂无发票记录</strong>
          </div>
          <div v-else class="table-scroll">
            <el-table :data="invoices" v-loading="loading">
              <el-table-column prop="id" label="编号" width="76" />
              <el-table-column prop="companyName" label="公司名称" min-width="180">
                <template #default="{ row }">
                  <strong class="company-name">{{ row.companyName }}</strong>
                </template>
              </el-table-column>
              <el-table-column prop="taxNumber" label="税号" min-width="176">
                <template #default="{ row }">
                  <span class="tax-number-cell">{{ row.taxNumber }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="amount" label="金额" width="136" align="right">
                <template #default="{ row }">
                  <span class="money-cell">{{ formatCurrency(row.amount) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="104" align="center">
                <template #default="{ row }">
                  <el-tag class="status-tag" :class="row.status === 'COMPLETED' ? 'is-completed' : 'is-pending'">
                    {{ row.status === 'COMPLETED' ? '已开票' : '待开票' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="申请时间" width="168">
                <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="180" align="center" fixed="right">
                <template #default="{ row }">
                  <template v-if="row.downloadable && row.fileExists">
                    <el-button
                      type="primary"
                      size="small"
                      :icon="ZoomIn"
                      :loading="previewingId === row.id"
                      @click="handlePreview(row)"
                    >
                      查看
                    </el-button>
                    <el-button
                      type="primary"
                      plain
                      size="small"
                      :icon="Download"
                      style="margin-left: 6px;"
                      @click="handleDownload(row)"
                    >
                      下载
                    </el-button>
                  </template>
                  <span v-else class="empty-action">暂不可用</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </AnimatedContent>
      </div>
    </main>

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
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  CircleCheck,
  Clock,
  DocumentAdd,
  Download,
  Files,
  List,
  Loading,
  OfficeBuilding,
  PictureRounded,
  Postcard,
  Promotion,
  Tickets,
  Wallet,
  ZoomIn
} from '@element-plus/icons-vue'
import { invoiceApi, type Invoice, type InvoiceRequest } from '@/api/invoice'
import AppHeader from '@/components/AppHeader.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import CountUp from '@/components/bits/CountUp.vue'
import SpotlightCard from '@/components/bits/SpotlightCard.vue'
import { saveBlobResponse } from '@/utils/download'

const formRef = ref()
const loading = ref(false)
const submitting = ref(false)
const invoices = ref<Invoice[]>([])
const pendingIdempotencyKey = ref<string | null>(null)

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
  amount: 0.01
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
  ]
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

const handleSubmit = async () => {
  if (submitting.value) return
  submitting.value = true
  try {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return

    const idempotencyKey = pendingIdempotencyKey.value || crypto.randomUUID()
    pendingIdempotencyKey.value = idempotencyKey
    await invoiceApi.createInvoice(form, idempotencyKey)
    ElMessage.success('提交成功')
    pendingIdempotencyKey.value = null
    formRef.value.resetFields()
    await loadInvoices()
  } catch {
    // 保留幂等键，网络失败后再次提交会复用同一请求
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

watch(
  () => [form.companyName, form.taxNumber, form.amount],
  () => {
    if (!submitting.value) pendingIdempotencyKey.value = null
  }
)

onMounted(loadInvoices)
onBeforeUnmount(onPreviewClose)
</script>

<style scoped>
.user-work-grid {
  display: grid;
  grid-template-columns: minmax(310px, 350px) minmax(0, 1fr);
  align-items: start;
  gap: 20px;
}

.form-panel {
  position: sticky;
  top: 20px;
}

.amount-input,
.form-submit {
  width: 100%;
}

.form-submit {
  margin-top: 4px;
}

.result-count {
  flex: 0 0 auto;
  padding: 5px 9px;
  color: var(--color-text-muted);
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
  border-radius: 5px;
  font-size: 11px;
  font-weight: 600;
}

.company-name {
  color: var(--color-text);
  font-weight: 600;
}

.amount-stat {
  font-size: 22px;
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
</style>
