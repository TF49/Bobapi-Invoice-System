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
      @closed="resetAiParseState"
    >
      <!-- AI 智能识别栏 -->
      <div class="ai-parse-card" :class="{ 'is-open': aiParseExpanded }">
        <div class="ai-parse-header" @click="aiStep === 'idle' && (aiParseExpanded = !aiParseExpanded)">
          <div class="ai-parse-title">
            <el-icon class="ai-sparkle-icon"><MagicStick /></el-icon>
            <span>AI 智能识别自动填单</span>
            <el-tag size="small" type="success" effect="plain" class="ai-tag">智能提取</el-tag>
          </div>
          <el-button link type="primary" size="small" class="ai-toggle-btn" :disabled="aiStep !== 'idle'">
            {{ aiParseExpanded ? '收起' : '展开文本识别' }}
          </el-button>
        </div>

        <div v-show="aiParseExpanded" class="ai-parse-body">
          <p class="ai-parse-hint">直接粘贴包含发票的文本信息，AI 将自动识别公司名称、税号、开票金额：</p>
          <el-input
            v-model="aiRawText"
            type="textarea"
            :rows="3"
            placeholder="例如：公司名称：北京某某科技有限公司，税号：91110108MA01XXXXXX，金额：1500.00元..."
            maxlength="2000"
            show-word-limit
            :disabled="aiStep !== 'idle'"
          />

          <!-- 进度条（AI 处理中） -->
          <div v-if="aiStep !== 'idle'" class="ai-progress-container">
            <div class="ai-progress-steps">
              <div class="ai-step-item" :class="aiStep === 'extracting' ? 'is-active' : 'is-done'">
                <span class="ai-step-dot">
                  <el-icon v-if="aiStep === 'extracting'" class="is-loading"><Loading /></el-icon>
                  <el-icon v-else class="ai-step-check"><Check /></el-icon>
                </span>
                <span class="ai-step-label">AI 提取中</span>
              </div>
              <span class="ai-step-line" :class="{ 'is-active': aiStep === 'verifying' }"></span>
              <div class="ai-step-item" :class="aiStep === 'verifying' ? 'is-active' : 'is-pending'">
                <span class="ai-step-dot">
                  <el-icon v-if="aiStep === 'verifying'" class="is-loading"><Loading /></el-icon>
                  <span v-else class="ai-step-number">2</span>
                </span>
                <span class="ai-step-label">AI 审核中</span>
              </div>
            </div>
            <el-progress
              :percentage="Math.min(100, Math.max(0, Math.round(aiProgress)))"
              :stroke-width="5"
              :show-text="false"
              color="#059669"
              class="ai-progress-bar"
            />
          </div>

          <!-- 操作按鈕（空闲时） -->
          <div v-else class="ai-parse-actions">
            <el-button size="small" :disabled="!aiRawText" @click="aiRawText = ''">
              清空
            </el-button>
            <el-button
              type="primary"
              size="small"
              :icon="MagicStick"
              :disabled="!aiRawText.trim()"
              @click="handleAiParse"
            >
              识别并填充表单
            </el-button>
          </div>
        </div>
      </div>

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

    <!-- AI 识别结果确认弹窗 -->
    <el-dialog
      v-model="aiConfirmVisible"
      title="AI 识别结果确认"
      width="420px"
      class="ai-confirm-dialog"
      :close-on-click-modal="false"
      :append-to-body="true"
    >
      <div class="ai-confirm-body">
        <div class="ai-confirm-intro">
          <el-icon class="ai-confirm-sparkle"><MagicStick /></el-icon>
          <span>AI 已完成二次核查，请确认以下信息无误后提交</span>
        </div>
        <dl class="ai-confirm-fields">
          <div class="ai-confirm-row">
            <dt>公司名称</dt>
            <dd>{{ aiConfirmData?.companyName || '-' }}</dd>
          </div>
          <div class="ai-confirm-row">
            <dt>税号</dt>
            <dd class="ai-confirm-code">{{ aiConfirmData?.taxNumber || '-' }}</dd>
          </div>
          <div class="ai-confirm-row">
            <dt>开票金额</dt>
            <dd class="ai-confirm-money">{{ aiConfirmData?.amount != null ? formatCurrency(aiConfirmData.amount) : '-' }}</dd>
          </div>
          <div class="ai-confirm-row">
            <dt>开票类型</dt>
            <dd><el-tag size="small" type="info" effect="plain">技术服务费</el-tag></dd>
          </div>
          <div class="ai-confirm-row ai-confirm-quota-row">
            <dt>预扣额度</dt>
            <dd class="ai-confirm-quota-value">
              {{ aiConfirmData?.amount != null ? formatCurrency(aiConfirmData.amount) : '-' }}
              <el-tag
                v-if="aiConfirmData?.amount != null && aiConfirmData.amount > quotaBalance"
                type="danger"
                size="small"
                effect="dark"
              >额度不足</el-tag>
            </dd>
          </div>
        </dl>
        <div
          v-if="aiConfirmData?.amount != null && aiConfirmData.amount > quotaBalance"
          class="ai-confirm-warning"
        >
          <el-icon><Warning /></el-icon>
          当前可用额度 {{ formatCurrency(quotaBalance) }}，额度不足，请联系管理员充値后再提交
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="aiConfirmVisible = false">重新识别</el-button>
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="submitting"
            :disabled="aiConfirmData?.amount != null && aiConfirmData.amount > quotaBalance"
            @click="handleAiConfirm"
          >
            确认并提交
          </el-button>
        </div>
      </template>
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
  Check,
  CircleCheck,
  Clock,
  Coin,
  Download,
  Files,
  List,
  Loading,
  MagicStick,
  OfficeBuilding,
  PictureRounded,
  Plus,
  Postcard,
  Promotion,
  Tickets,
  Upload,
  Wallet,
  Warning,
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

// 提交申请弹窗与 AI 识别状态
const submitDialogVisible = ref(false)
const aiParseExpanded = ref(false)
const aiRawText = ref('')
const aiStep = ref<'idle' | 'extracting' | 'verifying'>('idle')
const aiProgress = ref(0)
const aiConfirmVisible = ref(false)
const aiConfirmData = ref<{
  companyName: string | null
  taxNumber: string | null
  amount: number | null
  invoiceType: string
} | null>(null)
let rafId: number | null = null
/** 当前动画的 resolve 函数，用于在 resetAiParseState 中翟时解封 Promise，防止异步函数永久挂起 */
let currentAnimationResolver: (() => void) | null = null
/** 会话 ID：每次 resetAiParseState 自增，防止诎期 API 响应在重置后弹出确认框 */
let aiParseSessionId = 0

const showSubmitDialog = () => {
  submitDialogVisible.value = true
}

const resetAiParseState = () => {
  aiParseSessionId++                              // 使所有进行中的 handleAiParse 尽快退出
  if (rafId !== null) { cancelAnimationFrame(rafId); rafId = null }
  if (currentAnimationResolver) { currentAnimationResolver(); currentAnimationResolver = null } // 翟时解封成2 Promise
  aiParseExpanded.value = false
  aiRawText.value = ''
  aiStep.value = 'idle'
  aiProgress.value = 0
  aiConfirmVisible.value = false
  aiConfirmData.value = null
}

/** requestAnimationFrame 驱动的进度条平滑动画 */
const animateProgress = (from: number, to: number, durationMs: number): Promise<void> => {
  // 如果有正在进行的动画，先解封其 Promise（避免挂起）
  if (rafId !== null) { cancelAnimationFrame(rafId); rafId = null }
  if (currentAnimationResolver) { currentAnimationResolver(); currentAnimationResolver = null }
  return new Promise(resolve => {
    currentAnimationResolver = resolve
    aiProgress.value = from
    const startTime = Date.now()
    const tick = () => {
      const elapsed = Date.now() - startTime
      const t = durationMs <= 0 ? 1 : Math.min(1, elapsed / durationMs)
      aiProgress.value = from + (to - from) * t
      if (t < 1) {
        rafId = requestAnimationFrame(tick)
      } else {
        rafId = null
        currentAnimationResolver = null
        resolve()
      }
    }
    rafId = requestAnimationFrame(tick)
  })
}

const handleAiParse = async () => {
  const text = aiRawText.value.trim()
  if (!text) {
    ElMessage.warning('请先输入或粘贴包含发票信息的文本')
    return
  }

  const sessionId = aiParseSessionId // 捕获当前会话，用于检测是否被重置

  aiStep.value = 'extracting'
  aiProgress.value = 0

  try {
    // 第一阶段：提取（动画 0→45% 与 API 调用并行，取较慢者）
    const [res1] = await Promise.all([
      invoiceApi.parseInvoiceText(text),
      animateProgress(0, 45, 1500)
    ])
    if (aiParseSessionId !== sessionId) return // 弹窗已关闭，丢弃结果

    if (!res1.companyName && !res1.taxNumber && res1.amount === null) {
      aiStep.value = 'idle'
      aiProgress.value = 0
      ElMessage.warning(res1.hint || '未能从文本中识别出发票相关信息，请手动填写')
      return
    }

    // 第二阶段：审核（动画 50→90% 与 API 调用并行）
    aiStep.value = 'verifying'
    aiProgress.value = 50

    const [res2] = await Promise.all([
      invoiceApi.verifyInvoiceText(text, {
        companyName: res1.companyName,
        taxNumber: res1.taxNumber,
        amount: res1.amount !== null ? Number(res1.amount) : null
      }),
      animateProgress(50, 90, 1500)
    ])
    if (aiParseSessionId !== sessionId) return // 弹窗已关闭，丢弃结果

    // 完成动画至 100%
    await animateProgress(aiProgress.value, 100, 300)
    if (aiParseSessionId !== sessionId) return
    await new Promise<void>(r => setTimeout(r, 200))
    if (aiParseSessionId !== sessionId) return

    // 合并结果：优先使用审核结果，回退到提取结果
    const companyName = res2.companyName || res1.companyName || null
    const taxNumber   = res2.taxNumber   || res1.taxNumber   || null
    const rawAmount   = res2.amount      ?? res1.amount
    const amount = rawAmount !== null
      ? (() => {
          const n = Math.round(Number(rawAmount) * 100) / 100
          return Number.isFinite(n) && n >= 0.01 ? n : null
        })()
      : null

    aiStep.value = 'idle'
    aiProgress.value = 0
    aiConfirmData.value = { companyName, taxNumber, amount, invoiceType: '技术服务费' }
    aiConfirmVisible.value = true

  } catch (error: any) {
    aiStep.value = 'idle'
    if (rafId !== null) { cancelAnimationFrame(rafId); rafId = null }
    aiProgress.value = 0
    console.error('AI 识别发票信息失败', error)
    if (!(error instanceof ApiRequestError)) {
      ElMessage.error(error.message || 'AI 识别失败，请稍后重试或手动输入')
    }
  }
}

/** 用户在确认弹窗中点击「确认并提交」 */
const handleAiConfirm = async () => {
  const data = aiConfirmData.value
  if (!data) return

  // 将核查结果填入表单
  if (data.companyName) form.companyName = data.companyName.trim()
  if (data.taxNumber)   form.taxNumber   = data.taxNumber.toUpperCase().replace(/[^A-Z0-9]/g, '')
  if (data.amount !== null && Number.isFinite(data.amount) && data.amount >= 0.01) {
    form.amount = data.amount
  }
  form.invoiceType = '技术服务费'

  // 关闭确认弹窗并清理数据
  aiConfirmVisible.value = false
  aiConfirmData.value = null

  // 直接触发提交
  await handleSubmit()
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
onBeforeUnmount(() => {
  onPreviewClose()
  if (rafId !== null) { cancelAnimationFrame(rafId); rafId = null }
})
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

/* AI 智能识别卡片 */
.ai-parse-card {
  margin-bottom: 16px;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.04) 0%, rgba(6, 95, 70, 0.06) 100%);
  border: 1px dashed rgba(16, 185, 129, 0.35);
  border-radius: 8px;
  overflow: hidden;
  transition: all 0.2s ease;
}

.ai-parse-card.is-open {
  border-style: solid;
  border-color: rgba(16, 185, 129, 0.5);
  background: #f8fdfa;
}

.ai-parse-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
}

.ai-parse-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #065f46;
}

.ai-sparkle-icon {
  font-size: 15px;
  color: #059669;
}

.ai-tag {
  font-size: 11px;
  height: 20px;
  padding: 0 6px;
}

.ai-toggle-btn {
  font-size: 12px;
  padding: 0;
}

.ai-parse-body {
  padding: 0 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-parse-hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-muted, #6b7280);
  line-height: 1.4;
}

.ai-parse-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* AI 两阶段进度条 */
.ai-progress-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 0;
}

.ai-progress-steps {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-step-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-muted);
  transition: color 0.25s;
  white-space: nowrap;
}

.ai-step-item.is-active { color: #059669; }
.ai-step-item.is-done   { color: #059669; opacity: 0.75; }

.ai-step-dot {
  display: grid;
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  place-items: center;
  background: #f0faf6;
  border: 1.5px solid #d0e9df;
  border-radius: 50%;
  font-size: 11px;
  transition: all 0.25s;
}

.ai-step-item.is-active .ai-step-dot {
  background: #059669;
  border-color: #059669;
  color: #fff;
}

.ai-step-item.is-done .ai-step-dot {
  background: #d1fae5;
  border-color: #6ee7b7;
  color: #059669;
}

.ai-step-check { font-size: 11px; }

.ai-step-number {
  font-size: 10px;
  font-weight: 700;
}

.ai-step-line {
  flex: 1;
  height: 1.5px;
  background: #e0ede8;
  border-radius: 1px;
  transition: background 0.3s;
}

.ai-step-line.is-active { background: #059669; }

.ai-progress-bar :deep(.el-progress-bar__outer) {
  background: #e8f5f0;
  border-radius: 99px;
}

/* AI 识别结果确认弹窗 */
:global(.ai-confirm-dialog) {
  max-width: 420px;
}

:global(.ai-confirm-dialog .el-dialog__body) {
  padding: 4px 24px 16px;
}

.ai-confirm-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ai-confirm-intro {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.06), rgba(6, 95, 70, 0.08));
  border: 1px solid rgba(16, 185, 129, 0.2);
  border-radius: 8px;
  font-size: 13px;
  color: #065f46;
  line-height: 1.4;
}

.ai-confirm-sparkle {
  font-size: 16px;
  color: #059669;
  flex-shrink: 0;
}

.ai-confirm-fields {
  margin: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}

.ai-confirm-row {
  display: grid;
  grid-template-columns: 76px 1fr;
  align-items: center;
  gap: 12px;
  padding: 11px 16px;
  border-bottom: 1px solid var(--color-border);
}

.ai-confirm-row:last-child { border-bottom: none; }

.ai-confirm-row dt {
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 600;
}

.ai-confirm-row dd {
  margin: 0;
  color: var(--color-text);
  font-size: 13px;
  font-weight: 500;
  word-break: break-all;
}

.ai-confirm-code {
  font-family: 'SFMono-Regular', Consolas, monospace !important;
  font-size: 12px !important;
  letter-spacing: 0.04em;
}

.ai-confirm-money {
  font-size: 17px !important;
  font-weight: 700 !important;
  color: var(--color-text) !important;
}

.ai-confirm-quota-row {
  background: linear-gradient(90deg, #f8fffe, #f0faf7);
}

.ai-confirm-quota-value {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px !important;
  font-weight: 700 !important;
  color: #059669 !important;
}

.ai-confirm-warning {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  color: #dc2626;
  font-size: 12px;
  line-height: 1.5;
}

.ai-confirm-warning .el-icon {
  flex-shrink: 0;
  margin-top: 1px;
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
