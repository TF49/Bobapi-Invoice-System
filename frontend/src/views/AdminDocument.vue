<template>
  <div class="admin-document-page">
    <AppHeader title="证明文件盖章管理" />

    <main class="main-content">
      <!-- 顶部统计看板 -->
      <section class="stat-grid">
        <div class="stat-card pending-card">
          <div class="stat-icon-wrapper warning">
            <el-icon><Clock /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-label">待盖章工单</span>
            <div class="stat-value warning">{{ pendingCount }}</div>
            <span class="stat-sub">亟需盖章并交付文件</span>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon-wrapper success">
            <el-icon><CircleCheck /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-label">已交付完成</span>
            <div class="stat-value success">{{ completedCount }}</div>
            <span class="stat-sub">用户已可下载带章成品</span>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon-wrapper danger">
            <el-icon><CircleClose /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-label">已驳回退款</span>
            <div class="stat-value danger">{{ rejectedCount }}</div>
            <span class="stat-sub">服务费已全额原路退还</span>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon-wrapper info">
            <el-icon><DocumentChecked /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-label">总工单记录</span>
            <div class="stat-value">{{ documents.length }}</div>
            <span class="stat-sub">累计证明文件盖章</span>
          </div>
        </div>
      </section>

      <!-- 列表与过滤卡片 -->
      <section class="table-card">
        <div class="table-toolbar">
          <div class="toolbar-left">
            <el-radio-group v-model="filterStatus" size="default" @change="fetchDocuments">
              <el-radio-button label="ALL">全部工单</el-radio-button>
              <el-radio-button label="PENDING">
                待盖章 ({{ pendingCount }})
              </el-radio-button>
              <el-radio-button label="COMPLETED">已交付</el-radio-button>
              <el-radio-button label="REJECTED">已驳回</el-radio-button>
              <el-radio-button label="CANCELLED">已取消</el-radio-button>
            </el-radio-group>
            
            <el-input
              v-model="searchUsername"
              placeholder="按用户名筛选"
              clearable
              style="width: 200px;"
              @clear="fetchDocuments"
              @keyup.enter="fetchDocuments"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>

            <el-button type="primary" plain :icon="Search" @click="fetchDocuments">
              查询
            </el-button>
          </div>
        </div>

        <el-table
          v-loading="loading"
          :data="documents"
          stripe
          class="admin-doc-table"
          empty-text="暂无相关文件记录"
        >
          <el-table-column label="工单ID" prop="id" width="85" align="center" />

          <el-table-column label="申请用户" width="130" align="center">
            <template #default="{ row }">
              <div class="user-badge">
                <el-icon><User /></el-icon>
                <span>{{ row.username || `用户#${row.userId}` }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="待盖章原文件" min-width="220">
            <template #default="{ row }">
              <div class="doc-title-cell">
                <el-icon class="doc-icon"><Document /></el-icon>
                <div class="doc-meta">
                  <span class="file-name" :title="row.sourceFileName">{{ row.sourceFileName }}</span>
                  <span class="file-size">{{ formatFileSize(row.sourceFileSize) }}</span>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="服务费" width="95" align="center">
            <template #default="{ row }">
              <span class="fee-tag">¥{{ Number(row.feeAmount || 10).toFixed(2) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="提交时间" width="165" align="center">
            <template #default="{ row }">
              <span>{{ formatDateTime(row.createdAt) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="115" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'PENDING'" type="warning" effect="dark" round>
                待盖章
              </el-tag>
              <el-tag v-else-if="row.status === 'COMPLETED'" type="success" effect="dark" round>
                已完成
              </el-tag>
              <el-tag v-else-if="row.status === 'REJECTED'" type="danger" effect="light" round>
                已驳回
              </el-tag>
              <el-tag v-else-if="row.status === 'CANCELLED'" type="info" effect="plain" round>
                已取消
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="交付成品 / 备注" min-width="220">
            <template #default="{ row }">
              <div v-if="row.status === 'COMPLETED'" class="sealed-cell">
                <el-icon class="check-icon"><CircleCheck /></el-icon>
                <span class="sealed-name" :title="row.sealedFileName">{{ row.sealedFileName || '已交付' }}</span>
                <span v-if="row.operatorName" class="operator-hint">({{ row.operatorName }})</span>
              </div>
              <div v-else-if="row.status === 'REJECTED'" class="reject-cell">
                <span class="reject-tag">驳回原因: {{ row.rejectReason }}</span>
              </div>
              <div v-else-if="row.status === 'CANCELLED'" class="cancelled-cell">
                <span>用户自主撤销（已退费）</span>
              </div>
              <div v-else class="pending-cell">
                <span class="waiting-text">等待开票方下载并盖章上传</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="260" fixed="right" align="center">
            <template #default="{ row }">
              <div class="action-buttons">
                <!-- 待盖章状态 -->
                <template v-if="row.status === 'PENDING'">
                  <el-button
                    type="primary"
                    size="small"
                    :icon="Download"
                    @click="handleDownloadSource(row)"
                  >
                    下载原件
                  </el-button>
                  <el-button
                    type="success"
                    size="small"
                    :icon="UploadFilled"
                    @click="openUploadDialog(row)"
                  >
                    上传交付
                  </el-button>
                  <el-button
                    type="danger"
                    size="small"
                    link
                    @click="openRejectDialog(row)"
                  >
                    驳回
                  </el-button>
                </template>

                <!-- 已完成状态 -->
                <template v-else-if="row.status === 'COMPLETED'">
                  <el-button
                    type="primary"
                    size="small"
                    link
                    :icon="View"
                    @click="handlePreviewSealed(row)"
                  >
                    预览
                  </el-button>
                  <el-button
                    type="success"
                    size="small"
                    link
                    :icon="Download"
                    @click="handleDownloadSealed(row)"
                  >
                    下载交付
                  </el-button>
                  <el-button
                    type="info"
                    size="small"
                    link
                    @click="openUploadDialog(row)"
                  >
                    重新上传
                  </el-button>
                </template>

                <!-- 其他状态 -->
                <template v-else>
                  <el-button
                    type="info"
                    size="small"
                    link
                    :icon="Download"
                    @click="handleDownloadSource(row)"
                  >
                    下载原件
                  </el-button>
                </template>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </main>

    <!-- 上传盖章成品弹窗 -->
    <el-dialog
      v-model="showUploadDialog"
      :title="`上传盖章交付成品 - 工单 #${currentDoc?.id}`"
      width="500px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div v-if="currentDoc" class="upload-dialog-content">
        <div class="current-doc-info">
          <div><strong>原文件名：</strong>{{ currentDoc.sourceFileName }}</div>
          <div><strong>申请用户：</strong>{{ currentDoc.username || `用户#${currentDoc.userId}` }}</div>
        </div>

        <el-upload
          class="sealed-file-uploader"
          drag
          action="#"
          :auto-upload="false"
          :show-file-list="false"
          :on-change="handleSealedFileSelected"
          :disabled="isDelivering"
        >
          <div class="uploader-body">
            <el-icon v-if="!isDelivering" class="upload-icon"><UploadFilled /></el-icon>
            <el-icon v-else class="upload-icon is-loading"><Loading /></el-icon>
            <div class="upload-text">
              <span v-if="!selectedFile">{{ isDelivering ? '正在上传交付文件...' : '点击或将盖好章的文件拖拽到此处' }}</span>
              <span v-else class="file-chosen-hint">已选择: <strong>{{ selectedFile.name }}</strong> (点击可更换)</span>
            </div>
            <span class="upload-tip">支持上传任意格式（盖章版 PDF / 高清扫描图片等）</span>
          </div>
        </el-upload>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showUploadDialog = false">取消</el-button>
          <el-button
            type="primary"
            :loading="isDelivering"
            :disabled="!selectedFile"
            @click="handleSubmitDelivery"
          >
            确认交付
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 驳回弹窗 -->
    <el-dialog
      v-model="showRejectDialog"
      title="驳回盖章申请"
      width="460px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="reject-dialog-content">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 16px;"
        >
          <template #title>
            <span>驳回后，系统将<strong>自动把 10.00 元服务费原路退回</strong>该用户的盖章钱包。</span>
          </template>
        </el-alert>

        <el-form label-position="top">
          <el-form-item label="驳回原因（必填）" required>
            <el-input
              v-model="rejectReason"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              placeholder="例如：文件缺少法人签字、内容存在合规风险、无法加盖公章等"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showRejectDialog = false">取消</el-button>
          <el-button
            type="danger"
            :loading="isRejecting"
            :disabled="!rejectReason.trim()"
            @click="handleConfirmReject"
          >
            确认驳回并退款
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  CircleCheck,
  CircleClose,
  Clock,
  Document,
  DocumentChecked,
  Download,
  Loading,
  Search,
  UploadFilled,
  User,
  View
} from '@element-plus/icons-vue'
import AppHeader from '@/components/AppHeader.vue'
import { sealDocumentApi, type SealDocument } from '@/api/sealDocument'
import { saveBlobResponse } from '@/utils/download'

const documents = ref<SealDocument[]>([])
const loading = ref(false)
const filterStatus = ref('ALL')
const searchUsername = ref('')

// 统计
const pendingCount = computed(() => documents.value.filter(d => d.status === 'PENDING').length)
const completedCount = computed(() => documents.value.filter(d => d.status === 'COMPLETED').length)
const rejectedCount = computed(() => documents.value.filter(d => d.status === 'REJECTED').length)

// 上传交付弹窗
const showUploadDialog = ref(false)
const currentDoc = ref<SealDocument | null>(null)
const selectedFile = ref<File | null>(null)
const isDelivering = ref(false)

// 驳回弹窗
const showRejectDialog = ref(false)
const rejectReason = ref('')
const isRejecting = ref(false)

// 获取列表
const fetchDocuments = async () => {
  loading.value = true
  try {
    const res = await sealDocumentApi.getAllDocuments(filterStatus.value, searchUsername.value)
    documents.value = res || []
  } catch (err: any) {
    ElMessage.error(err.message || '获取文件列表失败')
  } finally {
    loading.value = false
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

// 预览已交付文件
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

// 下载已交付文件
const handleDownloadSealed = async (row: SealDocument) => {
  try {
    const res = await sealDocumentApi.downloadSealedFile(row.id)
    saveBlobResponse(res, row.sealedFileName || '交付件')
  } catch (err: any) {
    ElMessage.error(err.message || '下载交付件失败')
  }
}

// 打开上传交付弹窗
const openUploadDialog = (row: SealDocument) => {
  currentDoc.value = row
  selectedFile.value = null
  showUploadDialog.value = true
}

const handleSealedFileSelected = (fileObj: any) => {
  if (fileObj.raw) {
    selectedFile.value = fileObj.raw
  }
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

// 提交交付
const handleSubmitDelivery = async () => {
  if (!currentDoc.value || !selectedFile.value) return
  isDelivering.value = true
  try {
    await sealDocumentApi.uploadSealedDocument(currentDoc.value.id, selectedFile.value)
    ElMessage.success('交付成功，文件状态已变更为【已完成】')
    showUploadDialog.value = false
    await fetchDocuments()
    broadcastDocEvent('document-delivered')
  } catch (err: any) {
    ElMessage.error(err.message || '交付上传失败')
  } finally {
    isDelivering.value = false
  }
}

// 打开驳回弹窗
const openRejectDialog = (row: SealDocument) => {
  currentDoc.value = row
  rejectReason.value = ''
  showRejectDialog.value = true
}

// 确认驳回
const handleConfirmReject = async () => {
  if (!currentDoc.value || !rejectReason.value.trim()) return
  isRejecting.value = true
  try {
    await sealDocumentApi.rejectDocument(currentDoc.value.id, rejectReason.value.trim())
    ElMessage.success('工单已驳回，10 元服务费已自动退回用户钱包')
    showRejectDialog.value = false
    await fetchDocuments()
    broadcastDocEvent('document-rejected')
  } catch (err: any) {
    ElMessage.error(err.message || '驳回操作失败')
  } finally {
    isRejecting.value = false
  }
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

const handleRealtimeRefresh = () => {
  fetchDocuments()
}

onMounted(() => {
  fetchDocuments()
  if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
    try {
      docChannel = new BroadcastChannel('bobapi-document-events')
      docChannel.onmessage = () => {
        fetchDocuments()
      }
    } catch (_) {}
  }
  window.addEventListener('document-updated', handleRealtimeRefresh)
})

onBeforeUnmount(() => {
  if (docChannel) {
    docChannel.close()
    docChannel = null
  }
  window.removeEventListener('document-updated', handleRealtimeRefresh)
})
</script>

<style scoped>
.admin-document-page {
  min-height: 100vh;
  background-color: var(--color-bg, #f4f6f9);
  padding-bottom: 40px;
}

.main-content {
  max-width: 1440px;
  margin: 24px auto 0;
  padding: 0 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 顶部统计 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  gap: 16px;
}

.pending-card {
  border-left: 4px solid #f59e0b;
}

.stat-icon-wrapper {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
}

.stat-icon-wrapper.warning { background: #fef3c7; color: #d97706; }
.stat-icon-wrapper.success { background: #dcfce7; color: #16a34a; }
.stat-icon-wrapper.danger { background: #fee2e2; color: #dc2626; }
.stat-icon-wrapper.info { background: #e0f2fe; color: #0284c7; }

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.stat-value {
  font-size: 28px;
  font-weight: 800;
  color: #0f172a;
  margin: 2px 0;
}

.stat-value.warning { color: #d97706; }
.stat-value.success { color: #16a34a; }
.stat-value.danger { color: #dc2626; }

.stat-sub {
  font-size: 12px;
  color: #94a3b8;
}

/* 记录表格 */
.table-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid #ebeef5;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #1e293b;
  font-weight: 600;
}

.doc-title-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.doc-icon {
  font-size: 22px;
  color: #3b82f6;
  flex-shrink: 0;
}

.doc-meta {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.file-name {
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  font-size: 12px;
  color: #94a3b8;
}

.fee-tag {
  font-weight: 700;
  color: #0284c7;
}

.sealed-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #16a34a;
  font-weight: 600;
}

.check-icon {
  font-size: 16px;
}

.sealed-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.operator-hint {
  font-size: 12px;
  color: #64748b;
  font-weight: normal;
}

.reject-cell .reject-tag {
  color: #dc2626;
  font-size: 13px;
}

.waiting-text {
  color: #d97706;
  font-size: 13px;
}

.cancelled-cell {
  color: #94a3b8;
  font-size: 13px;
}

.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: wrap;
}

/* 弹窗样式 */
.current-doc-info {
  background: #f8fafc;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #334155;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sealed-file-uploader :deep(.el-upload-dragger) {
  border: 2px dashed #93c5fd;
  border-radius: 8px;
  background: #f8fafc;
  padding: 24px;
}

.uploader-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.upload-icon {
  font-size: 40px;
  color: #3b82f6;
}

.file-chosen-hint {
  color: #16a34a;
}

.upload-tip {
  font-size: 12px;
  color: #94a3b8;
}

.form-hint {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
  display: block;
}

@media (max-width: 1024px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
