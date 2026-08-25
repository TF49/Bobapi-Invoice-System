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
              <p>{{ userStore.role === 'INVOICE_CLERK' ? '处理申请并上传电子发票' : '审核申请并上传电子发票' }}</p>
            </div>
          </div>

          <div class="table-tools">
            <span class="result-count"><i></i>共 {{ filteredInvoices.length }} 条</span>
            <el-input
              v-model="searchKeyword"
              placeholder="搜索公司、税号或申请人"
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
            <div class="filter-control">
              <span class="filter-label"><User />用户</span>
              <el-select
                v-model="userFilter"
                aria-label="筛选申请用户"
                class="user-select"
                clearable
                placeholder="全部用户"
                filterable
              >
                <el-option
                  v-for="u in userOptions"
                  :key="u.value"
                  :label="u.label"
                  :value="u.value"
                />
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
          <el-table :data="paginatedInvoices" v-loading="loading" class="records-table">
            <el-table-column prop="companyName" label="公司名称" min-width="210">
              <template #default="{ row }">
                <div class="company-cell">
                  <span class="company-avatar">{{ getCompanyInitial(row.companyName) }}</span>
                  <strong class="company-name">{{ row.companyName }}</strong>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="username" label="申请用户" min-width="120">
              <template #default="{ row }">
                <span class="applicant-user-cell">{{ row.username || `用户#${row.userId}` }}</span>
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
            <el-table-column prop="invoiceType" label="开票类型" width="135">
              <template #default="{ row }">
                <el-tag
                  v-if="(row.invoiceType?.trim() || '技术服务费') !== '技术服务费'"
                  size="small"
                  type="danger"
                  effect="plain"
                  class="type-tag warning-type-tag"
                >
                  <el-icon class="warning-icon-inline"><Warning /></el-icon>
                  <span>{{ row.invoiceType }}</span>
                </el-tag>
                <el-tag
                  v-else
                  size="small"
                  type="info"
                  effect="plain"
                  class="type-tag"
                >
                  {{ row.invoiceType || '技术服务费' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.remark?.trim()" class="remark-text remark-warning-text">
                  <el-icon class="warning-icon-inline"><Warning /></el-icon>
                  <span>{{ row.remark }}</span>
                </span>
                <span v-else class="remark-text">-</span>
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
            <el-table-column label="操作" width="290" align="center" fixed="right">
              <template #default="{ row }">
                <div class="action-cell-wrapper">
                  <template v-if="row.status === 'PENDING'">
                    <!-- 剪贴板粘贴区域 -->
                    <el-tooltip content="点击直接粘贴剪贴板中的图片（亦支持 Ctrl+V）" placement="top">
                      <div
                        :id="`paste-zone-${row.id}`"
                        class="paste-zone"
                        tabindex="0"
                        role="button"
                        :aria-label="`粘贴发票图片`"
                        :class="{ 'paste-zone--active': pasteActiveId === row.id, 'paste-zone--uploading': uploadingId === row.id }"
                        @click="handlePasteButtonClick(row)"
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
                      <el-button type="primary" size="small" :icon="UploadFilled" :loading="uploadingId === row.id" plain class="action-btn">
                        选择
                      </el-button>
                    </el-upload>
                  </template>
                  <template v-else-if="row.downloadable && row.fileExists">
                    <el-button
                      :key="`preview-${row.id}`"
                      type="primary"
                      plain
                      size="small"
                      :icon="ZoomIn"
                      :loading="previewingId === row.id"
                      class="action-btn"
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
                      class="action-btn"
                      @click="handleDownload(row)"
                    >
                      下载
                    </el-button>
                    <el-button
                      :key="`copy-${row.id}`"
                      type="primary"
                      plain
                      size="small"
                      :icon="CopyDocument"
                      :loading="copyingId === row.id"
                      class="action-btn"
                      @click="handleCopyImage(row)"
                    >
                      复制
                    </el-button>
                    <span
                      v-if="row.isProcessed"
                      class="processed-row-indicator"
                      title="已标记处理"
                    >✓</span>
                  </template>
                  <span v-else class="empty-action"><i class="empty-dot"></i>暂不可用</span>
                  <!-- 修改按钮（所有行都显示） -->
                  <el-tooltip content="修改发票信息" placement="top">
                    <el-button
                      :key="`edit-${row.id}`"
                      size="small"
                      :icon="EditPen"
                      class="action-btn icon-only-btn"
                      @click="handleEditInvoice(row)"
                    />
                  </el-tooltip>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 移动端卡片视图 -->
        <div v-if="filteredInvoices.length > 0" v-loading="loading" class="mobile-records">
          <article v-for="row in paginatedInvoices" :key="row.id" class="admin-record-card">
            <div class="record-card-header">
              <div class="company-cell">
                <span class="company-avatar">{{ getCompanyInitial(row.companyName) }}</span>
                <strong class="company-name">{{ row.companyName }}</strong>
              </div>
              <el-tag class="status-tag" :class="row.status === 'COMPLETED' ? 'is-completed' : 'is-pending'">
                <i class="status-dot"></i>
                {{ row.status === 'COMPLETED' ? '已开票' : '待开票' }}
              </el-tag>
              <el-tag
                v-if="row.isProcessed"
                class="processed-tag"
                type="success"
                size="small"
                effect="plain"
              >
                <el-icon><Check /></el-icon>
                已处理
              </el-tag>
            </div>

            <dl class="record-card-details">
              <div>
                <dt>申请用户</dt>
                <dd>{{ row.username || `用户#${row.userId}` }}</dd>
              </div>
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
                <dd :class="{ 'warning-invoice-type-text': (row.invoiceType?.trim() || '技术服务费') !== '技术服务费' }">
                  <el-icon v-if="(row.invoiceType?.trim() || '技术服务费') !== '技术服务费'" class="warning-icon-inline"><Warning /></el-icon>
                  {{ row.invoiceType || '技术服务费' }}
                </dd>
              </div>
              <div v-if="row.remark?.trim()">
                <dt>备注</dt>
                <dd class="remark-warning-text">
                  <el-icon class="warning-icon-inline"><Warning /></el-icon>
                  {{ row.remark }}
                </dd>
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
                  @click="handlePasteButtonClick(row)"
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
                <el-button type="primary" plain :icon="CopyDocument" :loading="copyingId === row.id" @click="handleCopyImage(row)">
                  复制图片
                </el-button>
              </template>
              <!-- 移动端修改按钮 -->
              <el-button :icon="EditPen" @click="handleEditInvoice(row)">修改信息</el-button>
            </div>
          </article>
        </div>

        <div v-if="filteredInvoices.length > 0" class="pagination-bar">
          <span>共 {{ filteredInvoices.length }} 条记录</span>
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="filteredInvoices.length"
            layout="sizes, prev, pager, next, jumper"
            background
            @size-change="handlePageSizeChange"
            @current-change="handleCurrentChange"
          />
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
      <template #footer v-if="previewSrc && !previewError">
        <div class="dialog-footer preview-dialog-footer">
          <el-checkbox
            v-model="isCurrentPreviewProcessed"
            :disabled="updatingProcessed"
            @change="handleToggleProcessed"
            class="preview-processed-check"
            :class="{ 'is-done': isCurrentPreviewProcessed }"
          >
            {{ isCurrentPreviewProcessed ? '✓ 已处理' : '标记已处理' }}
          </el-checkbox>
          <el-button type="primary" plain :icon="CopyDocument" @click="handleCopyPreviewImage">复制图片</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 修改发票信息弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      title="修改发票信息"
      width="520px"
      class="edit-invoice-dialog"
      destroy-on-close
      :close-on-click-modal="false"
      @closed="editingRow = null"
    >
      <div v-if="editingRow" class="edit-dialog-meta">
        <span class="edit-meta-label">申请用户：</span>
        <span class="edit-meta-value">{{ editingRow.username || `用户#${editingRow.userId}` }}</span>
        <el-divider direction="vertical" />
        <span class="edit-meta-label">状态：</span>
        <el-tag class="status-tag" :class="editingRow.status === 'COMPLETED' ? 'is-completed' : 'is-pending'" size="small">
          <i class="status-dot"></i>
          {{ editingRow.status === 'COMPLETED' ? '已开票' : '待开票' }}
        </el-tag>
        <template v-if="editingRow.status === 'COMPLETED'">
          <el-divider direction="vertical" />
          <el-tag type="warning" size="small" effect="plain">已开票不可修改金额</el-tag>
        </template>
      </div>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-position="top">
        <el-form-item label="公司名称" prop="companyName">
          <el-input v-model="editForm.companyName" :prefix-icon="OfficeBuilding" placeholder="请输入公司名称" />
        </el-form-item>
        <el-form-item label="税号" prop="taxNumber">
          <el-input
            v-model="editForm.taxNumber"
            :prefix-icon="Postcard"
            placeholder="15-20 位大写字母或数字"
            maxlength="20"
            @input="normalizeEditTaxNumber"
          />
        </el-form-item>
        <el-form-item label="开票金额" prop="amount">
          <el-input-number
            v-model="editForm.amount"
            :min="0.01"
            :max="9999999999.99"
            :precision="2"
            :step="100"
            controls-position="right"
            class="amount-input"
            :disabled="editingRow?.status === 'COMPLETED'"
          />
        </el-form-item>
        <el-form-item label="开票类型" prop="invoiceType">
          <el-select v-model="editForm.invoiceType" placeholder="请选择开票类型" class="type-select">
            <el-option label="技术服务费" value="技术服务费" />
            <el-option label="AI订阅服务费" value="AI订阅服务费" />
            <el-option label="计算服务费" value="计算服务费" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="editForm.remark"
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
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            :icon="Check"
            :loading="editSubmitting"
            @click="handleEditSubmit"
          >
            {{ editSubmitting ? '保存中' : '保存修改' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import type { UploadRawFile } from 'element-plus'
import {
  Check,
  CircleCheck,
  Clock,
  CopyDocument,
  Download,
  EditPen,
  Files,
  Filter,
  List,
  Loading,
  OfficeBuilding,
  PictureRounded,
  Postcard,
  RefreshRight,
  Search,
  Tickets,
  UploadFilled,
  User,
  Wallet,
  Warning,
  ZoomIn
} from '@element-plus/icons-vue'
import { invoiceApi, type Invoice, type InvoiceRequest } from '@/api/invoice'
import AppHeader from '@/components/AppHeader.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import CountUp from '@/components/bits/CountUp.vue'
import SpotlightCard from '@/components/bits/SpotlightCard.vue'
import { useUserStore } from '@/stores/user'
import { saveBlobResponse } from '@/utils/download'
import { copyImageToClipboard } from '@/utils/clipboard'
import { ApiRequestError } from '@/utils/request'

const userStore = useUserStore()
const loading = ref(false)
const uploadingId = ref<number | null>(null)
const copyingId = ref<number | null>(null)
const pasteActiveId = ref<number | null>(null)
const invoices = ref<Invoice[]>([])
const statusFilter = ref('ALL')
const userFilter = ref('')
const searchKeyword = ref('')

// 预览状态
const previewVisible = ref(false)
const previewingId = ref<number | null>(null)
const previewingRow = ref<Invoice | null>(null)
const previewSrc = ref<string | null>(null)
const previewTitle = ref('')
const previewError = ref(false)
let previewController: AbortController | null = null
let previewRequestId = 0

// 已处理标记状态（服务端持久化与实时同步）
const updatingProcessed = ref(false)
const isCurrentPreviewProcessed = computed({
  get: () => Boolean(previewingRow.value?.isProcessed),
  set: (val: boolean) => {
    if (previewingRow.value) {
      previewingRow.value.isProcessed = val
    }
  }
})

const handleToggleProcessed = async (val: boolean | string | number) => {
  if (!previewingRow.value) return
  const row = previewingRow.value
  const targetVal = Boolean(val)
  const originalVal = !targetVal
  updatingProcessed.value = true
  try {
    const updated = await invoiceApi.updateProcessed(row.id, targetVal)
    row.isProcessed = updated.isProcessed
    const targetItem = invoices.value.find(item => item.id === row.id)
    if (targetItem) {
      targetItem.isProcessed = updated.isProcessed
    }
    ElMessage.success(targetVal ? '已标记为已处理' : '已取消处理标记')
  } catch {
    row.isProcessed = originalVal
    const targetItem = invoices.value.find(item => item.id === row.id)
    if (targetItem) {
      targetItem.isProcessed = originalVal
    }
    ElMessage.error('更新处理状态失败，请重试')
  } finally {
    updatingProcessed.value = false
  }
}

const getCompanyInitial = (companyName: string) => companyName.trim().charAt(0) || '企'

const userOptions = computed(() => {
  const seen = new Set<string>()
  const opts: { label: string; value: string }[] = []
  for (const inv of invoices.value) {
    const name = inv.username || `用户#${inv.userId}`
    if (!seen.has(name)) {
      seen.add(name)
      opts.push({ label: name, value: name })
    }
  }
  return opts.sort((a, b) => a.label.localeCompare(b.label, 'zh-CN'))
})

const filteredInvoices = computed(() => {
  return invoices.value.filter(invoice => {
    const matchesStatus = statusFilter.value === 'ALL' || invoice.status === statusFilter.value
    const invoiceUser = invoice.username || `用户#${invoice.userId}`
    const matchesUser = !userFilter.value || invoiceUser === userFilter.value
    const kw = searchKeyword.value.trim().toLowerCase()
    const matchesKeyword = !kw ||
      (invoice.companyName && invoice.companyName.toLowerCase().includes(kw)) ||
      (invoice.taxNumber && invoice.taxNumber.toLowerCase().includes(kw)) ||
      (invoice.username && invoice.username.toLowerCase().includes(kw))
    return matchesStatus && matchesUser && matchesKeyword
  })
})

const page = ref(1)
const pageSize = ref(10)

const paginatedInvoices = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredInvoices.value.slice(start, start + pageSize.value)
})

watch([searchKeyword, statusFilter, userFilter], () => {
  page.value = 1
})

watch(filteredInvoices, (newList) => {
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

const emptyText = computed(() => {
  if (searchKeyword.value.trim()) return '未找到匹配的发票申请记录'
  if (userFilter.value) return `用户「${userFilter.value}」暂无发票申请`
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

const handleCopyImage = async (row: Invoice) => {
  if (copyingId.value !== null) return
  copyingId.value = row.id
  try {
    const response = await invoiceApi.previewInvoice(row.id)
    const blob = response.data

    if (blob.type === 'application/pdf' || row.fileName?.toLowerCase().endsWith('.pdf')) {
      ElMessage.warning('该发票为 PDF 格式，暂不支持直接复制图片，请使用下载功能')
      return
    }

    await copyImageToClipboard(blob)
    ElMessage.success('发票图片已复制到剪贴板')
  } catch (error: any) {
    console.error('复制发票图片失败', error)
    const msg = error?.message || '复制图片失败，请稍后重试'
    ElMessage.error(msg)
  } finally {
    copyingId.value = null
  }
}

const handleCopyPreviewImage = async () => {
  if (!previewSrc.value) return
  try {
    const res = await fetch(previewSrc.value)
    const blob = await res.blob()
    await copyImageToClipboard(blob)
    ElMessage.success('发票图片已复制到剪贴板')
  } catch (error: any) {
    ElMessage.error(error?.message || '复制图片失败，请稍后重试')
  }
}

const createUploadHandler = (row: Invoice) => (file: UploadRawFile) => handleUpload(row, file)

// 聚焦粘贴区，并记录当前激活行 ID
const focusPasteZone = (id: number) => {
  pasteActiveId.value = id
  const el = document.getElementById(`paste-zone-${id}`)
  el?.focus()
}

// 点击"粘贴图片"按钮逻辑：直接从系统剪贴板读取图片并上传
const handlePasteButtonClick = async (row: Invoice) => {
  if (uploadingId.value !== null) return

  focusPasteZone(row.id)

  // 检查浏览器是否支持 Clipboard API 并且能读取剪贴板内容
  if (navigator.clipboard && typeof navigator.clipboard.read === 'function') {
    try {
      const items = await navigator.clipboard.read()
      let clipboardImageBlob: Blob | null = null
      let matchedType = ''

      for (const item of items) {
        for (const type of item.types) {
          if (ALLOWED_MIME.includes(type)) {
            clipboardImageBlob = await item.getType(type)
            matchedType = type
            break
          } else if (type.startsWith('image/')) {
            clipboardImageBlob = await item.getType(type)
            matchedType = type
            break
          }
        }
        if (clipboardImageBlob) break
      }

      if (clipboardImageBlob) {
        const ext = MIME_TO_EXT[matchedType] || 'png'
        const imageFile = new File(
          [clipboardImageBlob],
          `clipboard_invoice_${row.id}.${ext}`,
          { type: matchedType || 'image/png' }
        )
        await handleUpload(row, imageFile)
        return
      } else {
        ElMessage.warning('剪贴板中没有可用的 JPG 或 PNG 图片，请先截图或复制图片')
        return
      }
    } catch (error: any) {
      console.warn('自动读取剪贴板失败，回退到快捷键粘贴模式:', error)
      ElMessage.info('未获得剪贴板权限或自动粘贴受限，请按 Ctrl+V 粘贴图片')
      return
    }
  } else {
    ElMessage.info('已聚焦粘贴区域，请按 Ctrl+V 粘贴图片')
  }
}

// 处理键盘 Ctrl+V 粘贴事件
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

// 修改发票功能
const editDialogVisible = ref(false)
const editingRow = ref<Invoice | null>(null)
const editSubmitting = ref(false)
const editFormRef = ref<FormInstance>()

const editForm = reactive<InvoiceRequest>({
  companyName: '',
  taxNumber: '',
  amount: 0.01,
  invoiceType: '技术服务费',
  remark: ''
})

const editRules = {
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

const normalizeEditTaxNumber = (value: string) => {
  editForm.taxNumber = value.toUpperCase().replace(/[^A-Z0-9]/g, '')
}

const handleEditInvoice = (row: Invoice) => {
  editingRow.value = row
  editForm.companyName = row.companyName
  editForm.taxNumber = row.taxNumber
  editForm.amount = Number(row.amount)
  editForm.invoiceType = row.invoiceType || '技术服务费'
  editForm.remark = row.remark || ''
  editDialogVisible.value = true
}

const handleEditSubmit = async () => {
  if (editSubmitting.value) return
  editSubmitting.value = true
  try {
    const valid = await editFormRef.value?.validate().catch(() => false)
    if (!valid) {
      ElMessage.warning('请检查并完善发票信息')
      return
    }
    const row = editingRow.value!
    await invoiceApi.updateInvoice(row.id, {
      companyName: editForm.companyName.trim(),
      taxNumber: editForm.taxNumber,
      amount: editForm.amount,
      invoiceType: editForm.invoiceType,
      remark: editForm.remark?.trim() || undefined
    })
    ElMessage.success('修改成功')
    editDialogVisible.value = false
    await loadInvoices()
  } catch (error) {
    console.error('修改发票信息失败', error)
    if (!(error instanceof ApiRequestError)) {
      ElMessage.error('修改失败，请稍后重试')
    }
  } finally {
    editSubmitting.value = false
  }
}

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
  previewingRow.value = row
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
  if (!previewVisible.value) {
    previewingRow.value = null
  }
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

.user-select {
  width: 150px;
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

.applicant-user-cell {
  color: var(--color-primary);
  font-weight: 600;
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

.warning-icon-inline {
  margin-right: 3px;
  font-size: 13px;
  vertical-align: -1.5px;
  flex-shrink: 0;
}

.warning-type-tag {
  display: inline-flex;
  align-items: center;
  font-weight: 600;
}

.remark-warning-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  color: var(--color-danger, #c9463d) !important;
  font-weight: 550;
}

.remark-warning-text > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.warning-invoice-type-text {
  display: inline-flex;
  align-items: center;
  color: var(--color-danger, #c9463d) !important;
  font-weight: 600;
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
  gap: 6px;
  white-space: nowrap;
}

.action-cell-wrapper :deep(.el-button),
.action-cell-wrapper .el-button {
  margin-left: 0 !important;
}

.action-cell-wrapper :deep(.action-btn),
.action-cell-wrapper .action-btn {
  height: 28px;
  padding: 0 8px;
  font-size: 12px;
  border-radius: 6px;
}

.action-cell-wrapper :deep(.icon-only-btn),
.action-cell-wrapper .icon-only-btn {
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.paste-zone {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 28px;
  padding: 0 8px;
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

/* 修改发票弹窗 */
:global(.edit-invoice-dialog .el-dialog__body) {
  padding: 8px 20px 16px;
}

.edit-dialog-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  margin-bottom: 16px;
  background: var(--color-primary-soft);
  border: 1px solid #d6e8e2;
  border-radius: 8px;
  font-size: 13px;
  flex-wrap: wrap;
}

.edit-meta-label {
  color: var(--color-text-secondary);
  font-weight: 500;
}

.edit-meta-value {
  color: var(--color-primary);
  font-weight: 600;
}

.amount-input {
  width: 100%;
}

.type-select {
  width: 100%;
}

:global(.invoice-preview-dialog .el-dialog__footer .preview-dialog-footer) {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.preview-processed-check {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.preview-processed-check.is-done :deep(.el-checkbox__label) {
  color: var(--el-color-success);
  font-weight: 500;
}

.preview-processed-check.is-done :deep(.el-checkbox__inner) {
  background-color: var(--el-color-success);
  border-color: var(--el-color-success);
}

.processed-row-indicator {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background-color: var(--el-color-success);
  color: #fff;
  font-size: 11px;
  font-weight: bold;
  flex-shrink: 0;
  cursor: default;
}

.processed-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
}
</style>

