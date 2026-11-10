<template>
  <el-dialog
    v-model="visible"
    class="announcement-history-dialog"
    width="min(94vw, 980px)"
    :show-close="false"
    append-to-body
    destroy-on-close
    aria-label="系统公告"
    @open="handleOpen"
  >
    <template #header="{ close }">
      <div class="ann-history-head">
        <div class="ann-history-heading">
          <span class="ann-history-badge"><Promotion /></span>
          <div>
            <h3>系统公告</h3>
            <p>新功能、维护安排与重要通知</p>
          </div>
        </div>
        <button type="button" class="ann-card-close" aria-label="关闭系统公告" @click="close">
          <Close />
        </button>
      </div>
      <div class="ann-history-tabs" role="tablist" aria-label="按分类筛选公告">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          role="tab"
          class="ann-history-tab"
          :class="[`is-${tab.value.toLowerCase()}`, { 'is-active': category === tab.value }]"
          :aria-selected="category === tab.value"
          @click="selectCategory(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>
    </template>

    <div class="ann-history-layout">
      <aside class="ann-history-sidebar" v-loading="loading">
        <ul v-if="records.length > 0" class="ann-history-list" aria-label="公告列表">
          <li v-for="item in records" :key="item.id">
            <button
              type="button"
              class="ann-history-item"
              :class="[`is-${item.category.toLowerCase()}`, { 'is-active': selected?.id === item.id }]"
              :aria-current="selected?.id === item.id ? 'true' : undefined"
              @click="select(item)"
            >
              <span class="ann-history-dot" aria-hidden="true" />
              <span class="ann-history-item-main">
                <span class="ann-history-item-title">
                  <strong>{{ item.title }}</strong>
                  <em v-if="unreadIds.has(item.id)" class="ann-history-new">NEW</em>
                </span>
                <span class="ann-history-item-meta">
                  {{ categoryLabel(item.category) }} · {{ formatShortDate(item.publishedAt) }}
                </span>
              </span>
            </button>
          </li>
        </ul>
        <div v-else-if="!loading" class="ann-history-empty">
          <Promotion aria-hidden="true" />
          <span>暂无公告</span>
        </div>

        <div v-if="total > pageSize" class="ann-history-pagination">
          <el-pagination
            v-model:current-page="page"
            :page-size="pageSize"
            :total="total"
            layout="prev, pager, next"
            small
            @current-change="load"
          />
        </div>
      </aside>

      <section class="ann-history-detail" aria-live="polite">
        <AnnouncementCard
          v-if="selected"
          :key="selected.id"
          flat
          :title="selected.title"
          :category="selected.category"
          :content="selected.content"
          :published-at="selected.publishedAt"
        />
        <div v-else-if="!loading" class="ann-history-empty">
          <span>选择左侧公告查看详情</span>
        </div>
      </section>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Close, Promotion } from '@element-plus/icons-vue'
import AnnouncementCard from './AnnouncementCard.vue'
import {
  ANNOUNCEMENT_CATEGORIES,
  announcementApi,
  type Announcement,
  type AnnouncementCategory
} from '@/api/announcement'
import { useAnnouncementStore } from '@/stores/announcement'

type CategoryFilter = AnnouncementCategory | 'ALL'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const store = useAnnouncementStore()
const unreadIds = computed(() => new Set(store.unread.map((item) => item.id)))

const tabs: { value: CategoryFilter; label: string }[] = [
  { value: 'ALL', label: '全部' },
  ...(Object.keys(ANNOUNCEMENT_CATEGORIES) as AnnouncementCategory[]).map((key) => ({
    value: key,
    label: ANNOUNCEMENT_CATEGORIES[key].label
  }))
]

const pageSize = 10
const page = ref(1)
const total = ref(0)
const category = ref<CategoryFilter>('ALL')
const records = ref<Announcement[]>([])
const selected = ref<Announcement | null>(null)
const loading = ref(false)

function categoryLabel(value: AnnouncementCategory) {
  return (ANNOUNCEMENT_CATEGORIES[value] ?? ANNOUNCEMENT_CATEGORIES.GENERAL).label
}

/** 2026-11-10T09:30:00 → 11-10；非今年显示完整日期 */
function formatShortDate(value: string | null) {
  if (!value) return '—'
  const date = value.slice(0, 10)
  return date.startsWith(String(new Date().getFullYear())) ? date.slice(5) : date
}

function select(item: Announcement) {
  selected.value = item
  // 在历史列表中看过的未读公告同步标记为已读，避免下次登录重复弹窗
  if (unreadIds.value.has(item.id)) {
    void store.markRead([item.id])
  }
}

async function load() {
  loading.value = true
  try {
    const result = await announcementApi.getHistory({
      page: page.value,
      pageSize,
      category: category.value === 'ALL' ? undefined : category.value
    })
    records.value = result.records
    total.value = result.total
    const first = result.records[0]
    if (first) {
      select(first)
    } else {
      selected.value = null
    }
  } catch (error) {
    console.error('加载历史公告失败:', error)
  } finally {
    loading.value = false
  }
}

function selectCategory(value: CategoryFilter) {
  if (category.value === value) return
  category.value = value
  page.value = 1
  void load()
}

function handleOpen() {
  page.value = 1
  category.value = 'ALL'
  selected.value = null
  void load()
}
</script>
