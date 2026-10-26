<template>
  <div class="user-document-page">
    <AppHeader title="证明文件盖章管理" />

    <main class="main-content">
      <!-- 顶部盖章钱包卡片与概览 -->
      <section class="overview-grid">
        <div class="stat-card balance-card">
          <div class="stat-header">
            <span class="stat-icon-badge">
              <el-icon><Wallet /></el-icon>
            </span>
            <div class="stat-meta">
              <span class="stat-label">盖章钱包可用额度</span>
              <div class="balance-display">
                <span class="currency-symbol">¥</span>
                <span class="balance-number">{{ formatMoney(sealQuota.balance) }}</span>
              </div>
            </div>
          </div>
          <div class="stat-footer">
            <div class="quota-count-tag">
              可盖章 <strong>{{ sealQuota.availableCount || 0 }}</strong> 次（10元/次）
            </div>
            <el-button type="primary" size="default" :icon="Plus" class="recharge-btn" @click="openRechargeDialog">
              充值盖章额度
            </el-button>
          </div>
        </div>

        <div class="stat-card mini-stat">
          <div class="mini-header">
            <el-icon class="icon-pending"><Clock /></el-icon>
            <span class="stat-label">待盖章交付</span>
          </div>
          <div class="stat-value text-warning">{{ pendingCount }}</div>
          <div class="stat-hint">发票方处理中</div>
        </div>

        <div class="stat-card mini-stat">
          <div class="mini-header">
            <el-icon class="icon-success"><CircleCheck /></el-icon>
            <span class="stat-label">已盖章交付</span>
          </div>
          <div class="stat-value text-success">{{ completedCount }}</div>
          <div class="stat-hint">支持随时下载与查验</div>
        </div>

        <div class="stat-card mini-stat">
          <div class="mini-header">
            <el-icon class="icon-info"><DocumentChecked /></el-icon>
            <span class="stat-label">累计盖章支出</span>
          </div>
          <div class="stat-value">¥ {{ formatMoney(sealQuota.totalDeducted) }}</div>
          <div class="stat-hint">共计扣费记录</div>
        </div>
      </section>

      <!-- 核心直接上传区域（极简无表单） -->
      <section class="upload-section-card">
        <div class="section-heading">
          <div>
            <h2>极简上传 · 一键送盖</h2>
            <p class="section-desc">无需填写复杂表单，直接上传需盖章的证明原件，自动扣除 10 元额度，发票方盖章后将上传交付。</p>
          </div>
          <div class="fee-badge">
            <el-tag type="success" size="large" effect="light" round>
              单次盖章仅需 <strong>¥10.00</strong>
            </el-tag>
          </div>
        </div>

        <el-upload
          class="direct-document-uploader"
          drag
          action="#"
          :auto-upload="false"
          :show-file-list="false"
          :on-change="handleFileSelected"
          :disabled="isUploading"
        >
          <div class="uploader-inner">
            <div class="upload-icon-circle">
              <el-icon v-if="!isUploading"><UploadFilled /></el-icon>
              <el-icon v-else class="is-loading"><Loading /></el-icon>
            </div>
            <div class="upload-prompt">
              <span v-if="!isUploading" class="primary-text">点击选择文件，或将证明原件拖拽到此处</span>
              <span v-else class="primary-text">正在上传文件并扣减额度，请稍候...</span>
              <span class="sub-text">支持任意格式文件（PDF、Word、Excel、图片等均可），无需任何表单直接提交</span>
            </div>
          </div>
        </el-upload>
      </section>

      <!-- 工单列表区域 -->
      <section class="table-card">
        <div class="table-header">
          <div class="table-title-group">
            <h3>我的盖章记录</h3>
            <span class="total-tag">共 {{ documents.length }} 笔</span>
          </div>
          <div class="table-filters">
            <el-radio-group v-model="filterStatus" size="default" @change="fetchDocuments">
              <el-radio-button label="ALL">全部</el-radio-button>
              <el-radio-button label="PENDING">待盖章</el-radio-button>
              <el-radio-button label="COMPLETED">已交付</el-radio-button>
              <el-radio-button label="REJECTED">已驳回</el-radio-button>
              <el-radio-button label="CANCELLED">已取消</el-radio-button>
            </el-radio-group>
            <el-button :icon="Refresh" circle @click="refreshAll" />
          </div>
        </div>

        <el-table
          v-loading="loadingList"
          :data="documents"
          stripe
          class="document-table"
          empty-text="暂无证明文件盖章记录"
        >
          <el-table-column label="ID" prop="id" width="80" align="center" />
          
          <el-table-column label="待盖章原文件" min-width="220">
            <template #default="{ row }">
              <div class="file-name-cell">
                <el-icon class="file-icon"><Document /></el-icon>
                <div class="file-info-group">
                  <span class="file-title" :title="row.sourceFileName">{{ row.sourceFileName }}</span>
                  <span class="file-size-hint">{{ formatFileSize(row.sourceFileSize) }}</span>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="服务费" width="100" align="center">
            <template #default="{ row }">
              <span class="fee-text">¥ {{ formatMoney(row.feeAmount) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="提交时间" width="165" align="center">
            <template #default="{ row }">
              <span>{{ formatDateTime(row.createdAt) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="工单状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'PENDING'" type="warning" effect="dark" round>待盖章</el-tag>
              <el-tag v-else-if="row.status === 'COMPLETED'" type="success" effect="dark" round>已完成</el-tag>
              <el-tag v-else-if="row.status === 'REJECTED'" type="danger" effect="light" round>已驳回</el-tag>
              <el-tag v-else-if="row.status === 'CANCELLED'" type="info" effect="plain" round>已取消</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="交付成品文件" min-width="220">
            <template #default="{ row }">
              <div v-if="row.status === 'COMPLETED' && row.sealedFileName" class="sealed-file-cell">
                <el-icon class="sealed-icon"><CircleCheck /></el-icon>
                <span class="sealed-name" :title="row.sealedFileName">{{ row.sealedFileName }}</span>
              </div>
              <div v-else-if="row.status === 'REJECTED'" class="reject-reason-cell">
                <el-tooltip :content="row.rejectReason || '未提供具体原因'" placement="top">
                  <span class="reject-text">原因: {{ row.rejectReason || '已驳回退款' }}</span>
                </el-tooltip>
              </div>
              <div v-else-if="row.status === 'CANCELLED'" class="cancelled-hint">
                <span>用户已撤销，款项已退回</span>
              </div>
              <div v-else class="pending-hint">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>发票方盖章中...</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="220" fixed="right" align="center">
            <template #default="{ row }">
              <!-- 待盖章状态：支持撤销申请 -->
              <div v-if="row.status === 'PENDING'" class="actions-group">
                <el-button
                  type="primary"
                  link
                  size="small"
                  :icon="Download"
                  @click="handleDownloadSource(row)"
                >
                  原件
                </el-button>
                <el-popconfirm
                  title="撤销后将自动将 10 元服务费退回至您的盖章钱包，确认撤销吗？"
                  confirm-button-text="确认撤销"
                  cancel-button-text="取消"
                  confirm-button-type="danger"
                  @confirm="handleCancelDocument(row)"
                >
                  <template #reference>
                    <el-button type="danger" link size="small" :icon="CloseBold">
                      撤销
                    </el-button>
                  </template>
                </el-popconfirm>
              </div>

              <!-- 已完成状态：支持下载交付文件、在线预览 -->
              <div v-else-if="row.status === 'COMPLETED'" class="actions-group">
                <el-button
                  type="primary"
                  size="small"
                  :icon="View"
                  @click="handlePreviewSealed(row)"
                >
                  预览
                </el-button>
                <el-button
                  type="success"
                  size="small"
                  :icon="Download"
                  @click="handleDownloadSealed(row)"
                >
                  下载交付
                </el-button>
              </div>

              <!-- 其他状态：允许下载原始备份 -->
              <div v-else class="actions-group">
                <el-button
                  type="info"
                  link
                  size="small"
                  :icon="Download"
                  @click="handleDownloadSource(row)"
                >
                  原件备份
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </main>

    <!-- 充值盖章额度弹窗 -->
    <el-dialog
      v-model="showRechargeDialog"
      title="充值盖章钱包额度"
      width="480px"
      :close-on-click-modal="false"
      destroy-on-close
      class="recharge-dialog"
    >
      <el-form
        ref="rechargeFormRef"
        :model="rechargeForm"
        :rules="rechargeRules"
        label-position="top"
        @submit.prevent="handleSubmitRecharge"
      >
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="recharge-intro-alert"
        >
          <template #title>
            <span>每次盖章固定消耗 <strong>10.00 元</strong>。充值金额将 1:1 存入您的独立盖章钱包中，工单驳回或撤销时全额自动退还。</span>
          </template>
        </el-alert>

        <el-form-item label="充值金额 (元)" prop="feeAmount" style="margin-top: 16px;">
          <div class="quick-amounts">
            <el-button
              v-for="amt in [50, 100, 200, 500]"
              :key="amt"
              size="small"
              :type="rechargeForm.feeAmount === amt ? 'primary' : 'default'"
              @click="rechargeForm.feeAmount = amt"
            >
              ¥{{ amt }}（{{ amt / 10 }}次）
            </el-button>
          </div>
          <el-input-number
            v-model="rechargeForm.feeAmount"
            :min="10"
            :max="29999"
            :step="10"
            :precision="2"
            controls-position="right"
            style="width: 100%; margin-top: 8px;"
            placeholder="请输入充值金额"
          />
          <span class="form-hint">当前金额预计可盖章 <strong>{{ Math.floor((rechargeForm.feeAmount || 0) / 10) }}</strong> 次</span>
        </el-form-item>

        <el-form-item label="转账付款截图凭证" prop="screenshotUrl">
          <div v-if="rechargeForm.screenshotUrl" class="proof-card">
            <el-image
              :src="rechargeForm.screenshotUrl"
              fit="contain"
              class="proof-img"
              :preview-src-list="[rechargeForm.screenshotUrl]"
              preview-teleported
            />
            <div class="proof-actions">
              <el-button size="small" type="danger" link @click="rechargeForm.screenshotUrl = ''">移除重新上传</el-button>
            </div>
          </div>
          <el-upload
            v-else
            drag
            action="#"
            :auto-upload="false"
            :show-file-list="false"
            accept=".jpg,.jpeg,.png,.webp"
            :on-change="handleProofUpload"
            :disabled="uploadingProof"
          >
            <div class="proof-upload-box">
              <el-icon v-if="!uploadingProof"><UploadFilled /></el-icon>
              <el-icon v-else class="is-loading"><Loading /></el-icon>
              <span>{{ uploadingProof ? '凭证上传中...' : '点击或将转账截图拖拽到此处' }}</span>
            </div>
          </el-upload>
        </el-form-item>

        <el-form-item label="充值备注（可选）">
          <el-input
            v-model="rechargeForm.remark"
            maxlength="200"
            placeholder="可填写转账单号或公司名称等"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showRechargeDialog = false">取消</el-button>
          <el-button type="primary" :loading="submittingRecharge" @click="handleSubmitRecharge">
            提交充值审核
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CircleCheck,
  Clock,
  CloseBold,
  Document,
  DocumentChecked,
  Download,
  Loading,
  Plus,
  Refresh,
  UploadFilled,
  View,
  Wallet
} from '@element-plus/icons-vue'
import AppHeader from '@/components/AppHeader.vue'
import { sealDocumentApi, type SealDocument, type UserSealQuota } from '@/api/sealDocument'
import { rechargeRequestApi } from '@/api/rechargeRequest'
import { generateIdempotencyKey } from '@/utils/idempotency'
import { saveBlobResponse } from '@/utils/download'

// 钱包额度
const route = useRoute()
const sealQuota = reactive<UserSealQuota>({
  id: 0,
  userId: 0,
  balance: 0,
  totalRecharged: 0,
  totalDeducted: 0,
  availableCount: 0,
  updatedAt: ''
})

// 列表与加载
const documents = ref<SealDocument[]>([])
const loadingList = ref(false)
const filterStatus = ref('ALL')
const isUploading = ref(false)

// 充值弹窗
const showRechargeDialog = ref(false)
const uploadingProof = ref(false)
const submittingRecharge = ref(false)
const rechargeFormRef = ref()
const rechargeForm = reactive({
  feeAmount: 50,
  screenshotUrl: '',
  remark: ''
})
const rechargeRules = {
  feeAmount: [{ required: true, message: '请输入充值金额', trigger: 'change' }],
  screenshotUrl: [{ required: true, message: '请上传转账截图凭证', trigger: 'change' }]
}

// 统计
const pendingCount = computed(() => documents.value.filter(d => d.status === 'PENDING').length)
const completedCount = computed(() => documents.value.filter(d => d.status === 'COMPLETED').length)

// 获取盖章额度
const fetchQuota = async () => {
  try {
    const res = await sealDocumentApi.getMySealQuota()
    if (res) {
      Object.assign(sealQuota, res)
    }
  } catch (err: any) {
    console.error('获取盖章额度失败', err)
  }
}

// 获取文件列表
const fetchDocuments = async () => {
  loadingList.value = true
  try {
    const res = await sealDocumentApi.getMyDocuments(filterStatus.value)
    documents.value = res || []
  } catch (err: any) {
    ElMessage.error(err.message || '获取文件列表失败')
  } finally {
    loadingList.value = false
  }
}

const refreshAll = () => {
  fetchQuota()
  fetchDocuments()
}

let docChannel: BroadcastChannel | null = null

const broadcastDocEvent = (type: string) => {
  if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
    try {
      const channel = new BroadcastChannel('bobapi-document-events')
      channel.postMessage({ type })
      channel.close()
    } catch (_) {}
  }
  window.dispatchEvent(new CustomEvent('document-updated', { detail: { type } }))
}

const broadcastRechargeCreated = () => {
  if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
    try {
      const channel = new BroadcastChannel('bobapi-recharge-events')
      channel.postMessage({ type: 'recharge-created' })
      channel.close()
    } catch (_) {}
  }
  window.dispatchEvent(new CustomEvent('recharge-created'))
}

const handleRealtimeUpdate = () => {
  refreshAll()
}

watch(
  () => route.query.action,
  (action: unknown) => {
    if (action === 'recharge') {
      openRechargeDialog()
    }
  }
)

onMounted(() => {
  refreshAll()
  if (route.query.action === 'recharge') {
    openRechargeDialog()
  }
  if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
    try {
      docChannel = new BroadcastChannel('bobapi-document-events')
      docChannel.onmessage = () => {
        refreshAll()
      }
    } catch (_) {}
  }
  window.addEventListener('document-updated', handleRealtimeUpdate)
  window.addEventListener('recharge-reviewed', handleRealtimeUpdate)
})

onBeforeUnmount(() => {
  if (docChannel) {
    docChannel.close()
    docChannel = null
  }
  window.removeEventListener('document-updated', handleRealtimeUpdate)
  window.removeEventListener('recharge-reviewed', handleRealtimeUpdate)
})

// 用户选择文件后直接上传并扣费
const handleFileSelected = async (fileObj: any) => {
  const file = fileObj.raw
  if (!file) return

  // 校验当前余额是否足够 10 元
  if ((sealQuota.balance || 0) < 10) {
    ElMessageBox.confirm(
      '当前盖章钱包余额不足 10 元，单次盖章需 10 元。是否立即充值？',
      '余额不足提示',
      {
        confirmButtonText: '立即充值',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(() => {
      openRechargeDialog()
    }).catch(() => {})
    return
  }

  isUploading.value = true
  try {
    await sealDocumentApi.uploadDocument(file)
    ElMessage.success(`文件《${file.name}》已成功上传并扣除 10 元额度，等待发票方盖章交付`)
    await fetchQuota()
    await fetchDocuments()
    broadcastDocEvent('document-uploaded')
  } catch (err: any) {
    ElMessage.error(err.message || '文件上传提交失败')
  } finally {
    isUploading.value = false
  }
}

// 用户撤销申请
const handleCancelDocument = async (row: SealDocument) => {
  try {
    await sealDocumentApi.cancelDocument(row.id)
    ElMessage.success('已撤销申请，10 元服务费已自动退还')
    await fetchQuota()
    await fetchDocuments()
    broadcastDocEvent('document-cancelled')
  } catch (err: any) {
    ElMessage.error(err.message || '撤销失败')
  }
}

// 下载原件
const handleDownloadSource = async (row: SealDocument) => {
  try {
    const res = await sealDocumentApi.downloadSourceFile(row.id)
    saveBlobResponse(res, row.sourceFileName || '原件')
  } catch (err: any) {
    ElMessage.error(err.message || '下载原件失败')
  }
}

// 预览交付文件
const handlePreviewSealed = async (row: SealDocument) => {
  try {
    const res = await sealDocumentApi.previewSealedFile(row.id)
    const blob = res.data
    const url = window.URL.createObjectURL(blob)
    window.open(url, '_blank')
  } catch (err: any) {
    ElMessage.error(err.message || '预览交付文件失败')
  }
}

// 下载交付文件
const handleDownloadSealed = async (row: SealDocument) => {
  try {
    const res = await sealDocumentApi.downloadSealedFile(row.id)
    saveBlobResponse(res, row.sealedFileName || '盖章交付件')
  } catch (err: any) {
    ElMessage.error(err.message || '下载交付件失败')
  }
}

// 打开充值弹窗
const openRechargeDialog = () => {
  rechargeForm.feeAmount = 50
  rechargeForm.screenshotUrl = ''
  rechargeForm.remark = ''
  showRechargeDialog.value = true
}

// 上传凭证
const handleProofUpload = async (fileObj: any) => {
  const file = fileObj.raw
  if (!file) return
  uploadingProof.value = true
  try {
    const url = await rechargeRequestApi.uploadScreenshot(file)
    rechargeForm.screenshotUrl = url
    ElMessage.success('凭证上传成功')
  } catch (err: any) {
    ElMessage.error(err.message || '凭证上传失败')
  } finally {
    uploadingProof.value = false
  }
}

// 提交充值
const handleSubmitRecharge = async () => {
  if (!rechargeFormRef.value) return
  await rechargeFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    submittingRecharge.value = true
    try {
      const idempotencyKey = generateIdempotencyKey()
      await rechargeRequestApi.createRequest({
        requestType: 'SEAL',
        feeAmount: rechargeForm.feeAmount,
        screenshotUrl: rechargeForm.screenshotUrl,
        remark: rechargeForm.remark ? `[盖章钱包充值] ${rechargeForm.remark}` : '[盖章钱包充值]'
      }, idempotencyKey)

      ElMessage.success('充值申请提交成功，请等待管理员审核入账')
      showRechargeDialog.value = false
      broadcastRechargeCreated()
    } catch (err: any) {
      ElMessage.error(err.message || '提交充值申请失败')
    } finally {
      submittingRecharge.value = false
    }
  })
}

// 工具函数
const formatMoney = (val?: number) => {
  if (val === undefined || val === null) return '0.00'
  return Number(val).toFixed(2)
}

const formatFileSize = (bytes?: number) => {
  if (!bytes) return '0 KB'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

const formatDateTime = (val?: string) => {
  if (!val) return '-'
  return val.replace('T', ' ').slice(0, 19)
}

</script>

<style scoped>
.user-document-page {
  min-height: 100vh;
  background-color: var(--color-bg, #f4f6f9);
  padding-bottom: 40px;
}

.main-content {
  max-width: 1360px;
  margin: 24px auto 0;
  padding: 0 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 顶部概览网格 */
.overview-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  gap: 16px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.balance-card {
  background: var(--color-surface, #fff);
  color: var(--color-text, #20252b);
  border: 1px solid var(--color-border, #e2e7e5);
}

.stat-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon-badge {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  background: var(--color-primary-soft, #e9f4f0);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  color: var(--color-primary, #12715b);
}

.stat-meta {
  display: flex;
  flex-direction: column;
}

.balance-card .stat-label {
  font-size: 13px;
  color: var(--color-text-secondary, #4d5863);
  font-weight: 500;
}

.balance-display {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.currency-symbol {
  font-size: 22px;
  color: var(--color-primary, #12715b);
  font-weight: 700;
}

.balance-number {
  font-size: 36px;
  font-weight: 800;
  letter-spacing: -0.5px;
  color: var(--color-text, #20252b);
}

.stat-footer {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.quota-count-tag {
  font-size: 13px;
  color: var(--color-text-secondary, #4d5863);
  background: var(--color-surface-muted, #f8faf9);
  border: 1px solid var(--color-border, #e2e7e5);
  padding: 4px 10px;
  border-radius: 6px;
}

.quota-count-tag strong {
  color: var(--color-primary, #12715b);
  font-size: 15px;
}

.recharge-btn {
  font-weight: 600;
}

.mini-stat {
  justify-content: center;
}

.mini-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
}

.icon-pending { color: #e6a23c; font-size: 20px; }
.icon-success { color: #67c23a; font-size: 20px; }
.icon-info { color: #409eff; font-size: 20px; }

.stat-value {
  font-size: 30px;
  font-weight: 700;
  margin: 10px 0 4px;
  color: #1e293b;
}

.text-warning { color: #d97706; }
.text-success { color: #16a34a; }

.stat-hint {
  font-size: 12px;
  color: #94a3b8;
}

/* 核心直接上传卡片 */
.upload-section-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid #ebeef5;
}

.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 18px;
}

.section-heading h2 {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 6px 0;
}

.section-desc {
  font-size: 13px;
  color: #64748b;
  margin: 0;
}

.direct-document-uploader :deep(.el-upload) {
  width: 100%;
}

.direct-document-uploader :deep(.el-upload-dragger) {
  width: 100%;
  border: 1px dashed var(--color-border, #e2e7e5);
  border-radius: 8px;
  background: var(--color-surface-muted, #f8faf9);
  padding: 36px 20px;
  transition: border-color 0.2s, background-color 0.2s;
}

.direct-document-uploader :deep(.el-upload-dragger:hover) {
  border-color: var(--color-primary, #12715b);
  background: var(--color-primary-soft, #e9f4f0);
}

.uploader-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.upload-icon-circle {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--color-primary-soft, #e9f4f0);
  color: var(--color-primary, #12715b);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
}

.upload-prompt {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.upload-prompt .primary-text {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.upload-prompt .sub-text {
  font-size: 13px;
  color: #64748b;
}

/* 记录表格卡片 */
.table-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid #ebeef5;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}

.table-title-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.table-title-group h3 {
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
}

.total-tag {
  font-size: 12px;
  color: #64748b;
  background: #f1f5f9;
  padding: 3px 8px;
  border-radius: 10px;
}

.table-filters {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-icon {
  font-size: 24px;
  color: #3b82f6;
  flex-shrink: 0;
}

.file-info-group {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.file-title {
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size-hint {
  font-size: 12px;
  color: #94a3b8;
}

.fee-text {
  font-weight: 700;
  color: #0284c7;
}

.sealed-file-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #16a34a;
  font-weight: 600;
}

.sealed-icon {
  font-size: 18px;
}

.sealed-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reject-reason-cell .reject-text {
  color: #dc2626;
  font-size: 13px;
  cursor: pointer;
}

.pending-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #d97706;
  font-size: 13px;
}

.cancelled-hint {
  color: #94a3b8;
  font-size: 13px;
}

.actions-group {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

/* 充值弹窗样式 */
.quick-amounts {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}

.form-hint {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
  display: block;
}

.proof-upload-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 13px;
}

.proof-card {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  background: #f8fafc;
}

.proof-img {
  max-height: 180px;
  border-radius: 4px;
}

.proof-actions {
  margin-top: 8px;
}

@media (max-width: 1024px) {
  .overview-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
