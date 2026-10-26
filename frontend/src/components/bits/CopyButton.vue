<template>
  <el-button
    :type="type"
    :size="size"
    :plain="plain"
    :link="link"
    :disabled="disabled || copying"
    :loading="copying"
    :class="['copy-btn', customClass]"
    @click="handleCopy"
  >
    <template #icon>
      <el-icon v-if="!copying"><DocumentCopy /></el-icon>
    </template>
    <slot>{{ buttonText }}</slot>
  </el-button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentCopy } from '@element-plus/icons-vue'
import { copyTextToClipboard } from '@/utils/clipboard'

interface Props {
  text: string
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'default'
  size?: 'large' | 'default' | 'small'
  plain?: boolean
  link?: boolean
  disabled?: boolean
  customClass?: string
  successMessage?: string
  errorMessage?: string
  buttonText?: string
}

const props = withDefaults(defineProps<Props>(), {
  type: 'default',
  size: 'small',
  plain: false,
  link: false,
  disabled: false,
  customClass: '',
  successMessage: '已复制到剪贴板',
  errorMessage: '复制失败',
  buttonText: '复制'
})

const copying = ref(false)

const handleCopy = async () => {
  if (copying.value || !props.text) return
  
  copying.value = true
  try {
    await copyTextToClipboard(props.text)
    ElMessage.success(props.successMessage)
  } catch (error: any) {
    console.error('复制失败', error)
    ElMessage.error(props.errorMessage)
  } finally {
    copying.value = false
  }
}
</script>

<style scoped>
.copy-btn {
  margin-left: 4px;
}
</style>
