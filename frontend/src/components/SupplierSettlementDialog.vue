<template>
  <el-dialog
    v-model="visible"
    title="供应商结算"
    width="480px"
    :close-on-click-modal="false"
    destroy-on-close
    @close="handleClose"
  >
    <!-- 修复 #8：显示当前未结款项供管理员参考，避免盲目录入 -->
    <el-alert
      v-if="unsettledAmount !== undefined"
      type="info"
      :closable="false"
      class="unsettled-hint"
    >
      <template #default>
        <span>当前未结款项：</span>
        <strong class="unsettled-amount">¥{{ formatAmount(unsettledAmount) }}</strong>
        <span class="unsettled-sub">（本次结算金额不得超过此值）</span>
      </template>
    </el-alert>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="80px"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="结算金额" prop="amount">
        <!-- 修复 #2：:max 动态绑定当前未结款项，防止超额录入通过前端校验 -->
        <el-input-number
          v-model="form.amount"
          :min="0.01"
          :max="effectiveMax"
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
import { computed, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createSettlement, type SupplierSettlementRequest } from '@/api/supplierSettlement'

interface Props {
  modelValue: boolean
  /** 当前未结款项金额，用于动态限制最大结算额并在对话框内展示（修复 #2 / #8） */
  unsettledAmount?: number
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

// 修复 #2：有效最大值 = 当前未结款项（无值时降级为 999999.99）
const effectiveMax = computed(() =>
  props.unsettledAmount !== undefined ? props.unsettledAmount : 999999.99
)

const formRef = ref<FormInstance>()
const submitting = ref(false)

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
        } else if (props.unsettledAmount !== undefined && value > props.unsettledAmount) {
          // 修复 #2：动态校验，超过当前未结款项时给出明确提示
          callback(new Error(`结算金额不能超过当前未结款项 ¥${formatAmount(props.unsettledAmount)}`))
        } else if (value > 999999.99) {
          callback(new Error('结算金额不能超过 999,999.99 元'))
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
      const payload: SupplierSettlementRequest = {
        amount: form.amount,
        remark: form.remark?.trim() || undefined
      }

      await createSettlement(payload)

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

