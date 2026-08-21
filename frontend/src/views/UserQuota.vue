<template>
  <div class="workspace-shell">
    <AppHeader title="我的额度" />

    <main class="workspace-content">
      <AnimatedContent tag="section" class="stats-grid quota-stats-grid" :distance="10">
        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon success"><Wallet /></span>
            <div class="stat-copy">
              <span class="stat-label">当前余额</span>
              <strong class="stat-value quota-stat">
                <CountUp :value="quota.balance" :decimals="2" prefix="¥" />
              </strong>
            </div>
            <p class="stat-note">可用于开票的剩余余额</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon neutral"><Coin /></span>
            <div class="stat-copy">
              <span class="stat-label">累计充值</span>
              <strong class="stat-value">
                <CountUp :value="quota.totalRecharged" :decimals="2" prefix="¥" />
              </strong>
            </div>
            <p class="stat-note">历史获得的算力额度</p>
          </div>
        </SpotlightCard>

        <SpotlightCard class="stat-card">
          <div class="stat-card-content">
            <span class="stat-icon warning"><Remove /></span>
            <div class="stat-copy">
              <span class="stat-label">累计扣除</span>
              <strong class="stat-value">
                <CountUp :value="quota.totalDeducted" :decimals="2" prefix="¥" />
              </strong>
            </div>
            <p class="stat-note">已开票或扣除的额度</p>
          </div>
        </SpotlightCard>
      </AnimatedContent>

      <AnimatedContent tag="section" class="surface-panel records-panel" :delay="80">
        <div class="panel-header">
          <div class="panel-heading">
            <span class="panel-heading-icon"><List /></span>
            <div>
              <h2>额度使用记录</h2>
              <p>查看变动明细与余额轨迹</p>
            </div>
          </div>
          <div class="filter-control">
            <span class="filter-label"><Filter /> 筛选类型</span>
            <el-select
              v-model="filterType"
              placeholder="全部类型"
              clearable
              style="width: 140px"
              @change="loadTransactions"
            >
              <el-option label="全部类型" value="" />
              <el-option label="充值" value="RECHARGE" />
              <el-option label="扣除" value="DEDUCT" />
              <el-option label="调整" value="ADJUST" />
            </el-select>
          </div>
        </div>

        <div v-if="!loading && transactions.length === 0" class="table-empty-state">
          <span><Wallet /></span>
          <strong>暂无额度使用记录</strong>
        </div>

        <div v-else class="table-scroll desktop-records">
          <el-table :data="transactions" v-loading="loading" class="records-table">
            <el-table-column prop="id" label="记录编号" width="112">
              <template #default="{ row }">
                <span class="record-id">#{{ String(row.id).padStart(4, '0') }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="transactionType" label="变动类型" width="124" align="center">
              <template #default="{ row }">
                <el-tag class="status-tag" :class="getTransactionTagClass(row.transactionType)">
                  <i class="status-dot"></i>
                  {{ getTransactionTypeLabel(row.transactionType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="amount" label="变动金额" width="150" align="right">
              <template #default="{ row }">
                <span class="money-cell" :class="row.amount >= 0 ? 'amount-positive' : 'amount-negative'">
                  {{ row.amount > 0 ? '+' : '' }}{{ formatCurrency(row.amount) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="balanceBefore" label="变更前余额" width="150" align="right">
              <template #default="{ row }">
                <span class="money-cell text-muted">{{ formatCurrency(row.balanceBefore) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="balanceAfter" label="变更后余额" width="150" align="right">
              <template #default="{ row }">
                <span class="money-cell">{{ formatCurrency(row.balanceAfter) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注说明" min-width="200">
              <template #default="{ row }">
                <span class="remark-cell">{{ row.remark || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="记录时间" width="168">
              <template #default="{ row }">
                <div class="date-cell">
                  <span>{{ formatDateParts(row.createdAt).date }}</span>
                  <small>{{ formatDateParts(row.createdAt).time }}</small>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="transactions.length > 0" v-loading="loading" class="mobile-records">
          <article v-for="row in transactions" :key="row.id" class="transaction-record-card">
            <div class="record-card-header">
              <div class="type-cell">
                <el-tag class="status-tag" :class="getTransactionTagClass(row.transactionType)">
                  <i class="status-dot"></i>
                  {{ getTransactionTypeLabel(row.transactionType) }}
                </el-tag>
                <span class="record-id">#{{ String(row.id).padStart(4, '0') }}</span>
              </div>
              <strong class="money-cell" :class="row.amount >= 0 ? 'amount-positive' : 'amount-negative'">
                {{ row.amount > 0 ? '+' : '' }}{{ formatCurrency(row.amount) }}
              </strong>
            </div>

            <dl class="record-card-details">
              <div>
                <dt>变更前余额</dt>
                <dd>{{ formatCurrency(row.balanceBefore) }}</dd>
              </div>
              <div>
                <dt>变更后余额</dt>
                <dd>{{ formatCurrency(row.balanceAfter) }}</dd>
              </div>
              <div>
                <dt>备注说明</dt>
                <dd>{{ row.remark || '-' }}</dd>
              </div>
              <div>
                <dt>记录时间</dt>
                <dd>{{ formatDateTime(row.createdAt) }}</dd>
              </div>
            </dl>
          </article>
        </div>
      </AnimatedContent>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Coin, Filter, List, Remove, Wallet } from '@element-plus/icons-vue'
import { quotaApi, type QuotaTransaction, type UserQuota } from '@/api/quota'
import AppHeader from '@/components/AppHeader.vue'
import AnimatedContent from '@/components/bits/AnimatedContent.vue'
import CountUp from '@/components/bits/CountUp.vue'
import SpotlightCard from '@/components/bits/SpotlightCard.vue'

const loading = ref(false)
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
  loading.value = true
  try {
    transactions.value = await quotaApi.getMyTransactions(filterType.value || undefined)
  } catch (error: any) {
    ElMessage.error(error.message || '加载额度记录失败')
  } finally {
    loading.value = false
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

const getTransactionTagClass = (type: string) => {
  const classes: Record<string, string> = {
    RECHARGE: 'is-completed',
    DEDUCT: 'is-danger',
    ADJUST: 'is-pending'
  }
  return classes[type] || ''
}

const formatCurrency = (amount: number) => new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  minimumFractionDigits: 2
}).format(Number(amount))

const formatDateTime = (value: string) => {
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

const formatDateParts = (value: string) => {
  const formatted = formatDateTime(value)
  const separatorIndex = formatted.lastIndexOf(' ')
  if (separatorIndex < 0) return { date: formatted, time: '' }
  return {
    date: formatted.slice(0, separatorIndex),
    time: formatted.slice(separatorIndex + 1)
  }
}

onMounted(() => {
  loadQuota()
  loadTransactions()
})
</script>

<style scoped>
.quota-stats-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.record-id {
  display: inline-flex;
  padding: 4px 7px;
  color: #61716b;
  background: #f1f5f3;
  border: 1px solid #e1e9e6;
  border-radius: 5px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.02em;
}

.status-tag.is-danger {
  color: #c9463d;
  background: #fdf2f1;
}

.amount-positive {
  color: #12715b;
}

.amount-negative {
  color: #c9463d;
}

.text-muted {
  color: var(--color-text-muted);
}

.remark-cell {
  color: var(--color-text);
  font-size: 13px;
}

.records-panel {
  overflow: hidden;
}

.records-table :deep(.el-table__row td) {
  transition: background-color 180ms ease;
}

.records-table :deep(.el-table__row:hover td) {
  background: #f5faf8 !important;
}

.date-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.date-cell small {
  color: var(--color-text-muted);
  font-size: 11px;
}

.mobile-records {
  display: none;
}

@media (max-width: 900px) {
  .quota-stats-grid {
    grid-template-columns: repeat(1, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .desktop-records {
    display: none;
  }

  .mobile-records {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 16px;
  }

  .transaction-record-card {
    padding: 14px 16px;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: 8px;
  }

  .record-card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  .type-cell {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .record-card-details {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px 16px;
    margin: 0;
  }

  .record-card-details dt {
    color: var(--color-text-muted);
    font-size: 11px;
  }

  .record-card-details dd {
    margin: 2px 0 0;
    color: var(--color-text);
    font-size: 13px;
    font-weight: 600;
  }
}
</style>