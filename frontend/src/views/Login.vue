<template>
  <AuthShell eyebrow="帐号访问" title="欢迎回来" subtitle="登录后继续处理您的发票事项。">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleLogin">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" :prefix-icon="User" placeholder="请输入用户名" autocomplete="username" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          :prefix-icon="Lock"
          type="password"
          show-password
          placeholder="请输入密码"
          autocomplete="current-password"
        />
      </el-form-item>
      <div class="form-options">
        <el-checkbox v-model="form.rememberMe">7 天内保持登录</el-checkbox>
      </div>
      <el-alert
        v-if="lockCountdown > 0"
        type="error"
        :closable="false"
        show-icon
        class="lock-alert"
        :title="`登录失败次数过多或请求过于频繁，请等待 ${lockCountdown} 秒后重试`"
      />
      <el-button
        class="submit-button"
        type="primary"
        :icon="Right"
        native-type="submit"
        :loading="loading"
        :disabled="lockCountdown > 0"
      >
        {{ lockCountdown > 0 ? `已锁定 (${lockCountdown}s)` : '登录系统' }}
      </el-button>
      <p class="auth-switch">
        还没有账号？
        <el-link type="primary" underline="never" @click="goToRegister">创建账号</el-link>
      </p>
    </el-form>
  </AuthShell>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Right, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/auth'
import AuthShell from '@/components/AuthShell.vue'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const lockCountdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const LOCK_STORAGE_KEY = 'invoice_login_lock_until'

const startCountdown = (seconds: number) => {
  if (seconds <= 0) return
  const lockUntil = Date.now() + seconds * 1000
  try {
    sessionStorage.setItem(LOCK_STORAGE_KEY, String(lockUntil))
  } catch {
    // ignore storage error
  }

  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  lockCountdown.value = Math.ceil(seconds)
  countdownTimer = setInterval(() => {
    const remainingMs = lockUntil - Date.now()
    const remainingSec = Math.max(0, Math.ceil(remainingMs / 1000))
    lockCountdown.value = remainingSec
    if (remainingSec <= 0) {
      if (countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
      try {
        sessionStorage.removeItem(LOCK_STORAGE_KEY)
      } catch {
        // ignore
      }
    }
  }, 1000)
}

const restoreLockState = () => {
  try {
    const saved = sessionStorage.getItem(LOCK_STORAGE_KEY)
    if (saved) {
      const lockUntil = Number(saved)
      const remainingMs = lockUntil - Date.now()
      const remainingSec = Math.ceil(remainingMs / 1000)
      if (remainingSec > 0) {
        startCountdown(remainingSec)
      } else {
        sessionStorage.removeItem(LOCK_STORAGE_KEY)
      }
    }
  } catch {
    // ignore
  }
}

onMounted(() => {
  restoreLockState()
})

onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
})

const form = reactive({
  username: '',
  password: '',
  rememberMe: false
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度必须在 2-20 位之间', trigger: 'blur' },
    { pattern: /^[\u4e00-\u9fa5A-Za-z0-9_]+$/, message: '用户名只能包含汉字、字母、数字和下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度必须在 6-20 位之间', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (loading.value || lockCountdown.value > 0) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const response = await authApi.login(form)
    try {
      sessionStorage.removeItem(LOCK_STORAGE_KEY)
    } catch {
      // ignore
    }
    // 先写 role，再写 token，确保路由守卫触发时 role 已存在
    userStore.setUser({ username: response.username, role: response.role })
    userStore.setToken(response.token, form.rememberMe)
    ElMessage.success('登录成功')
    // 根据角色重定向到对应页面
    if (response.role === 'ADMIN' || response.role === 'INVOICE_CLERK') {
      router.push('/admin')
    } else {
      router.push('/user')
    }
  } catch (err: any) {
    // 错误提示已由请求拦截器统一处理，当检测到 429 频控/锁定时启动倒计时
    if (err && (err.code === 42901 || err.code === 42900)) {
      const waitSeconds = typeof err.data === 'number' && err.data > 0 ? err.data : 60
      startCountdown(waitSeconds)
    }
  } finally {
    loading.value = false
  }
}

const goToRegister = () => {
  router.push('/register')
}
</script>

<style scoped>
.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -4px 0 24px;
}

.lock-alert {
  margin-bottom: 16px;
}

.submit-button {
  width: 100%;
}

.auth-switch {
  margin: 22px 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
  text-align: center;
}
</style>
