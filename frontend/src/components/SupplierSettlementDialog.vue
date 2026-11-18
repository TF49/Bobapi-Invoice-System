<template>
  <el-dialog
    v-model="visible"
    title="供应商结算"
    width="560px"
    :close-on-click-modal="false"
    destroy-on-close
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="80px"
      @submit.prevent="handleSubmit"
    >
      <!-- 结算通道（出票主体 × 票种）必选：每笔结算只计入所选通道的已结金额 -->
      <el-form-item label="结算通道" prop="channelKey">
        <el-radio-group v-model="form.channelKey" class="channel-radio-group" @change="handleChannelChange">
          <el-radio
            v-for="ch in channelOptions"
            :key="ch.key"
            :value="ch.key"
            border
            class="channel-radio"
            :data-channel="ch.key"
          >
            <span class="channel-radio-name">{{ ch.label }}</span>
            <span class="channel-radio-unsettled" :class="{ negative: channelUnsettled(ch) < 0 }">
              未结 {{ formatDisplayAmount(channelUnsettled(ch)) }}
            </span>
          </el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 所选通道的未结款项；支持提前结款，负数仅在本通道后续开票中抵扣 -->
      <el-alert
        v-if="selectedChannel"
        :type="channelUnsettled(selectedChannel) < 0 ? 'warning' : 'info'"
        :closable="false"
        class="unsettled-hint"
      >
        <template #default>
          <span>
            [{{ selectedChannel.label }}] 已开 ¥{{ formatAmount(selectedChannel.completedAmount) }}，
            已结 ¥{{ formatAmount(selectedChannel.settledAmount || 0) }}，当前未结：
          </span>
          <strong class="unsettled-amount">{{ formatDisplayAmount(channelUnsettled(selectedChannel)) }}</strong>
          <span v-if="channelUnsettled(selectedChannel) < 0" class="unsettled-sub">（该通道已提前预结，后续该通道开票将自动抵扣）</span>
          <span v-else class="unsettled-sub">（支持提前结款，超出部分计为负数，仅在本通道后续开票中抵扣）</span>
        </template>
      </el-alert>

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
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createSettlement,
  type SettlementInvoiceCategory,
  type SettlementSellerCompany,
  type SupplierSettlementRequest
} from '@/api/supplierSettlement'
import { generateIdempotencyKey } from '@/utils/idempotency'
import type { SellerCategoryStat } from '@/api/dashboard'

interface Props {
  modelValue: boolean
  /** 四大结算通道（出票主体 × 票种）统计，含各通道已开 / 已结 / 未结 */
  channels?: SellerCategoryStat[]
  /** 默认选中的通道 key（如从通道子卡片点击「结算」发起） */
  initialChannelKey?: string
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
/** 最近一次由系统自动带入的金额；用于判断用户是否手动改过金额，切换通道时只覆盖自动带入的值 */
const lastAutoFilledAmount = ref<number | undefined>(undefined)

const form = reactive<{ channelKey: string; amount: number | undefined; remark: string }>({
  channelKey: '',
  amount: undefined,
  remark: ''
})

const channelOptions = computed(() => props.channels ?? [])
const selectedChannel = computed(() => channelOptions.value.find((ch) => ch.key === form.channelKey))

/** 通道未结 = 已开票面 - 已结算；后端未返回时前端兜底计算 */
function channelUnsettled(ch: SellerCategoryStat): number {
  if (ch.unsettledAmount !== undefined && ch.unsettledAmount !== null) {
    return Number(ch.unsettledAmount)
  }
  return Number(ch.completedAmount || 0) - Number(ch.settledAmount || 0)
}

const rules: FormRules = {
  channelKey: [
    { required: true, message: '请选择结算通道', trigger: 'change' }
  ],
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
  form.channelKey = ''
  form.amount = undefined
  form.remark = ''
  pendingIdempotencyKey.value = ''
  lastAutoFilledAmount.value = undefined
}

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      if (props.initialChannelKey) {
        form.channelKey = props.initialChannelKey
      }
      if (props.initialAmount !== undefined && props.initialAmount > 0) {
        form.amount = props.initialAmount
        lastAutoFilledAmount.value = props.initialAmount
      }
    }
  },
  { immediate: true }
)

/**
 * 切换通道时自动带入该通道的未结金额：
 * - 金额为空，或仍是上一次自动带入的值（用户未手动修改）时，覆盖为新通道的未结金额；
 * - 新通道未结 <= 0（无欠款或已预结）时清空金额，由用户自行填写预结金额；
 * - 用户手动改过的金额保持不动。
 */
function handleChannelChange() {
  const ch = selectedChannel.value
  if (!ch) return
  const amountIsAuto = !form.amount || form.amount <= 0 || form.amount === lastAutoFilledAmount.value
  if (!amountIsAuto) return
  const unsettled = channelUnsettled(ch)
  const next = unsettled > 0 ? Math.round(unsettled * 100) / 100 : undefined
  form.amount = next
  lastAutoFilledAmount.value = next
}

const handleClose = () => {
  visible.value = false
  resetForm()
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return
    const ch = selectedChannel.value
    if (!ch || form.amount === undefined) return

    // 二次确认：明确展示通道、金额与结算后的通道未结，降低选错通道 / 填错金额的概率
    const after = Math.round((channelUnsettled(ch) - form.amount) * 100) / 100
    const afterText = after < 0
      ? `结算后该通道将预结 ${formatDisplayAmount(-after)}（后续该通道开票自动抵扣）`
      : `结算后该通道未结将变为 ${formatDisplayAmount(after)}`
    try {
      await ElMessageBox.confirm(
        `确认向【${ch.label}】结算 ${formatDisplayAmount(form.amount)}？${afterText}。`,
        '确认结算',
        { confirmButtonText: '确认结算', cancelButtonText: '再看看', type: 'warning' }
      )
    } catch {
      return
    }

    const idempotencyKey = pendingIdempotencyKey.value || generateIdempotencyKey('settlement')
    pendingIdempotencyKey.value = idempotencyKey

    submitting.value = true
    try {
      const payload: SupplierSettlementRequest = {
        amount: form.amount,
        sellerCompany: ch.sellerCompany as SettlementSellerCompany,
        invoiceCategory: ch.invoiceCategory as SettlementInvoiceCategory,
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
  margin-bottom: 16px;
  border-radius: 8px;
}

.channel-radio-group {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
}

.channel-radio {
  margin-right: 0 !important;
  height: auto !important;
  padding: 8px 12px !important;
  width: 100%;
}

.channel-radio :deep(.el-radio__label) {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.4;
}

.channel-radio-name {
  font-weight: 600;
  color: #1e293b;
}

.channel-radio-unsettled {
  font-size: 12px;
  color: #e6a23c;
}

.channel-radio-unsettled.negative {
  color: #67c23a;
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
