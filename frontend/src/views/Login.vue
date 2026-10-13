<template>
  <AuthShell eyebrow="帐号访问" title="欢迎回来" subtitle="登录后继续处理您的发票事项。">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleLogin">
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="form.username"
          :prefix-icon="User"
          placeholder="请输入用户名"
          autocomplete="username"
          @blur="checkCaptchaRequired"
        />
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
      <SlideCaptcha
        v-if="needCaptcha"
        ref="captchaRef"
        :disabled="lockCountdown > 0"
        :reset-key="captchaResetKey"
        @verified="onCaptchaVerified"
        @invalidate="captchaToken = ''"
      />
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
        :disabled="lockCountdown > 0 || (needCaptcha && !captchaToken)"
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
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Right, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/auth'
import AuthShell from '@/components/AuthShell.vue'
import SlideCaptcha from '@/components/SlideCaptcha.vue'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const captchaRef = ref<InstanceType<typeof SlideCaptcha>>()
const loading = ref(false)
const lockCountdown = ref(0)
const needCaptcha = ref(false)
const captchaToken = ref('')
const captchaResetKey = ref(0)
const localFailCount = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const LOCK_STORAGE_KEY = 'invoice_login_lock_until'
const FAIL_STORAGE_KEY = 'invoice_login_fail_count'

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
    const fails = Number(sessionStorage.getItem(FAIL_STORAGE_KEY) || '0')
    if (fails >= 2) {
      localFailCount.value = fails
      needCaptcha.value = true
    }
  } catch {
    // ignore
  }
}

const bumpFailCount = () => {
  localFailCount.value += 1
  try {
    sessionStorage.setItem(FAIL_STORAGE_KEY, String(localFailCount.value))
  } catch {
    // ignore
  }
  if (localFailCount.value >= 2) {
    needCaptcha.value = true
  }
}

const clearFailCount = () => {
  localFailCount.value = 0
  needCaptcha.value = false
  captchaToken.value = ''
  try {
    sessionStorage.removeItem(FAIL_STORAGE_KEY)
  } catch {
    // ignore
  }
}

const resetCaptchaUi = () => {
  captchaToken.value = ''
  captchaResetKey.value += 1
}

const checkCaptchaRequired = async () => {
  if (!form.username || form.username.length < 2) return
  try {
    const result = await authApi.captchaRequired(form.username)
    if (result.required) {
      needCaptcha.value = true
    } else if (localFailCount.value < 2) {
      // 服务端未要求且本地失败未达阈值时，收起滑块
      needCaptcha.value = false
      captchaToken.value = ''
    }
  } catch {
    // 查询失败不阻断登录流程
  }
}

const onCaptchaVerified = (token: string) => {
  captchaToken.value = token
}

const form = reactive({
  username: '',
  password: '',
  rememberMe: true
})

watch(
  () => form.username,
  () => {
    // 换用户名后旧 captcha 状态不可复用
    captchaToken.value = ''
    captchaResetKey.value += 1
    if (localFailCount.value < 2) {
      needCaptcha.value = false
    }
  }
)

onMounted(() => {
  restoreLockState()
})

onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
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

  // 提交前再与服务端对齐一次，避免本地计数被清空导致漏验
  await checkCaptchaRequired()

  if (needCaptcha.value && !captchaToken.value) {
    ElMessage.warning('请先完成安全验证')
    return
  }
  loading.value = true
  try {
    const response = await authApi.login({
      username: form.username,
      password: form.password,
      rememberMe: form.rememberMe,
      ...(needCaptcha.value && captchaToken.value ? { captchaToken: captchaToken.value } : {})
    })
    try {
      sessionStorage.removeItem(LOCK_STORAGE_KEY)
    } catch {
      // ignore
    }
    clearFailCount()
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
    if (err && (err.code === 40101 || err.code === 40102 || err.code === 40020 || err.code === 40021)) {
      bumpFailCount()
      resetCaptchaUi()
      if (err.code === 40102 || err.code === 40020 || err.code === 40021) {
        needCaptcha.value = true
      }
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
