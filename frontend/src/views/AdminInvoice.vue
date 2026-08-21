<template>
  <div class="workspace-shell">
    <AppHeader title="发票管理" />

    <main class="workspace-content">
      <AnimatedContent tag="section" class="stats-grid" :distance="10">
        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon neutral"><Tickets /></span>
            <div class="stat-copy">
              <span class="stat-label">申请总数</span>
              <strong class="stat-value"><CountUp :value="invoices.length" /></strong>
            </div>
            <p class="stat-note">当前全部申请</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon warning"><Clock /></span>
            <div class="stat-copy">
              <span class="stat-label">待处理</span>
              <strong class="stat-value"><CountUp :value="pendingCount" /></strong>
            </div>
            <p class="stat-note">等待上传发票</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon"><CircleCheck /></span>
            <div class="stat-copy">
              <span class="stat-label">已完成</span>
              <strong class="stat-value"><CountUp :value="completedCount" /></strong>
            </div>
            <p class="stat-note">已交付电子凭证</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon"><Wallet /></span>
            <div class="stat-copy">
              <span class="stat-label">申请总金额</span>
              <strong class="stat-value amount-stat"><CountUp :value="totalAmount" :decimals="2" prefix="¥" /></strong>
            </div>
            <p class="stat-note">全部申请合计</p>
          </div>
        </SpotlightCard>
      </AnimatedContent>

      <AnimatedContent tag="section" class="surface-panel" :delay="80">
        <div class="panel-header">
          <div class="panel-heading">
            <span class="panel-heading-icon"><List /></span>
            <div>
              <h2>发票申请</h2>
              <p>审核申请并上传电子发票</p>
            </div>
          </div>

          <div class="table-tools">
            <div class="filter-control">
              <span class="filter-label"><Filter />状态</span>
              <el-select v-model="statusFilter" aria-label="筛选发票状态" class="status-select">
                <el-option label="全部状态" value="ALL" />
                <el-option label="待开票" value="PENDING" />
                <el-option label="已开票" value="COMPLETED" />
              </el-select>
            </div>
            <el-tooltip content="刷新列表" placement="top">
              <el-button class="refresh-button" :icon="RefreshRight" :loading="loading" aria-label="刷新列表" @click="loadInvoices" />
            </el-tooltip>
          </div>
        </div>

        <div v-if="!loading && filteredInvoices.length === 0" class="table-empty-state">
          <span><Files /></span>
          <strong>{{ statusFilter === 'ALL' ? '暂无发票申请' : '当前状态下暂无申请' }}</strong>
        </div>
        <div v-else class="table-scroll">
          <el-table :data="filteredInvoices" v-loading="loading">
            <el-table-column prop="id" label="编号" width="76" />
            <el-table-column prop="companyName" label="公司名称" min-width="190">
              <template #default="{ row }">
                <strong class="company-name">{{ row.companyName }}</strong>
              </template>
            </el-table-column>
            <el-table-column prop="taxNumber" label="税号" min-width="176">
              <template #default="{ row }">
                <span class="tax-number-cell">{{ row.taxNumber }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="amount" label="金额" width="130" align="right">
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
            <el-table-column prop="status" label="状态" width="106" align="center">
              <template #default="{ row }">
                <el-tag class="status-tag" :class="row.status === 'COMPLETED' ? 'is-completed' : 'is-pending'">
                  {{ row.status === 'COMPLETED' ? '已开票' : '待开票' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="申请时间" width="170">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="250" align="center" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 'PENDING'">
                  <!-- 剪贴板粘贴区域 -->
                  <el-tooltip content="点击此处后按 Ctrl+V 粘贴图片" placement="top">
                    <div
                      :id="`paste-zone-${row.id}`"
                      class="paste-zone"
                      tabindex="0"
                      role="button"
                      :aria-label="`为申请 ${row.id} 粘贴发票图片`"
                      :class="{ 'paste-zone--active': pasteActiveId === row.id, 'paste-zone--uploading': uploadingId === row.id }"
                      @click="focusPasteZone(row.id)"
                      @focus="pasteActiveId = row.id"
                      @blur="pasteActiveId = null"
                      @paste="handlePaste($event, row)"
                    >
                      <CopyDocument class="paste-icon" />
                      <span>{{ uploadingId === row.id ? '上传中…' : '粘贴图片' }}</span>
                    </div>
                  </el-tooltip>
                  <!-- 文件选择（备用入口） -->
                  <el-upload
                    :key="`upload-${row.id}`"
                    :show-file-list="false"
                    :before-upload="createUploadHandler(row)"
                    :disabled="uploadingId !== null"
                    accept=".jpg,.jpeg,.png"
                    class="upload-btn-wrapper"
                  >
                    <el-button type="primary" size="small" :icon="UploadFilled" :loading="uploadingId === row.id" plain>
                      选择
                    </el-button>
                  </el-upload>
                </template>
                <template v-else-if="row.downloadable && row.fileExists">
                  <el-button
                    :key="`preview-${row.id}`"
                    type="primary"
                    size="small"
                    :icon="ZoomIn"
                    :loading="previewingId === row.id"
                    @click="handlePreview(row)"
                  >
                    查看
                  </el-button>
                  <el-button
                    :key="`download-${row.id}`"
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
    </main>

    <!-- 发票图片预览弹窗（管理员） -->
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRawFile } from 'element-plus'
import {
  CircleCheck,
  Clock,
  CopyDocument,
  Download,
  Files,
  Filter,
  List,
  Loading,
  PictureRounded,
  RefreshRight,
  Tickets,
  UploadFilled,
  Wallet,
  ZoomIn
} from '@element-plus/icons-vue'
import { invoiceApi, type Invoice } from '@/api/invoice'
import AppHeader from '@/components/AppHeader.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import CountUp from '@/components/bits/CountUp.vue'
import SpotlightCard from '@/components/bits/SpotlightCard.vue'
import { saveBlobResponse } from '@/utils/download'

const loading = ref(false)
const uploadingId = ref<number | null>(null)
const pasteActiveId = ref<number | null>(null)
const invoices = ref<Invoice[]>([])
const statusFilter = ref('ALL')

// 预览状态
const previewVisible = ref(false)
const previewingId = ref<number | null>(null)
const previewSrc = ref<string | null>(null)
const previewTitle = ref('')
const previewError = ref(false)
let previewController: AbortController | null = null
let previewRequestId = 0

const filteredInvoices = computed(() => statusFilter.value !== 'ALL'
  ? invoices.value.filter(invoice => invoice.status === statusFilter.value)
  : invoices.value)
const pendingCount = computed(() => invoices.value.filter(invoice => invoice.status === 'PENDING').length)
const completedCount = computed(() => invoices.value.filter(invoice => invoice.status === 'COMPLETED').length)
const totalAmount = computed(() => invoices.value.reduce((total, invoice) => total + Number(invoice.amount), 0))

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

const loadInvoices = async () => {
  loading.value = true
  try {
    invoices.value = await invoiceApi.getAllInvoices()
  } catch (error) {
    console.error('加载发票列表失败', error)
  } finally {
    loading.value = false
  }
}

const ALLOWED_MIME = ['image/jpeg', 'image/jpg', 'image/png']
const MIME_TO_EXT: Record<string, string> = {
  'image/jpeg': 'jpg',
  'image/jpg': 'jpg',
  'image/png': 'png'
}

const handleUpload = async (row: Invoice, file: File) => {
  if (uploadingId.value !== null || row.status !== 'PENDING') return false

  const isValidType = ALLOWED_MIME.includes(file.type)
  const isLt10M = file.size <= 10 * 1024 * 1024

  if (!isValidType) {
    ElMessage.error('只能上传 JPG、JPEG 或 PNG 图片')
    return false
  }
  if (!isLt10M) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }

  uploadingId.value = row.id
  try {
    await invoiceApi.uploadInvoice(row.id, file)
    ElMessage.success('上传成功')
    await loadInvoices()
  } catch (error) {
    // 错误提示由请求拦截器统一处理，这里重新抛出以确保拦截器能捕获
    throw error
  } finally {
    uploadingId.value = null
  }
  return false
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

const createUploadHandler = (row: Invoice) => (file: UploadRawFile) => handleUpload(row, file)

// 聚焦粘贴区，并记录当前激活行 ID
const focusPasteZone = (id: number) => {
  pasteActiveId.value = id
  const el = document.getElementById(`paste-zone-${id}`)
  el?.focus()
}

// 处理粘贴事件
const handlePaste = async (event: ClipboardEvent, row: Invoice) => {
  if (uploadingId.value !== null) return

  const clipboardData = event.clipboardData
  if (!clipboardData) {
    ElMessage.warning('无法读取剪贴板内容，请使用选择文件上传')
    return
  }

  let clipboardImage: File | null = null
  for (const item of Array.from(clipboardData.items)) {
    if (item.type.startsWith('image/') && ALLOWED_MIME.includes(item.type)) {
      const blob = item.getAsFile()
      if (blob) {
        clipboardImage = blob
        break
      }
    }
  }

  if (!clipboardImage) {
    clipboardImage = Array.from(clipboardData.files)
      .find(file => ALLOWED_MIME.includes(file.type)) || null
  }

  if (!clipboardImage) {
    ElMessage.warning('剪贴板中没有可用的 JPG 或 PNG 图片，请先截图或复制图片')
    return
  }

  event.preventDefault()
  const ext = MIME_TO_EXT[clipboardImage.type] || 'png'
  const imageFile = new File(
    [clipboardImage],
    `clipboard_invoice_${row.id}.${ext}`,
    { type: clipboardImage.type }
  )
  await handleUpload(row, imageFile)
}

onMounted(loadInvoices)

// 预览功能
const releasePreviewUrl = () => {
  if (previewSrc.value) {
    URL.revokeObjectURL(previewSrc.value)
    previewSrc.value = null
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

onBeforeUnmount(onPreviewClose)
</script>

<style scoped>
.table-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-select {
  width: 142px;
}

.refresh-button {
  width: 42px;
  padding: 0;
}

.company-name {
  color: var(--color-text);
  font-weight: 600;
}

.amount-stat {
  font-size: 22px;
}

/* 操作列：粘贴 + 选择按钮并排 */
.paste-zone {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1.5px dashed var(--color-border);
  border-radius: 6px;
  font-size: 12px;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s, color 0.2s;
  outline: none;
  user-select: none;
}

.paste-zone:hover,
.paste-zone--active {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 6%, transparent);
}

.paste-zone--uploading {
  opacity: 0.6;
  pointer-events: none;
}

.paste-icon {
  width: 13px;
  height: 13px;
  flex-shrink: 0;
}

.upload-btn-wrapper {
  display: inline-flex;
}

/* PENDING 行操作区：粘贴 + 选择横向排列，允许换行 */
:deep(.el-table__cell) .paste-zone + .upload-btn-wrapper {
  margin-left: 6px;
}

@media (max-width: 720px) {
  .table-tools {
    width: 100%;
  }

  .filter-control {
    flex: 1;
  }

  .status-select {
    width: auto;
  }
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
</style>
