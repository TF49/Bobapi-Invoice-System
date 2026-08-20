<template>
  <div class="quota-page">
    <el-card class="quota-card">
      <template #header>
        <div class="card-header">
          <span>我的额度</span>
        </div>
      </template>
      
      <div class="quota-info">
        <div class="quota-item">
          <div class="label">当前余额</div>
          <div class="value balance">{{ quota.balance.toFixed(2) }}</div>
        </div>
        <div class="quota-item">
          <div class="label">总充值</div>
          <div class="value">{{ quota.totalRecharged.toFixed(2) }}</div>
        </div>
        <div class="quota-item">
          <div class="label">总扣除</div>
          <div class="value">{{ quota.totalDeducted.toFixed(2) }}</div>
        </div>
      </div>
    </el-card>

    <el-card class="transactions-card">
      <template #header>
        <div class="card-header">
          <span>额度使用记录</span>
          <el-select 
            v-model="filterType" 
            placeholder="筛选类型" 
            clearable
            @change="loadTransactions"
            style="width: 150px"
          >
            <el-option label="全部" value="" />
            <el-option label="充值" value="RECHARGE" />
            <el-option label="扣除" value="DEDUCT" />
            <el-option label="调整" value="ADJUST" />
          </el-select>
        </div>
      </template>

      <el-table :data="transactions" stripe>
        <el-table-column prop="transactionType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getTransactionTypeTag(row.transactionType)">
              {{ getTransactionTypeLabel(row.transactionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="120">
          <template #default="{ row }">
            <span :class="row.amount > 0 ? 'amount-positive' : 'amount-negative'">
              {{ row.amount > 0 ? '+' : '' }}{{ row.amount.toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="balanceBefore" label="变更前余额" width="120">
          <template #default="{ row }">
            {{ row.balanceBefore.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="balanceAfter" label="变更后余额" width="120">
          <template #default="{ row }">
            {{ row.balanceAfter.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column prop="createdAt" label="时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { quotaApi, type UserQuota, type QuotaTransaction } from '@/api/quota'

const quota = ref<UserQuota>({
  userId: 0,
  balance: 0,
  totalRecharged: 0,
  totalDeducted: 0
})

const transactions = ref<QuotaTransaction[]>([])
const filterType = ref('')

const loadQuota = async () => {
  try {
    quota.value = await quotaApi.getMyQuota()
  } catch (error: any) {
    ElMessage.error(error.message || '加载额度信息失败')
  }
}

const loadTransactions = async () => {
  try {
    transactions.value = await quotaApi.getMyTransactions(filterType.value || undefined)
  } catch (error: any) {
    ElMessage.error(error.message || '加载额度记录失败')
  }
}

const getTransactionTypeLabel = (type: string) => {
  const labels: Record<string, string> = {
    RECHARGE: '充值',
    DEDUCT: '扣除',
    ADJUST: '调整'
  }
  return labels[type] || type
}

const getTransactionTypeTag = (type: string) => {
  const tags: Record<string, any> = {
    RECHARGE: 'success',
    DEDUCT: 'danger',
    ADJUST: 'warning'
  }
  return tags[type] || 'info'
}

const formatDateTime = (dateTime: string) => {
  return new Date(dateTime).toLocaleString('zh-CN')
}

onMounted(() => {
  loadQuota()
  loadTransactions()
})
</script>

<style scoped>
.quota-page {
  padding: 20px;
}

.quota-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.quota-info {
  display: flex;
  gap: 40px;
}

.quota-item {
  text-align: center;
}

.quota-item .label {
  color: #909399;
  font-size: 14px;
  margin-bottom: 8px;
}

.quota-item .value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.quota-item .value.balance {
  color: #409eff;
  font-size: 36px;
}

.amount-positive {
  color: #67c23a;
}

.amount-negative {
  color: #f56c6c;
}
</style>