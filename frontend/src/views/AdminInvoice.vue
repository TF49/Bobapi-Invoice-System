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

      <AnimatedContent tag="section" class="surface-panel records-panel" :delay="80">
        <div class="panel-header">
          <div class="panel-heading">
            <span class="panel-heading-icon"><List /></span>
            <div>
              <h2>发票申请</h2>
              <p>审核申请并上传电子发票</p>
            </div>
          </div>

          <div class="table-tools">
            <span class="result-count"><i></i>共 {{ filteredInvoices.length }} 条</span>
            <el-input
              v-model="searchKeyword"
              placeholder="搜索公司名称或税号"
              :prefix-icon="Search"
              clearable
              class="search-input"
            />
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
          <strong>{{ emptyText }}</strong>
        </div>
        <div v-else class="table-scroll desktop-records">
          <el-table :data="filteredInvoices" v-loading="loading" class="records-table">
            <el-table-column prop="companyName" label="公司名称" min-width="210">
              <template #default="{ row }">
                <div class="company-cell">
                  <span class="company-avatar">{{ getCompanyInitial(row.companyName) }}</span>
                  <strong class="company-name">{{ row.companyName }}</strong>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="taxNumber" label="税号" min-width="180">
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
                <el-tag size="small" type="info" effect="plain" class="type-tag">{{ row.invoiceType || '技术服务费' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="remark-text">{{ row.remark || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag class="status-tag" :class="row.status === 'COMPLETED' ? 'is-completed' : 'is-pending'">
                  <i class="status-dot"></i>
                  {{ row.status === 'COMPLETED' ? '已开票' : '待开票' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="申请时间" width="160">
              <template #default="{ row }">
                <div class="date-cell">
                  <span>{{ formatDateParts(row.createdAt).date }}</span>
                  <small>{{ formatDateParts(row.createdAt).time }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" align="center" fixed="right">
              <template #default="{ row }">
                <div class="action-cell-wrapper">
                  <template v-if="row.status === 'PENDING'">
                    <!-- 剪贴板粘贴区域 -->
                    <el-tooltip content="点击此处后按 Ctrl+V 粘贴图片" placement="top">
                      <div
                        :id="`paste-zone-${row.id}`"
                        class="paste-zone"
                        tabindex="0"
                        role="button"
                        :aria-label="`粘贴发票图片`"
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
                      @click="handleDownload(row)"
                    >
                      下载
                    </el-button>
                  </template>
                  <span v-else class="empty-action"><i class="empty-dot"></i>暂不可用</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 移动端卡片视图 -->
        <div v-if="filteredInvoices.length > 0" v-loading="loading" class="mobile-records">
          <article v-for="row in filteredInvoices" :key="row.id" class="admin-record-card">
            <div class="record-card-header">
              <div class="company-cell">
                <span class="company-avatar">{{ getCompanyInitial(row.companyName) }}</span>
                <strong class="company-name">{{ row.companyName }}</strong>
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
                <dt>金额</dt>
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

            <div class="mobile-card-actions">
              <template v-if="row.status === 'PENDING'">
                <div
                  :id="`paste-zone-m-${row.id}`"
                  class="paste-zone mobile-paste"
                  tabindex="0"
                  role="button"
                  @click="focusPasteZone(row.id)"
                  @paste="handlePaste($event, row)"
                >
                  <CopyDocument class="paste-icon" />
                  <span>粘贴图片</span>
                </div>
                <el-upload
                  :show-file-list="false"
                  :before-upload="createUploadHandler(row)"
                  :disabled="uploadingId !== null"
                  accept=".jpg,.jpeg,.png"
                  class="mobile-upload"
                >
                  <el-button type="primary" :icon="UploadFilled" :loading="uploadingId === row.id">
                    选择文件
                  </el-button>
                </el-upload>
              </template>
              <template v-else-if="row.downloadable && row.fileExists">
                <el-button type="primary" :icon="ZoomIn" :loading="previewingId === row.id" @click="handlePreview(row)">
                  查看发票
                </el-button>
                <el-button type="primary" plain :icon="Download" @click="handleDownload(row)">
                  下载文件
                </el-button>
              </template>
            </div>
          </article>
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
  Search,
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
const searchKeyword = ref('')

// 预览状态
const previewVisible = ref(false)
const previewingId = ref<number | null>(null)
const previewSrc = ref<string | null>(null)
const previewTitle = ref('')
const previewError = ref(false)
let previewController: AbortController | null = null
let previewRequestId = 0

const getCompanyInitial = (companyName: string) => companyName.trim().charAt(0) || '企'

const filteredInvoices = computed(() => {
  return invoices.value.filter(invoice => {
    const matchesStatus = statusFilter.value === 'ALL' || invoice.status === statusFilter.value
    const kw = searchKeyword.value.trim().toLowerCase()
    const matchesKeyword = !kw ||
      (invoice.companyName && invoice.companyName.toLowerCase().includes(kw)) ||
      (invoice.taxNumber && invoice.taxNumber.toLowerCase().includes(kw))
    return matchesStatus && matchesKeyword
  })
})

const emptyText = computed(() => {
  if (searchKeyword.value.trim()) return '未找到匹配的发票申请记录'
  if (statusFilter.value !== 'ALL') return '当前状态下暂无发票申请'
  return '暂无发票申请'
})

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

const formatDateParts = (value: string) => {
  const formatted = formatDate(value)
  const separatorIndex = formatted.lastIndexOf(' ')
  if (separatorIndex < 0) return { date: formatted, time: '' }
  return {
    date: formatted.slice(0, separatorIndex),
    time: formatted.slice(separatorIndex + 1)
  }
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
.records-panel {
  overflow: hidden;
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
  padding: 5px 11px;
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
  width: 200px;
}

.status-select {
  width: 130px;
}

.refresh-button {
  width: 38px;
  height: 38px;
  padding: 0;

}

.records-table :deep(.el-table__row td) {
  transition: background-color 180ms ease;
}

.records-table :deep(.el-table__row:hover td) {
  background: #f5faf8 !important;
}

.company-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
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
  color: var(--color-text);
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.amount-stat {
  font-size: 22px;
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
  box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 14%, transparent);
}

.action-cell-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.paste-zone {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 32px;
  padding: 0 10px;
  border: 1.5px dashed var(--color-border-strong);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
  background: #fafcfb;
  cursor: pointer;
  transition: all 0.2s ease;
  outline: none;
  user-select: none;
  white-space: nowrap;
}

.paste-zone:hover,
.paste-zone:focus-visible,
.paste-zone--active {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  background: var(--color-primary-soft);
}

.paste-zone--uploading {
  opacity: 0.6;
  pointer-events: none;
}

.paste-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.upload-btn-wrapper {
  display: inline-flex;
}

.empty-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.empty-dot {
  width: 6px;
  height: 6px;
  background: #aab4b0;
  border-radius: 50%;
}

.mobile-records {
  display: none;
}

@media (max-width: 768px) {
  .desktop-records {
    display: none;
  }

  .mobile-records {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 16px;
  }

  .admin-record-card {
    padding: 16px;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
  }

  .record-card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 12px;
    margin-bottom: 12px;
    border-bottom: 1px dashed var(--color-border);
  }

  .record-card-details {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 10px 14px;
    margin: 0 0 14px 0;
  }

  .record-card-details dt {
    color: var(--color-text-muted);
    font-size: 12px;
  }

  .record-card-details dd {
    margin: 2px 0 0 0;
    color: var(--color-text);
    font-size: 13px;
    font-weight: 500;
  }

  .mobile-card-actions {
    display: flex;
    gap: 10px;
  }

  .mobile-card-actions .el-button,
  .mobile-card-actions .mobile-upload {
    flex: 1;
  }

  .mobile-card-actions .mobile-paste {
    flex: 1;
    height: 38px;
  }
}

@media (max-width: 720px) {
  .table-tools {
    width: 100%;
  }

  .search-input {
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

