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
      <router-link to="/admin/users" exact-active-class="is-active">
        <UserFilled />
        <span>用户管理</span>
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
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataAnalysis, Files, SwitchButton, Tickets, UserFilled, Wallet } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

defineProps<{ title: string }>()

const router = useRouter()
const userStore = useUserStore()
const roleLabel = computed(() => userStore.role === 'ADMIN' ? '管理员' : '普通用户')
const userInitial = computed(() => userStore.username.trim().slice(0, 1).toUpperCase() || 'U')

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

.header-actions {
  flex: 0 0 auto;
  gap: 14px;
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
