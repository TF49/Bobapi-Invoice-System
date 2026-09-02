<template>
  <el-dialog
    v-model="visible"
    title="申请充值额度"
    width="520px"
    class="recharge-dialog"
    :close-on-click-modal="false"
    destroy-on-close
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="90px"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="手续费 (元)" prop="feeAmount">
        <el-input-number
          v-model="form.feeAmount"
          :min="0.01"
          :max="29999.99"
          :precision="2"
          :step="1"
          controls-position="right"
          placeholder="请输入手续费"
          style="width: 100%"
        />
        <span class="form-hint">支持手续费范围：0.01 - 29,999.99 元</span>
      </el-form-item>

      <div class="quota-conversion" aria-live="polite">
        <div class="conversion-item">
          <span>手续费比例</span>
          <strong>3%</strong>
        </div>
        <div class="conversion-divider" />
        <div class="conversion-item conversion-result">
          <span>预计到账额度</span>
          <strong>{{ formatCurrency(calculatedQuotaAmount) }}</strong>
        </div>
      </div>

      <el-form-item label="充值凭证截图" prop="screenshotUrl">
        <div class="upload-section">
          <!-- 已上传凭证：专属高清预览卡片 -->
          <div v-if="displayPreviewUrl" class="proof-preview-card">
            <div class="proof-card-header">
              <div class="proof-status-label">
                <i class="status-dot-success" />
                <span>{{ uploading ? '正在上传中...' : '凭证已就绪（点击图片可全屏放大）' }}</span>
              </div>
              <div class="proof-card-actions">
                <el-button size="small" type="primary" link :icon="UploadFilled" :disabled="uploading" @click="triggerFileInput">
                  更换凭证
                </el-button>
                <el-button size="small" type="danger" link :icon="Delete" :disabled="uploading" @click="removeScreenshot">
                  移除
                </el-button>
              </div>
            </div>
            <div class="proof-img-container" v-loading="uploading">
              <el-image
                :src="displayPreviewUrl"
                :preview-src-list="[displayPreviewUrl]"
                preview-teleported
                fit="contain"
                class="uploaded-proof-img"
              >
                <template #placeholder>
                  <div class="proof-img-loading">加载凭证中...</div>
                </template>
                <template #error>
                  <div class="proof-img-error">
                    <el-icon :size="24"><Picture /></el-icon>
                    <span>图片加载失败，请检查 URL 或重新上传</span>
                  </div>
                </template>
              </el-image>
            </div>
          </div>

          <!-- 未上传：拖拽/点击上传区域 -->
          <div v-else>
            <el-upload
              class="screenshot-uploader"
              drag
              action="#"
              :auto-upload="false"
              :show-file-list="false"
              accept=".jpg,.jpeg,.png,.webp"
              :on-change="handleFileChange"
              :disabled="uploading"
            >
              <div class="upload-placeholder">
                <el-icon v-if="!uploading" class="upload-icon"><UploadFilled /></el-icon>
                <el-icon v-else class="upload-icon is-loading"><Loading /></el-icon>
                <div class="upload-text">
                  <span>{{ uploading ? '正在上传凭证图片...' : '点击或将转账截图拖拽到此处' }}</span>
                  <small>支持 JPG / PNG / WEBP 格式，单张不超过 5MB</small>
                </div>
              </div>
            </el-upload>
          </div>

          <!-- 隐藏的文件选择器（用于更换凭证） -->
          <input
            ref="fileInputRef"
            type="file"
            accept=".jpg,.jpeg,.png,.webp"
            style="display: none"
            @change="handleHiddenFileInputChange"
          />

          <!-- 备用 URL 输入入口 -->
          <div class="url-fallback-toggle">
            <el-button link type="primary" size="small" @click="showUrlInput = !showUrlInput">
              {{ showUrlInput ? '收起 URL 输入' : '或手动输入图片 URL' }}
            </el-button>
          </div>

          <el-input
            v-if="showUrlInput"
            v-model="form.screenshotUrl"
            placeholder="https://example.com/screenshot.png"
            maxlength="512"
            clearable
            style="margin-top: 8px"
            @input="handleUrlInput"
          />
        </div>
      </el-form-item>

      <el-form-item label="备注说明" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          placeholder="可填写转账单号或具体充值说明（可选）"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-actions">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="submitting || uploading" @click="handleSubmit">
          确认提交
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElImage, ElMessage, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import { Delete, Loading, Picture, UploadFilled } from '@element-plus/icons-vue'
import { rechargeRequestApi, type CreateRechargeRequest } from '@/api/rechargeRequest'
import { getImageUrl } from '@/utils/imageUrl'
import { generateIdempotencyKey } from '@/utils/idempotency'

interface Props {
  modelValue: boolean
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const visible = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit('update:modelValue', val)
})

const formRef = ref<FormInstance>()
const fileInputRef = ref<HTMLInputElement>()
const submitting = ref(false)
const uploading = ref(false)
const showUrlInput = ref(false)
const localPreviewUrl = ref('')
const uploadGeneration = ref(0)
const pendingIdempotencyKey = ref('')
const pendingPayloadFingerprint = ref('')

const form = reactive<CreateRechargeRequest>({
  feeAmount: undefined as any,
  screenshotUrl: '',
  remark: ''
})

const calculatedQuotaAmount = computed(() => {
  const feeAmount = Number(form.feeAmount)
  if (!Number.isFinite(feeAmount) || feeAmount <= 0) return 0

  const feeCents = Math.round(feeAmount * 100)
  const quotaCents = Math.floor((feeCents * 100) / 3)
  return quotaCents / 100
})

const formatCurrency = (amount: number) => new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  minimumFractionDigits: 2
}).format(amount)

const displayPreviewUrl = computed(() => {
  if (localPreviewUrl.value) return localPreviewUrl.value
  return getImageUrl(form.screenshotUrl)
})

const rules: FormRules = {
  feeAmount: [
    { required: true, message: '请输入手续费', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value === undefined || value === null || value <= 0) {
          callback(new Error('手续费必须大于 0'))
        } else if (value < 0.01) {
          callback(new Error('手续费不能小于 0.01 元'))
        } else if (value > 29999.99) {
          callback(new Error('手续费不能超过 29,999.99 元'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  screenshotUrl: [
    {
      validator: (_rule, value, callback) => {
        if (typeof value !== 'string' || !value.trim()) {
          callback(new Error('请上传或填写充值截图凭证'))
        } else if (value.length > 512) {
          callback(new Error('截图地址不能超过 512 个字符'))
        } else {
          callback()
        }
      },
      trigger: ['blur', 'change']
    }
  ]
}

const processUpload = async (rawFile: File) => {
  const isImage = /\.(jpg|jpeg|png|webp)$/i.test(rawFile.name)
  if (!isImage) {
    ElMessage.error('仅支持 JPG、PNG、WEBP 格式的图片文件')
    return
  }

  const isLt5M = rawFile.size / 1024 / 1024 <= 5
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB')
    return
  }

  // 1. 本地即时生成 Blob 预览（0毫秒立即展示，无需等待网络）
  const currentUploadGeneration = ++uploadGeneration.value
  if (localPreviewUrl.value) {
    URL.revokeObjectURL(localPreviewUrl.value)
  }
  localPreviewUrl.value = URL.createObjectURL(rawFile)

  uploading.value = true
  try {
    const url = await rechargeRequestApi.uploadScreenshot(rawFile)
    if (currentUploadGeneration !== uploadGeneration.value) return
    form.screenshotUrl = url
    ElMessage.success('凭证上传成功')
    formRef.value?.validateField('screenshotUrl')
  } catch (error: any) {
    if (currentUploadGeneration !== uploadGeneration.value) return
    ElMessage.error(error.message || '上传凭证失败，请重试')
    if (localPreviewUrl.value) {
      URL.revokeObjectURL(localPreviewUrl.value)
      localPreviewUrl.value = ''
    }
  } finally {
    if (currentUploadGeneration === uploadGeneration.value) {
      uploading.value = false
    }
  }
}

const handleFileChange = async (uploadFile: UploadFile) => {
  if (!uploadFile.raw) return
  await processUpload(uploadFile.raw)
}

const triggerFileInput = () => {
  fileInputRef.value?.click()
}

const handleHiddenFileInputChange = async (e: Event) => {
  const target = e.target as HTMLInputElement
  if (target.files && target.files[0]) {
    await processUpload(target.files[0])
    target.value = ''
  }
}

const removeScreenshot = () => {
  uploadGeneration.value += 1
  uploading.value = false
  if (localPreviewUrl.value) {
    URL.revokeObjectURL(localPreviewUrl.value)
    localPreviewUrl.value = ''
  }
  form.screenshotUrl = ''
  formRef.value?.validateField('screenshotUrl')
}

const handleUrlInput = () => {
  uploadGeneration.value += 1
  uploading.value = false
  if (localPreviewUrl.value) {
    URL.revokeObjectURL(localPreviewUrl.value)
    localPreviewUrl.value = ''
  }
  formRef.value?.validateField('screenshotUrl')
}

const resetForm = () => {
  uploadGeneration.value += 1
  uploading.value = false
  if (localPreviewUrl.value) {
    URL.revokeObjectURL(localPreviewUrl.value)
    localPreviewUrl.value = ''
  }
  formRef.value?.resetFields()
  form.feeAmount = undefined as any
  form.screenshotUrl = ''
  form.remark = ''
  pendingIdempotencyKey.value = ''
  pendingPayloadFingerprint.value = ''
  showUrlInput.value = false
}

const handleClose = () => {
  visible.value = false
  resetForm()
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      const normalizedRemark = form.remark?.trim() || undefined
      const payload: CreateRechargeRequest = {
        feeAmount: form.feeAmount,
        screenshotUrl: form.screenshotUrl.trim(),
        remark: normalizedRemark
      }
      const payloadFingerprint = JSON.stringify(payload)
      if (!pendingIdempotencyKey.value || pendingPayloadFingerprint.value !== payloadFingerprint) {
        pendingIdempotencyKey.value = generateIdempotencyKey('recharge')
        pendingPayloadFingerprint.value = payloadFingerprint
      }

      await rechargeRequestApi.createRequest(payload, pendingIdempotencyKey.value)

      ElMessage.success('充值申请提交成功，请等待管理员审核')
      if (typeof window !== 'undefined') {
        try {
          const channel = new BroadcastChannel('bobapi-recharge-events')
          channel.postMessage({ type: 'RECHARGE_CREATED' })
          channel.close()
        } catch (_) {}
        window.dispatchEvent(new CustomEvent('recharge-created'))
      }
      emit('success')
      handleClose()
    } catch (error: any) {
      ElMessage.error(error.message || '提交申请失败，请重试')
    } finally {
      submitting.value = false
    }
  })
}
</script>

<style scoped>
.form-hint {
  display: block;
  margin-top: 4px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.quota-conversion {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 16px;
  margin: -2px 0 20px;
  padding: 12px 0;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}

.conversion-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.conversion-item span {
  color: var(--color-text-muted);
  font-size: 12px;
}

.conversion-item strong {
  color: var(--color-text);
  font-size: 15px;
  font-variant-numeric: tabular-nums;
}

.conversion-result {
  text-align: right;
}

.conversion-result strong {
  color: var(--color-primary);
  font-size: 18px;
}

.conversion-divider {
  width: 1px;
  height: 30px;
  background: var(--color-border);
}

.upload-section {
  width: 100%;
}

.screenshot-uploader :deep(.el-upload) {
  width: 100%;
}

.screenshot-uploader :deep(.el-upload-dragger) {
  width: 100%;
  padding: 24px 16px;
  border-radius: 8px;
  border: 1px dashed var(--color-border);
  background: var(--color-surface-soft, #f9fbfb);
  transition: border-color 0.2s, background-color 0.2s;
}

.screenshot-uploader :deep(.el-upload-dragger:hover) {
  border-color: var(--color-primary);
  background: var(--color-primary-soft, #eef7f5);
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.upload-icon {
  font-size: 36px;
  color: var(--color-primary);
}

.upload-text {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 4px;
  color: var(--color-text);
  font-size: 13px;
  width: 100%;
}

.upload-text small {
  color: var(--color-text-muted);
  font-size: 11px;
  word-break: break-word;
}

/* 凭证预览卡片 */
.proof-preview-card {
  border-radius: 8px;
  border: 1px solid var(--color-border);
  background: var(--color-surface-soft, #f8faf9);
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.proof-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.proof-status-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #12715b;
  font-size: 12px;
  font-weight: 550;
}

.status-dot-success {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #12715b;
}

.proof-card-actions {
  display: flex;
  gap: 8px;
}

.proof-img-container {
  height: 180px;
  border-radius: 6px;
  border: 1px solid var(--color-border);
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.uploaded-proof-img {
  width: 100%;
  height: 100%;
  cursor: zoom-in;
}

.uploaded-proof-img :deep(.el-image__inner) {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.proof-img-loading,
.proof-img-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.url-fallback-toggle {
  margin-top: 6px;
  text-align: right;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  width: 100%;
}

@media (max-width: 600px) {
  .screenshot-uploader :deep(.el-upload-dragger) {
    padding: 18px 12px;
  }

  .upload-icon {
    font-size: 30px;
  }

  .proof-img-container {
    height: 150px;
  }

  .dialog-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }

  .dialog-actions .el-button {
    width: 100%;
    margin-left: 0 !important;
  }
}
</style>
