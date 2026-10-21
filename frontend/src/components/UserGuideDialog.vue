<template>
  <el-dialog
    v-model="visible"
    width="920px"
    class="user-guide-dialog"
    :close-on-click-modal="true"
    :show-close="false"
    append-to-body
    destroy-on-close
    @closed="handleClosed"
  >
    <template #header="{ close }">
      <div class="guide-dialog-header">
        <div class="header-title-box">
          <span class="header-icon-badge"><Reading /></span>
          <div>
            <h3>系统使用说明</h3>
            <p>快速了解额度申请与发票开具全流程</p>
          </div>
        </div>

        <div class="header-controls">
          <el-tooltip content="在独立新页面中打开完整文档" placement="bottom">
            <el-button
              class="open-page-btn"
              size="small"
              plain
              :icon="TopRight"
              @click="handleOpenFullPage"
            >
              在新页面打开
            </el-button>
          </el-tooltip>
          <button type="button" class="close-icon-btn" aria-label="关闭弹窗" @click="close">
            <el-icon><Close /></el-icon>
          </button>
        </div>
      </div>
    </template>

    <div class="guide-dialog-body">
      <UserGuideContent
        :initial-tab="initialTab"
        mode="dialog"
        @navigate="handleNavigate"
      />
    </div>

    <template #footer>
      <div class="guide-dialog-footer">
        <span class="footer-tip">提示：随时可通过页面顶部导航或辅助按钮再次唤出此指南</span>
        <el-button type="primary" @click="visible = false">我知道了</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Close, Reading, TopRight } from '@element-plus/icons-vue'
import UserGuideContent from './UserGuideContent.vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    initialTab?: 'quota' | 'invoice' | 'faq'
  }>(),
  {
    modelValue: false,
    initialTab: 'quota'
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const router = useRouter()

const visible = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit('update:modelValue', val)
})

const handleClosed = () => {
  emit('update:modelValue', false)
}

const handleOpenFullPage = () => {
  visible.value = false
  router.push('/guide')
}

const handleNavigate = (destination: 'quota' | 'invoice') => {
  visible.value = false
  if (destination === 'quota') {
    router.push('/user/quota')
  } else if (destination === 'invoice') {
    router.push('/user')
  }
}
</script>

<style scoped>
:deep(.el-dialog.user-guide-dialog) {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.18);
  max-width: 95vw;
}

:deep(.el-dialog__header) {
  margin: 0;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
}

:deep(.el-dialog__body) {
  padding: 16px 20px;
  max-height: 72vh;
  overflow-y: auto;
  scroll-behavior: smooth;
  background: var(--color-canvas);
}

:deep(.el-dialog__footer) {
  padding: 12px 20px;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface);
}

.guide-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-title-box {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon-badge {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.header-title-box h3 {
  margin: 0 0 2px 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
}

.header-title-box p {
  margin: 0;
  font-size: 12.5px;
  color: var(--color-text-secondary);
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}

.close-icon-btn {
  background: transparent;
  border: none;
  font-size: 18px;
  color: var(--color-text-secondary);
  cursor: pointer;
  padding: 6px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.close-icon-btn:hover {
  background: var(--color-surface-muted);
  color: var(--color-text);
}

.guide-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer-tip {
  font-size: 12.5px;
  color: var(--color-text-muted);
}

@media (max-width: 640px) {
  .footer-tip {
    display: none;
  }
}
</style>
