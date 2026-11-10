<template>
  <el-dialog
    v-model="visible"
    class="announcement-popup"
    width="min(92vw, 600px)"
    :show-close="false"
    :close-on-click-modal="false"
    append-to-body
    align-center
    aria-label="系统公告"
    @closed="handleClosed"
  >
    <AnnouncementCard
      v-if="current"
      :key="current.id"
      :title="current.title"
      :category="current.category"
      :content="current.content"
      :published-at="current.publishedAt"
      closable
      @close="visible = false"
    >
      <template #footer>
        <div class="ann-popup-progress">
          <template v-if="items.length > 1">
            <div class="ann-popup-dots" role="tablist" aria-label="切换公告">
              <button
                v-for="(item, i) in items"
                :key="item.id"
                type="button"
                role="tab"
                class="ann-popup-dot"
                :class="{ 'is-active': i === index }"
                :aria-selected="i === index"
                :aria-label="`第 ${i + 1} 条：${item.title}`"
                @click="index = i"
              />
            </div>
            <span class="ann-popup-counter">第 {{ index + 1 }} / {{ items.length }} 条公告</span>
          </template>
          <span v-else class="ann-popup-counter">系统公告</span>
        </div>
        <div class="ann-popup-actions">
          <el-button v-if="items.length > 1" :disabled="index === 0" @click="go(-1)">上一条</el-button>
          <el-button v-if="!isLast" type="primary" @click="go(1)">
            下一条<el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
          <el-button v-else type="primary" @click="visible = false">我知道了</el-button>
        </div>
      </template>
    </AnnouncementCard>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ArrowRight } from '@element-plus/icons-vue'
import AnnouncementCard from './AnnouncementCard.vue'
import type { Announcement } from '@/api/announcement'
import { useAnnouncementStore } from '@/stores/announcement'

const store = useAnnouncementStore()
const { popupVisible, unread } = storeToRefs(store)

/** 打开时对未读列表做快照，翻页期间不受 store 变化影响 */
const items = ref<Announcement[]>([])
const index = ref(0)
const viewedIds = new Set<number>()

const visible = computed({
  get: () => popupVisible.value && items.value.length > 0,
  set: (value: boolean) => {
    if (!value) popupVisible.value = false
  }
})

const current = computed(() => items.value[index.value])
const isLast = computed(() => index.value >= items.value.length - 1)

function go(step: number) {
  const next = index.value + step
  if (next < 0 || next >= items.value.length) return
  index.value = next
}

watch(
  popupVisible,
  (open) => {
    if (open) {
      items.value = [...unread.value]
      index.value = 0
      viewedIds.clear()
    }
  },
  { immediate: true }
)

watch(current, (item) => {
  if (item) viewedIds.add(item.id)
}, { immediate: true })

/** 只把真正翻看过的公告标记为已读；未看到的下次登录仍会弹出 */
function handleClosed() {
  const ids = [...viewedIds]
  viewedIds.clear()
  items.value = []
  index.value = 0
  void store.markRead(ids)
}
</script>
