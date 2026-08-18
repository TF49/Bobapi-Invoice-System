<template>
  <div class="user-invoice-container">
    <AppHeader title="我的发票" />

    <el-card class="form-card">
      <template #header>
        <h3>提交发票申请</h3>
      </template>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="公司名称" prop="companyName">
          <el-input v-model="form.companyName" placeholder="请输入公司名称" />
        </el-form-item>
        <el-form-item label="税号" prop="taxNumber">
          <el-input v-model="form.taxNumber" placeholder="请输入税号（15-20位大写字母或数字）" />
        </el-form-item>
        <el-form-item label="开票金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :max="9999999999.99" :precision="2" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            提交申请
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="list-card">
      <template #header>
        <h3>我的发票记录</h3>
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
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button 
              v-if="row.downloadable"
              type="primary" 
              size="small"
              @click="handleDownload(row)"
            >
              下载发票
            </el-button>
            <span v-else style="color: #999; font-size: 12px">暂无可下载</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { invoiceApi, type Invoice, type InvoiceRequest } from '@/api/invoice'
import AppHeader from '@/components/AppHeader.vue'
import { saveBlobResponse } from '@/utils/download'

const formRef = ref()
const loading = ref(false)
const submitting = ref(false)
const invoices = ref<Invoice[]>([])
const pendingIdempotencyKey = ref<string | null>(null)

const form = reactive<InvoiceRequest>({
  companyName: '',
  taxNumber: '',
  amount: 0.01
})

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

onMounted(() => {
  loadInvoices()
})
</script>

<style scoped>
.user-invoice-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.form-card {
  margin-bottom: 20px;
}

h3 {
  margin: 0;
}
</style>
