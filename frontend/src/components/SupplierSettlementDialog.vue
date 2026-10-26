<template>
  <el-dialog
    v-model="visible"
    title="供应商结算"
    width="480px"
    :close-on-click-modal="false"
    destroy-on-close
    @close="handleClose"
  >
    <!-- 显示当前未结款项供管理员参考，支持提前结款与负数抵扣 -->
    <el-alert
      v-if="unsettledAmount !== undefined"
      :type="unsettledAmount < 0 ? 'warning' : 'info'"
      :closable="false"
      class="unsettled-hint"
    >
      <template #default>
        <span>当前未结款项：</span>
        <strong class="unsettled-amount">{{ formatDisplayAmount(unsettledAmount) }}</strong>
        <span v-if="unsettledAmount < 0" class="unsettled-sub">（当前已提前预结，后续开票将自动抵扣）</span>
        <span v-else class="unsettled-sub">（支持提前结款，超出未结金额部分将计为负数自动抵扣）</span>
      </template>
    </el-alert>

    <!-- 四大出票通道额度快捷参考与填充 -->
    <div v-if="channels && channels.length" class="channels-ref-box">
      <div class="channels-ref-title">
        <span>各出票通道已开额度参考 (点击快速带入):</span>
      </div>
      <div class="channels-tag-group">
        <el-tag
          v-for="ch in channels"
          :key="ch.key"
          size="small"
          class="channel-quick-tag"
          :type="ch.sellerCompany === 'TECH' ? 'primary' : 'success'"
          effect="plain"
          @click="handleSelectChannel(ch)"
        >
          {{ ch.label }}: ¥{{ formatAmount(ch.completedAmount) }}
        </el-tag>
      </div>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="80px"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="结算金额" prop="amount">
        <el-input-number
          v-model="form.amount"
          :min="0.01"
          :max="99999999.99"
          :precision="2"
          :step="100"
          controls-position="right"
          placeholder="请输入结算金额"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          placeholder="可填写结算说明（可选）"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-actions">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          确认结算
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createSettlement, type SupplierSettlementRequest } from '@/api/supplierSettlement'
import { generateIdempotencyKey } from '@/utils/idempotency'
import type { SellerCategoryStat } from '@/api/dashboard'

interface Props {
  modelValue: boolean
  /** 当前未结款项金额，用于动态限制最大结算额并在对话框内展示（修复 #2 / #8） */
  unsettledAmount?: number
  /** 四大通道细分统计数据供快捷参考与选择 */
  channels?: SellerCategoryStat[]
  /** 默认初始备注（如快捷从通道子卡片点击发起） */
  initialRemark?: string
  /** 默认初始金额 */
  initialAmount?: number
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
const submitting = ref(false)
const pendingIdempotencyKey = ref<string>('')

const form = reactive<SupplierSettlementRequest>({
  amount: undefined as any,
  remark: ''
})

const rules: FormRules = {
  amount: [
    { required: true, message: '请输入结算金额', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value === undefined || value === null || value <= 0) {
          callback(new Error('结算金额必须大于 0'))
        } else if (value < 0.01) {
          callback(new Error('结算金额不能小于 0.01 元'))
        } else if (value > 99999999.99) {
          callback(new Error('结算金额不能超过 99,999,999.99 元'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

const resetForm = () => {
  formRef.value?.resetFields()
  form.amount = undefined as any
  form.remark = ''
  pendingIdempotencyKey.value = ''
}

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      if (props.initialAmount !== undefined && props.initialAmount > 0) {
        form.amount = props.initialAmount
      }
      if (props.initialRemark) {
        form.remark = props.initialRemark
      }
    }
  }
)

function handleSelectChannel(ch: SellerCategoryStat) {
  if (ch.completedAmount > 0 && (!form.amount || form.amount <= 0)) {
    form.amount = ch.completedAmount
  }
  form.remark = `[${ch.label}] 款项结算`
}

const handleClose = () => {
  visible.value = false
  resetForm()
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    const idempotencyKey = pendingIdempotencyKey.value || generateIdempotencyKey('settlement')
    pendingIdempotencyKey.value = idempotencyKey

    submitting.value = true
    try {
      const payload: SupplierSettlementRequest = {
        amount: form.amount,
        remark: form.remark?.trim() || undefined
      }

      await createSettlement(payload, idempotencyKey)

      pendingIdempotencyKey.value = ''
      ElMessage.success('结算记录创建成功')
      emit('success')
      handleClose()
    } catch (error: any) {
      ElMessage.error(error.message || '创建结算记录失败，请重试')
    } finally {
      submitting.value = false
    }
  })
}

function formatAmount(val: number): string {
  return (val || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatDisplayAmount(val: number): string {
  const isNegative = val < 0
  const abs = Math.abs(val)
  return `${isNegative ? '-' : ''}¥${formatAmount(abs)}`
}
</script>

<style scoped>
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  width: 100%;
}

/* 修复 #8：未结款项提示样式 */
.unsettled-hint {
  margin-bottom: 12px;
  border-radius: 8px;
}

.channels-ref-box {
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 16px;
}

.channels-ref-title {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
  font-weight: 500;
}

.channels-tag-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.channel-quick-tag {
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
}

.channel-quick-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);
}

.unsettled-amount {
  font-size: 16px;
  color: #e6a23c;
  margin: 0 4px;
}

.unsettled-sub {
  font-size: 12px;
  color: #909399;
  margin-left: 4px;
}
</style>

