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
      <el-button class="submit-button" type="primary" :icon="Right" native-type="submit" :loading="loading">
        登录系统
      </el-button>
      <p class="auth-switch">
        还没有账号？
        <el-link type="primary" underline="never" @click="goToRegister">创建账号</el-link>
      </p>
    </el-form>
  </AuthShell>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
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

const form = reactive({
  username: '',
  password: '',
  rememberMe: false
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度必须在 3-20 位之间', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度必须在 6-20 位之间', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (loading.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const response = await authApi.login(form)
    userStore.setToken(response.token)
    userStore.setUser({ username: response.username, role: response.role })
    ElMessage.success('登录成功')
    router.push(response.role === 'ADMIN' ? '/admin' : '/user')
  } catch {
    // 错误提示由请求拦截器统一处理
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
