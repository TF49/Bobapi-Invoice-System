import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || sessionStorage.getItem('token') || '')
  const username = ref<string>(localStorage.getItem('username') || '')
  const role = ref<string>(localStorage.getItem('role') || '')

  function setToken(newToken: string, rememberMe: boolean = false) {
    token.value = newToken
    if (rememberMe) {
      // 勾选「7 天内保持登录」：持久化到 localStorage，与后端 7 天 token 有效期一致
      localStorage.setItem('token', newToken)
      sessionStorage.removeItem('token')
    } else {
      // 未勾选：仅存入 sessionStorage，关闭浏览器标签页后自动清除
      sessionStorage.setItem('token', newToken)
      localStorage.removeItem('token')
    }
  }

  function setUser(user: { username: string; role: string }) {
    username.value = user.username
    role.value = user.role
    localStorage.setItem('username', user.username)
    localStorage.setItem('role', user.role)
  }

  function logout() {
    token.value = ''
    username.value = ''
    role.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('role')
    sessionStorage.removeItem('token')
  }

  return { token, username, role, setToken, setUser, logout }
})