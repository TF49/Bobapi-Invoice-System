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
            <el-table-column prop="amount" label="金额" width="142" align="right">
              <template #default="{ row }">
                <span class="money-cell">{{ formatCurrency(row.amount) }}</span>
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
            <el-table-column label="操作" width="150" align="center" fixed="right">
              <template #default="{ row }">
                <el-upload
                  v-if="row.status === 'PENDING'"
                  :key="`upload-${row.id}`"
                  :show-file-list="false"
                  :before-upload="createUploadHandler(row)"
                  accept=".pdf,.jpg,.jpeg,.png"
                >
                  <el-button type="primary" size="small" :icon="UploadFilled" :loading="uploadingId === row.id">
                    上传
                  </el-button>
                </el-upload>
                <el-button
                  v-else-if="row.downloadable"
                  :key="`download-${row.id}`"
                  type="primary"
                  plain
                  size="small"
                  :icon="Download"
                  @click="handleDownload(row)"
                >
                  下载
                </el-button>
                <span v-else class="empty-action">暂不可用</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </AnimatedContent>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRawFile } from 'element-plus'
import {
  CircleCheck,
  Clock,
  Download,
  Files,
  Filter,
  List,
  RefreshRight,
  Tickets,
  UploadFilled,
  Wallet
} from '@element-plus/icons-vue'
import { invoiceApi, type Invoice } from '@/api/invoice'
import AppHeader from '@/components/AppHeader.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import CountUp from '@/components/bits/CountUp.vue'
import SpotlightCard from '@/components/bits/SpotlightCard.vue'
import { saveBlobResponse } from '@/utils/download'

const loading = ref(false)
const uploadingId = ref<number | null>(null)
const invoices = ref<Invoice[]>([])
const statusFilter = ref('ALL')

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

const handleUpload = async (row: Invoice, file: File) => {
  const isValidType = ['application/pdf', 'image/jpeg', 'image/jpg', 'image/png'].includes(file.type)
  const isLt10M = file.size / 1024 / 1024 < 10

  if (!isValidType) {
    ElMessage.error('只能上传 PDF 或图片文件')
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
    console.error('上传失败', error)
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

onMounted(loadInvoices)
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
</style>
