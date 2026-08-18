<template>
  <div class="admin-invoice-container">
    <AppHeader title="发票管理" />

    <el-card class="list-card">
      <template #header>
        <div class="header">
          <h3>发票管理</h3>
          <el-select v-model="statusFilter" placeholder="筛选状态" @change="loadInvoices" style="width: 150px">
            <el-option label="全部" value="" />
            <el-option label="待开票" value="PENDING" />
            <el-option label="已开票" value="COMPLETED" />
          </el-select>
        </div>
      </template>
      <el-table :data="invoices" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="companyName" label="公司名称" width="200" />
        <el-table-column prop="taxNumber" label="税号" width="150" />
        <el-table-column prop="amount" label="金额" width="120">
          <template #default="{ row }">
            ¥{{ Number(row.amount).toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'warning'">
              {{ row.status === 'COMPLETED' ? '已开票' : '待开票' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" width="180" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-upload
              v-if="row.status === 'PENDING'"
              :key="`upload-${row.id}`"
              :show-file-list="false"
              :before-upload="createUploadHandler(row)"
              accept=".pdf,.jpg,.jpeg,.png"
            >
              <el-button type="primary" size="small">上传发票</el-button>
            </el-upload>
            <el-button
              v-else-if="row.downloadable"
              :key="`download-${row.id}`"
              type="success"
              size="small"
              @click="handleDownload(row)"
            >
              下载发票
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRawFile } from 'element-plus'
import { invoiceApi, type Invoice } from '@/api/invoice'
import AppHeader from '@/components/AppHeader.vue'
import { saveBlobResponse } from '@/utils/download'

const loading = ref(false)
const invoices = ref<Invoice[]>([])
const statusFilter = ref('')

const loadInvoices = async () => {
  loading.value = true
  try {
    const allInvoices = await invoiceApi.getAllInvoices()
    if (statusFilter.value) {
      invoices.value = allInvoices.filter(inv => inv.status === statusFilter.value)
    } else {
      invoices.value = allInvoices
    }
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

  try {
    await invoiceApi.uploadInvoice(row.id, file)
    ElMessage.success('上传成功')
    await loadInvoices()
  } catch (error) {
    console.error('上传失败', error)
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

onMounted(() => {
  loadInvoices()
})
</script>

<style scoped>
.admin-invoice-container {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

h3 {
  margin: 0;
}
</style>
