<template>
  <div class="tax-flow-search" :style="{ width }">
    <el-autocomplete
      v-model="keyword"
      class="tax-flow-search__input"
      :fetch-suggestions="fetchSuggestions"
      :trigger-on-focus="false"
      :debounce="300"
      :placeholder="placeholder"
      :aria-label="placeholder"
      popper-class="tax-flow-search__popper"
      clearable
      maxlength="100"
      value-key="taxNumber"
      @select="handleSelect"
    >
      <template #prefix>
        <el-icon><Search /></el-icon>
      </template>
      <template #default="{ item }">
        <div class="tax-flow-search__option">
          <span class="tax-flow-search__tax">{{ item.taxNumber }}</span>
          <span class="tax-flow-search__company">{{ item.companyName }}</span>
          <span class="tax-flow-search__count">{{ item.invoiceCount }} 张</span>
        </div>
      </template>
    </el-autocomplete>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import {
  invoiceFlowApi,
  TAX_FLOW_MIN_QUERY_LENGTH,
  type TaxFlowScope,
  type TaxNumberSuggestion
} from '@/api/invoiceFlow'

const props = withDefaults(defineProps<{
  scope: TaxFlowScope
  placeholder?: string
  width?: string
}>(), {
  placeholder: '搜索税号查看数据流',
  width: '240px'
})

const emit = defineEmits<{
  (e: 'select', taxNumber: string): void
}>()

const keyword = ref('')

const fetchSuggestions = async (query: string, callback: (items: TaxNumberSuggestion[]) => void) => {
  const normalized = (query || '').trim()
  if (normalized.length < TAX_FLOW_MIN_QUERY_LENGTH) {
    callback([])
    return
  }
  try {
    callback(await invoiceFlowApi.suggest(props.scope, normalized))
  } catch {
    callback([])
  }
}

const handleSelect = (item: Record<string, any>) => {
  const suggestion = item as TaxNumberSuggestion
  keyword.value = suggestion.taxNumber
  emit('select', suggestion.taxNumber)
}
</script>

<style scoped>
.tax-flow-search {
  max-width: 100%;
}

.tax-flow-search__input {
  width: 100%;
}
</style>

<style>
.tax-flow-search__popper .el-autocomplete-suggestion li {
  line-height: 1.4;
  padding: 8px 12px;
}

.tax-flow-search__option {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    'tax count'
    'company count';
  column-gap: 12px;
  row-gap: 2px;
  align-items: center;
}

.tax-flow-search__tax {
  grid-area: tax;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-weight: 600;
  color: var(--color-text, #20252b);
  overflow: hidden;
  text-overflow: ellipsis;
}

.tax-flow-search__company {
  grid-area: company;
  font-size: 12px;
  color: var(--color-text-muted, #74808b);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tax-flow-search__count {
  grid-area: count;
  font-size: 12px;
  color: var(--color-primary, #12715b);
  background: var(--color-primary-soft, #e9f4f0);
  border-radius: 10px;
  padding: 1px 8px;
  white-space: nowrap;
}
</style>
