<template>
  <article class="ann-card" :class="[`is-${categoryKey}`, { 'is-flat': flat }]">
    <header class="ann-card-head">
      <span class="ann-card-icon" aria-hidden="true">
        <component :is="categoryIcon" />
      </span>
      <div class="ann-card-heading">
        <span class="ann-card-eyebrow">{{ categoryLabel }}</span>
        <h3 class="ann-card-title">{{ title || '未命名公告' }}</h3>
        <span class="ann-card-time">
          <Clock aria-hidden="true" />
          {{ publishedAt ? `发布于 ${formatAnnouncementTime(publishedAt)}` : '尚未发布' }}
        </span>
      </div>
      <button v-if="closable" type="button" class="ann-card-close" aria-label="关闭公告" @click="emit('close')">
        <Close />
      </button>
    </header>

    <div class="ann-card-body">
      <div
        v-if="html"
        class="announcement-markdown"
        data-testid="announcement-content"
        v-html="html"
      />
      <p v-else class="ann-card-empty">{{ emptyText }}</p>
    </div>

    <footer v-if="$slots.footer" class="ann-card-foot">
      <slot name="footer" />
    </footer>
  </article>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { Bell, Clock, Close, MagicStick, Tools, WarningFilled } from '@element-plus/icons-vue'
import { ANNOUNCEMENT_CATEGORIES, formatAnnouncementTime, type AnnouncementCategory } from '@/api/announcement'
import { renderMarkdown } from '@/utils/markdown'

const props = withDefaults(
  defineProps<{
    title: string
    category: AnnouncementCategory
    content: string
    publishedAt?: string | null
    /** 显示右上角关闭按钮（用户弹窗） */
    closable?: boolean
    /** 无阴影/圆角的嵌入样式（历史详情） */
    flat?: boolean
    emptyText?: string
  }>(),
  {
    publishedAt: null,
    closable: false,
    flat: false,
    emptyText: '暂无内容'
  }
)

const emit = defineEmits<{ (e: 'close'): void }>()

const CATEGORY_ICONS: Record<AnnouncementCategory, Component> = {
  FEATURE: MagicStick,
  MAINTENANCE: Tools,
  IMPORTANT: WarningFilled,
  GENERAL: Bell
}

const safeCategory = computed<AnnouncementCategory>(() =>
  props.category in ANNOUNCEMENT_CATEGORIES ? props.category : 'GENERAL'
)
const categoryKey = computed(() => safeCategory.value.toLowerCase())
const categoryLabel = computed(() => ANNOUNCEMENT_CATEGORIES[safeCategory.value].label)
const categoryIcon = computed(() => CATEGORY_ICONS[safeCategory.value])
const html = computed(() => renderMarkdown(props.content))
</script>
