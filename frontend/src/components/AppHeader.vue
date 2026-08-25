<template>
  <header class="app-header">
    <div class="header-brand">
      <span class="header-brand-mark"><Tickets /></span>
      <div class="header-copy">
        <span class="product-name">BOBAPI 发票管理系统</span>
        <h1>{{ title }}</h1>
      </div>
    </div>
    <nav v-if="userStore.role === 'ADMIN'" class="admin-nav" aria-label="管理员功能导航">
      <router-link to="/dashboard" exact-active-class="is-active">
        <DataAnalysis />
        <span>数据概览</span>
      </router-link>
      <router-link to="/admin" exact-active-class="is-active">
        <Files />
        <span>发票管理</span>
      </router-link>
      <router-link to="/admin/recharge-requests" exact-active-class="is-active">
        <Tickets />
        <span>充值审核</span>
      </router-link>
      <router-link to="/admin/users" exact-active-class="is-active">
        <UserFilled />
        <span>用户管理</span>
      </router-link>
    </nav>
    <nav v-else-if="userStore.role === 'INVOICE_CLERK'" class="admin-nav" aria-label="开票员功能导航">
      <router-link to="/admin" exact-active-class="is-active">
        <Files />
        <span>发票管理</span>
      </router-link>
    </nav>
    <nav v-else class="user-nav" aria-label="用户功能导航">
      <router-link to="/user" exact-active-class="is-active">
        <Files />
        <span>我的发票</span>
      </router-link>
      <router-link to="/user/quota" exact-active-class="is-active">
        <Wallet />
        <span>我的额度</span>
      </router-link>
    </nav>
    <div class="header-actions">
      <div v-if="userStore.role === 'ADMIN'" class="notification-bell-wrapper">
        <el-tooltip content="充值申请待审核" placement="bottom" :show-after="300">
          <div class="notification-bell" role="button" aria-label="待审核充值申请" @click="handleBellClick">
            <el-badge :value="pendingCount" :max="99" :hidden="pendingCount === 0" class="bell-badge">
              <Bell />
            </el-badge>
          </div>
        </el-tooltip>
      </div>
      <div class="user-chip">
        <span class="user-avatar">{{ userInitial }}</span>
        <span class="user-copy">
          <strong>{{ userStore.username }}</strong>
          <small>{{ roleLabel }}</small>
        </span>
      </div>
      <el-button :icon="SwitchButton" plain aria-label="退出登录" @click="handleLogout">退出登录</el-button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, DataAnalysis, Files, SwitchButton, Tickets, UserFilled, Wallet } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { rechargeRequestApi } from '@/api/rechargeRequest'

defineProps<{ title: string }>()

const router = useRouter()
const userStore = useUserStore()
const pendingCount = ref(0)
let timer: number | null = null
let broadcastChannel: BroadcastChannel | null = null

const roleLabel = computed(() => {
  if (userStore.role === 'ADMIN') return '管理员'
  if (userStore.role === 'INVOICE_CLERK') return '开票员'
  return '普通用户'
})

const userInitial = computed(() => userStore.username.trim().slice(0, 1).toUpperCase() || 'U')

const handleLogout = async () => {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
  userStore.logout()
  ElMessage.success('已退出登录')
  await router.replace('/login')
}

const handleBellClick = () => {
  router.push('/admin/recharge-requests')
}

const loadPendingCount = async () => {
  if (userStore.role === 'ADMIN' && userStore.token) {
    try {
      const count = await rechargeRequestApi.getPendingCount()
      pendingCount.value = Number(count) || 0
    } catch (error: any) {
      // 忽略未登录或网络中断静默处理
    }
  }
}

const handleRefreshEvent = () => {
  loadPendingCount()
}

onMounted(() => {
  loadPendingCount()

  // 1. 定时每 8 秒轮询一次
  timer = window.setInterval(loadPendingCount, 8000)

  // 2. 跨标签页即时同步
  if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
    try {
      broadcastChannel = new BroadcastChannel('bobapi-recharge-events')
      broadcastChannel.onmessage = () => {
        loadPendingCount()
      }
    } catch (_) {}
  }

  // 3. 标签页激活 / 窗口聚焦 / 自定义事件即时拉取
  window.addEventListener('visibilitychange', handleRefreshEvent)
  window.addEventListener('focus', handleRefreshEvent)
  window.addEventListener('recharge-created', handleRefreshEvent)
  window.addEventListener('recharge-reviewed', handleRefreshEvent)
})

onBeforeUnmount(() => {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
  if (broadcastChannel) {
    broadcastChannel.close()
    broadcastChannel = null
  }
  window.removeEventListener('visibilitychange', handleRefreshEvent)
  window.removeEventListener('focus', handleRefreshEvent)
  window.removeEventListener('recharge-created', handleRefreshEvent)
  window.removeEventListener('recharge-reviewed', handleRefreshEvent)
})
</script>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 82px;
  padding: 14px 32px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.header-brand,
.header-actions,
.user-chip,
.admin-nav,
.admin-nav a {
  display: flex;
  align-items: center;
}

.header-brand {
  min-width: 0;
  gap: 13px;
}

.header-brand-mark {
  display: grid;
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
  place-items: center;
  color: #fff;
  background: var(--color-primary);
  border-radius: 7px;
}

.header-brand-mark :deep(svg) {
  width: 22px;
  height: 22px;
}

.header-copy {
  min-width: 0;
}

.product-name {
  display: block;
  margin-bottom: 2px;
  color: var(--color-text-muted);
  font-size: 11px;
  font-weight: 650;
}

h1 {
  overflow: hidden;
  margin: 0;
  color: var(--color-text);
  font-size: 19px;
  font-weight: 680;
  line-height: 1.35;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-bell-wrapper {
  display: flex;
  align-items: center;
}

.notification-bell {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  cursor: pointer;
  color: var(--color-text-muted);
  border-radius: 8px;
  transition: background-color 160ms ease, color 160ms ease;
}

.notification-bell:hover {
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

.bell-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.bell-badge :deep(svg) {
  width: 20px;
  height: 20px;
}

.bell-badge :deep(.el-badge__content) {
  background-color: #e53935 !important;
  color: #ffffff !important;
  font-weight: 700;
  font-size: 11px;
  height: 18px;
  line-height: 18px;
  min-width: 18px;
  padding: 0 5px;
  border-radius: 9px;
  border: 2px solid var(--color-surface, #ffffff);
  box-shadow: 0 2px 4px rgba(229, 57, 53, 0.35);
}

.admin-nav {
  flex: 0 0 auto;
  padding: 3px;
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
  border-radius: 7px;
}

.admin-nav a {
  gap: 6px;
  min-height: 34px;
  padding: 0 12px;
  color: var(--color-text-muted);
  border-radius: 5px;
  font-size: 12px;
  font-weight: 650;
  text-decoration: none;
  transition: color 160ms ease, background-color 160ms ease, box-shadow 160ms ease;
}

.admin-nav a:hover {
  color: var(--color-primary);
}

.admin-nav a.is-active {
  color: var(--color-primary);
  background: var(--color-surface);
  box-shadow: 0 1px 3px rgba(24, 39, 34, 0.1);
}

.admin-nav svg {
  width: 15px;
  height: 15px;
}

.user-nav {
  flex: 0 0 auto;
  padding: 3px;
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
  border-radius: 7px;
}

.user-nav a {
  gap: 6px;
  min-height: 34px;
  padding: 0 12px;
  color: var(--color-text-muted);
  border-radius: 5px;
  font-size: 12px;
  font-weight: 650;
  text-decoration: none;
  transition: color 160ms ease, background-color 160ms ease, box-shadow 160ms ease;
}

.user-nav a:hover {
  color: var(--color-primary);
}

.user-nav a.is-active {
  color: var(--color-primary);
  background: var(--color-surface);
  box-shadow: 0 1px 3px rgba(24, 39, 34, 0.1);
}

.user-nav svg {
  width: 15px;
  height: 15px;
}

.user-chip {
  gap: 9px;
}

.user-avatar {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  color: var(--color-primary);
  background: var(--color-primary-soft);
  border: 1px solid #d5e6e0;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 700;
}

.user-copy {
  display: grid;
  gap: 1px;
  min-width: 72px;
}

.user-copy strong {
  overflow: hidden;
  max-width: 136px;
  color: var(--color-text);
  font-size: 12px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-copy small {
  color: var(--color-text-muted);
  font-size: 10px;
}

@media (max-width: 600px) {
  .app-header {
    flex-wrap: wrap;
    gap: 12px;
    min-height: 72px;
    padding: 12px 16px;
  }

  .header-brand-mark {
    width: 38px;
    height: 38px;
  }

  .product-name,
  .user-chip {
    display: none;
  }

  h1 {
    font-size: 17px;
  }

  .header-actions :deep(.el-button span) {
    display: none;
  }

  .header-actions :deep(.el-button) {
    width: 42px;
    padding: 0;
  }

  .admin-nav {
    order: 3;
    width: 100%;
  }

  .admin-nav a {
    flex: 1;
    justify-content: center;
  }
}
</style>
