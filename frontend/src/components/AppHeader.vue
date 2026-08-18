<template>
  <header class="app-header">
    <div>
      <h1>{{ title }}</h1>
      <span>{{ userStore.username }} · {{ roleLabel }}</span>
    </div>
    <el-button :icon="SwitchButton" @click="handleLogout">退出登录</el-button>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

defineProps<{ title: string }>()

const router = useRouter()
const userStore = useUserStore()
const roleLabel = computed(() => userStore.role === 'ADMIN' ? '管理员' : '普通用户')

const handleLogout = async () => {
  userStore.logout()
  ElMessage.success('已退出登录')
  await router.replace('/login')
}
</script>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #dcdfe6;
}

h1 {
  margin: 0 0 6px;
  font-size: 22px;
  letter-spacing: 0;
}

span {
  color: #606266;
  font-size: 14px;
}

@media (max-width: 600px) {
  .app-header {
    align-items: flex-start;
  }

  h1 {
    font-size: 18px;
  }
}
</style>
