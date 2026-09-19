import { defineStore } from 'pinia'
import { ref } from 'vue'

function getStoredItem(key: string): string {
  try {
    if (typeof window !== 'undefined' && window.localStorage) {
      const val = window.localStorage.getItem(key)
      if (val) return val
    }
  } catch {
    // ignore
  }
  try {
    if (typeof window !== 'undefined' && window.sessionStorage) {
      const val = window.sessionStorage.getItem(key)
      if (val) return val
    }
  } catch {
    // ignore
  }
  return ''
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getStoredItem('token'))
  const username = ref<string>(getStoredItem('username'))
  const role = ref<string>(getStoredItem('role'))

  function setToken(newToken: string, rememberMe: boolean = true) {
    token.value = newToken
    try {
      if (rememberMe) {
        // 勾选「7 天内保持登录」：持久化到 localStorage，与后端 7 天 token 有效期一致
        if (typeof window !== 'undefined' && window.localStorage) {
          window.localStorage.setItem('token', newToken)
        }
        if (typeof window !== 'undefined' && window.sessionStorage) {
          window.sessionStorage.removeItem('token')
        }
      } else {
        // 未勾选：仅存入 sessionStorage，关闭浏览器标签页后自动清除
        if (typeof window !== 'undefined' && window.sessionStorage) {
          window.sessionStorage.setItem('token', newToken)
        }
        if (typeof window !== 'undefined' && window.localStorage) {
          window.localStorage.removeItem('token')
        }
      }
    } catch {
      // ignore
    }
  }

  function setUser(user: { username: string; role: string }) {
    username.value = user.username
    role.value = user.role
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        window.localStorage.setItem('username', user.username)
        window.localStorage.setItem('role', user.role)
      }
    } catch {
      // ignore
    }
  }

  function logout() {
    token.value = ''
    username.value = ''
    role.value = ''
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        window.localStorage.removeItem('token')
        window.localStorage.removeItem('username')
        window.localStorage.removeItem('role')
      }
      if (typeof window !== 'undefined' && window.sessionStorage) {
        window.sessionStorage.removeItem('token')
      }
    } catch {
      // ignore
    }
  }

  return { token, username, role, setToken, setUser, logout }
})