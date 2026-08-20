<template>
  <AuthShell eyebrow="新建帐号" title="创建您的账号" subtitle="注册完成后即可进入个人发票工作台。">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleRegister">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" :prefix-icon="User" placeholder="3-20 位字母、数字或下划线" autocomplete="username" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          :prefix-icon="Lock"
          type="password"
          show-password
          placeholder="6-20 位，须包含字母和数字"
          autocomplete="new-password"
        />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          :prefix-icon="Key"
          type="password"
          show-password
          placeholder="再次输入密码"
          autocomplete="new-password"
        />
      </el-form-item>
      <el-button class="submit-button" type="primary" :icon="Right" native-type="submit" :loading="loading">
        创建账号
      </el-button>
      <p class="auth-switch">
        已有账号？
        <el-link type="primary" underline="never" @click="goToLogin">返回登录</el-link>
      </p>
    </el-form>
  </AuthShell>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key, Lock, Right, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { authApi, type RegisterRequest } from '@/api/auth'
import AuthShell from '@/components/AuthShell.vue'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive<RegisterRequest & { confirmPassword: string }>({
  username: '',
  password: '',
  confirmPassword: ''
})

const validateConfirmPassword = (_rule: any, value: any, callback: any) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度必须在 3-20 位之间', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度必须在 6-20 位之间', trigger: 'blur' },
    { pattern: /^(?=.*[0-9])(?=.*[A-Za-z]).{6,20}$/, message: '密码必须包含字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const handleRegister = async () => {
  if (loading.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const response = await authApi.register({
      username: form.username,
      password: form.password
    })
    // 先写 role，再写 token，确保路由守卫触发时 role 已存在
    userStore.setUser({ username: response.username, role: response.role })
    userStore.setToken(response.token)
    ElMessage.success('注册成功')
    router.push('/user')
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

const goToLogin = () => {
  router.push('/login')
}
</script>

<style scoped>
.submit-button {
  width: 100%;
  margin-top: 4px;
}

.auth-switch {
  margin: 22px 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
  text-align: center;
}
</style>
