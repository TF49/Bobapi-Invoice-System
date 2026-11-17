<template>
  <el-drawer
    :model-value="modelValue"
    size="min(640px, 100vw)"
    append-to-body
    destroy-on-close
    class="tax-flow-drawer"
    :with-header="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="tf">
      <header class="tf-head">
        <div class="tf-head__top">
          <span class="tf-eyebrow">税号数据流</span>
          <el-button class="tf-close" link aria-label="关闭" @click="emit('update:modelValue', false)">
            <el-icon :size="18"><Close /></el-icon>
          </el-button>
        </div>
        <div class="tf-tax">
          <h2 class="tf-tax__number">{{ result?.taxNumber || taxNumber }}</h2>
          <el-button
            v-if="result?.taxNumber || taxNumber"
            link
            size="small"
            :icon="CopyDocument"
            aria-label="复制税号"
            @click="copyTax"
          />
        </div>
        <p v-if="result?.companyNames?.length" class="tf-companies">{{ result.companyNames.join(' / ') }}</p>

        <div v-if="result && result.total > 0" class="tf-stats">
          <div class="tf-stat">
            <span class="tf-stat__label">发票</span>
            <strong class="tf-stat__value">{{ result.summary.invoiceCount }} 张</strong>
          </div>
          <div class="tf-stat">
            <span class="tf-stat__label">开票总额</span>
            <strong class="tf-stat__value">{{ formatMoney(result.summary.totalAmount) }}</strong>
          </div>
          <div class="tf-stat">
            <span class="tf-stat__label">当前占用额度</span>
            <strong class="tf-stat__value tf-stat__value--danger">{{ formatMoney(result.summary.activeDeducted) }}</strong>
          </div>
        </div>
        <div v-if="result && result.total > 0" class="tf-status-row">
          <span v-if="result.summary.pendingCount" class="tf-chip tf-chip--primary">待开票 {{ result.summary.pendingCount }}</span>
          <span v-if="result.summary.completedCount" class="tf-chip tf-chip--success">已开票 {{ result.summary.completedCount }}</span>
          <span v-if="result.summary.redFlushedCount" class="tf-chip tf-chip--warning">已红冲 {{ result.summary.redFlushedCount }}</span>
          <span v-if="result.summary.cancelledCount" class="tf-chip tf-chip--info">已取消 {{ result.summary.cancelledCount }}</span>
        </div>
      </header>

      <section v-loading="loading && !result" class="tf-body">
        <div v-if="loadError" class="tf-empty">
          <strong>加载失败</strong>
          <span>{{ loadError }}</span>
          <el-button size="small" @click="load(1)">重试</el-button>
        </div>

        <div v-else-if="result && result.total === 0" class="tf-empty">
          <strong>未找到该税号的发票</strong>
          <span>请确认税号是否正确</span>
        </div>

        <template v-else-if="result">
          <article
            v-for="(invoice, index) in result.invoices"
            :key="invoice.id"
            class="tf-card"
            :class="{ 'is-open': isExpanded(invoice.id, index) }"
          >
            <button
              type="button"
              class="tf-card__head"
              :aria-expanded="isExpanded(invoice.id, index)"
              @click="toggle(invoice.id, index)"
            >
              <div class="tf-card__title">
                <span class="tf-card__id">#{{ invoice.id }}</span>
                <span class="tf-card__company">{{ invoice.companyName }}</span>
              </div>
              <div class="tf-card__meta">
                <span class="tf-chip" :class="`tf-chip--${invoiceStatusLabel(invoice).tone}`">
                  {{ invoiceStatusLabel(invoice).label }}
                </span>
                <span class="tf-card__tag">{{ invoice.invoiceCategory === 'VAT_SPECIAL' ? '专票' : '普票' }}</span>
                <span class="tf-card__tag">{{ submissionLabel(invoice) }}</span>
                <span v-if="invoice.username" class="tf-card__tag tf-card__tag--owner">{{ invoice.username }}</span>
                <span class="tf-card__date">{{ formatFlowDateTime(invoice.createdAt) }}</span>
              </div>
              <strong class="tf-card__amount">{{ formatMoney(invoice.amount) }}</strong>
              <el-icon class="tf-card__chevron"><ArrowDown /></el-icon>
            </button>

            <div v-if="isExpanded(invoice.id, index)" class="tf-card__body">
              <div v-if="invoice.events.length === 0" class="tf-legacy">
                该发票创建于数据流功能上线前，暂无流转记录。
                <span>创建于 {{ formatFlowDateTime(invoice.createdAt) }}，扣除额度 {{ formatMoney(invoice.deductedQuota) }}</span>
              </div>

              <el-timeline v-else class="tf-timeline">
                <el-timeline-item
                  v-for="event in invoice.events"
                  :key="event.id"
                  :timestamp="formatFlowDateTime(event.createdAt)"
                  :color="eventColor(event)"
                  placement="top"
                >
                  <div class="tf-event">
                    <div class="tf-event__line">
                      <span class="tf-event__label">{{ eventMeta(event.eventType).label }}</span>
                      <span class="tf-event__operator">{{ operatorLabel(event.operatorType) }}</span>
                      <span
                        v-if="event.quotaDelta != null && Number(event.quotaDelta) !== 0"
                        class="tf-event__delta"
                        :class="Number(event.quotaDelta) < 0 ? 'is-minus' : 'is-plus'"
                      >
                        {{ formatSignedMoney(event.quotaDelta) }}
                      </span>
                    </div>
                    <p v-if="event.detail" class="tf-event__detail">{{ event.detail }}</p>
                    <p v-if="event.balanceAfter != null || event.quotaTransactionId" class="tf-event__ref">
                      <span v-if="event.balanceAfter != null">余额 {{ formatMoney(event.balanceAfter) }}</span>
                      <span v-if="event.quotaTransactionId">流水 #{{ String(event.quotaTransactionId).padStart(4, '0') }}</span>
                    </p>
                  </div>
                </el-timeline-item>
              </el-timeline>
            </div>
          </article>

          <div v-if="hasMore" class="tf-more">
            <el-button :loading="loading" @click="load(result.page + 1)">
              加载更多（{{ result.invoices.length }} / {{ result.total }}）
            </el-button>
          </div>
        </template>
      </section>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, Close, CopyDocument } from '@element-plus/icons-vue'
import { invoiceFlowApi, type TaxFlowResult, type TaxFlowScope } from '@/api/invoiceFlow'
import { copyTextToClipboard } from '@/utils/clipboard'
import {
  eventColor,
  eventMeta,
  formatFlowDateTime,
  formatMoney,
  formatSignedMoney,
  invoiceStatusLabel,
  operatorLabel,
  submissionLabel
} from '@/utils/taxFlow'

const PAGE_SIZE = 20
const DEFAULT_EXPANDED = 3

const props = defineProps<{
  modelValue: boolean
  taxNumber: string | null
  scope: TaxFlowScope
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const result = ref<TaxFlowResult | null>(null)
const loading = ref(false)
const loadError = ref('')
const toggled = ref<Set<number>>(new Set())
let requestSeq = 0

const hasMore = computed(() => !!result.value && result.value.invoices.length < result.value.total)

const isExpanded = (invoiceId: number, index: number) => {
  const defaultOpen = index < DEFAULT_EXPANDED
  return toggled.value.has(invoiceId) ? !defaultOpen : defaultOpen
}

const toggle = (invoiceId: number, _index: number) => {
  const next = new Set(toggled.value)
  if (next.has(invoiceId)) next.delete(invoiceId)
  else next.add(invoiceId)
  toggled.value = next
}

const load = async (page: number) => {
  if (!props.taxNumber) return
  const seq = ++requestSeq
  loading.value = true
  loadError.value = ''
  try {
    const data = await invoiceFlowApi.getFlow(props.scope, props.taxNumber, page, PAGE_SIZE)
    if (seq !== requestSeq) return
    if (page > 1 && result.value) {
      result.value = { ...data, invoices: [...result.value.invoices, ...data.invoices] }
    } else {
      result.value = data
    }
  } catch (error: any) {
    if (seq !== requestSeq) return
    loadError.value = error?.message || '请稍后重试'
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

watch(
  () => [props.modelValue, props.taxNumber, props.scope.mode, props.scope.userId] as const,
  ([open]) => {
    if (!open) return
    result.value = null
    toggled.value = new Set()
    load(1)
  },
  { immediate: true }
)

const copyTax = async () => {
  const value = result.value?.taxNumber || props.taxNumber
  if (!value) return
  try {
    await copyTextToClipboard(value)
    ElMessage.success('税号已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}
</script>

<style scoped>
.tf {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  color: var(--color-text, #20252b);
}

.tf-head {
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--color-border, #e2e7e5);
  background: var(--color-surface-muted, #f8faf9);
}

.tf-head__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tf-eyebrow {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--color-primary, #12715b);
}

.tf-close {
  color: var(--color-text-muted, #74808b);
}

.tf-tax {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
}

.tf-tax__number {
  margin: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 20px;
  font-weight: 700;
  word-break: break-all;
}

.tf-companies {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--color-text-secondary, #4d5863);
}

.tf-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.tf-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--color-border, #e2e7e5);
  border-radius: 8px;
  background: var(--color-surface, #fff);
}

.tf-stat__label {
  font-size: 12px;
  color: var(--color-text-muted, #74808b);
}

.tf-stat__value {
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}

.tf-stat__value--danger {
  color: var(--color-danger, #c9463d);
}

.tf-status-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.tf-chip {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 20px;
  white-space: nowrap;
}

.tf-chip--primary { color: #12715b; background: #e9f4f0; }
.tf-chip--success { color: #2f7d58; background: #e7f5ee; }
.tf-chip--warning { color: #b66d0b; background: #fff4dc; }
.tf-chip--danger { color: #c9463d; background: #fbeceb; }
.tf-chip--info { color: #4d5863; background: #eef1f3; }

.tf-body {
  flex: 1;
  padding: 18px 24px 28px;
  min-height: 200px;
}

.tf-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 48px 0;
  color: var(--color-text-muted, #74808b);
  font-size: 13px;
}

.tf-empty strong {
  color: var(--color-text, #20252b);
  font-size: 15px;
}

.tf-card {
  border: 1px solid var(--color-border, #e2e7e5);
  border-radius: 10px;
  background: var(--color-surface, #fff);
  margin-bottom: 12px;
  overflow: hidden;
  transition: border-color 0.2s;
}

.tf-card.is-open {
  border-color: var(--color-border-strong, #cfd7d4);
}

.tf-card__head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 18px;
  grid-template-areas:
    'title amount chevron'
    'meta meta meta';
  gap: 6px 10px;
  width: 100%;
  padding: 12px 14px;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font: inherit;
  color: inherit;
}

.tf-card__head:hover {
  background: var(--color-surface-muted, #f8faf9);
}

.tf-card__head:focus-visible {
  outline: 2px solid var(--color-primary, #12715b);
  outline-offset: -2px;
}

.tf-card__title {
  grid-area: title;
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.tf-card__id {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--color-text-muted, #74808b);
}

.tf-card__company {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tf-card__amount {
  grid-area: amount;
  font-variant-numeric: tabular-nums;
}

.tf-card__chevron {
  grid-area: chevron;
  align-self: center;
  color: var(--color-text-muted, #74808b);
  transition: transform 0.2s;
}

.tf-card.is-open .tf-card__chevron {
  transform: rotate(180deg);
}

.tf-card__meta {
  grid-area: meta;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.tf-card__tag {
  font-size: 12px;
  color: var(--color-text-secondary, #4d5863);
  padding: 0 6px;
  border: 1px solid var(--color-border, #e2e7e5);
  border-radius: 4px;
  line-height: 20px;
}

.tf-card__tag--owner {
  color: var(--color-primary, #12715b);
  border-color: var(--el-color-primary-light-7, #afcfc7);
}

.tf-card__date {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-muted, #74808b);
  font-variant-numeric: tabular-nums;
}

.tf-card__body {
  padding: 4px 16px 6px 18px;
  border-top: 1px dashed var(--color-border, #e2e7e5);
}

.tf-legacy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 0;
  font-size: 13px;
  color: var(--color-text-secondary, #4d5863);
}

.tf-legacy span {
  font-size: 12px;
  color: var(--color-text-muted, #74808b);
}

.tf-timeline {
  padding: 14px 0 0;
}

.tf-timeline :deep(.el-timeline-item__timestamp) {
  font-variant-numeric: tabular-nums;
}

.tf-event__line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.tf-event__label {
  font-weight: 600;
}

.tf-event__operator {
  font-size: 12px;
  color: var(--color-text-muted, #74808b);
  background: var(--color-canvas, #f4f6f7);
  border-radius: 4px;
  padding: 0 6px;
  line-height: 18px;
}

.tf-event__delta {
  margin-left: auto;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.tf-event__delta.is-minus { color: var(--color-danger, #c9463d); }
.tf-event__delta.is-plus { color: #2f9e6f; }

.tf-event__detail {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--color-text-secondary, #4d5863);
  word-break: break-word;
}

.tf-event__ref {
  display: flex;
  gap: 12px;
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-text-muted, #74808b);
  font-variant-numeric: tabular-nums;
}

.tf-more {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}

@media (max-width: 640px) {
  .tf-head,
  .tf-body {
    padding-left: 16px;
    padding-right: 16px;
  }

  .tf-stats {
    gap: 6px;
  }

  .tf-stat {
    padding: 8px;
  }

  .tf-stat__label {
    font-size: 11px;
  }

  .tf-stat__value {
    font-size: 14px;
  }

  .tf-card__date {
    margin-left: 0;
    width: 100%;
  }
}
</style>

<style>
.tax-flow-drawer .el-drawer__body {
  padding: 0;
}
</style>
