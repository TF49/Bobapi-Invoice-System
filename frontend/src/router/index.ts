import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue')
  },
  {
    path: '/user',
    name: 'User',
    component: () => import('@/views/UserInvoice.vue'),
    meta: { requiresAuth: true, role: 'USER' }
  },
  {
    path: '/user/documents',
    name: 'UserDocument',
    component: () => import('@/views/UserDocument.vue'),
    meta: { requiresAuth: true, role: 'USER' }
  },
  {
    path: '/user/quota',
    name: 'UserQuota',
    component: () => import('@/views/UserQuota.vue'),
    meta: { requiresAuth: true, role: 'USER' }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/AdminInvoice.vue'),
    meta: { requiresAuth: true, role: ['ADMIN', 'INVOICE_CLERK'] }
  },
  {
    path: '/admin/documents',
    name: 'AdminDocument',
    component: () => import('@/views/AdminDocument.vue'),
    meta: { requiresAuth: true, role: ['ADMIN', 'INVOICE_CLERK'] }
  },
  {
    path: '/admin/users',
    name: 'AdminUsers',
    component: () => import('@/views/AdminUsers.vue'),
    meta: { requiresAuth: true, role: 'ADMIN' }
  },
  {
    path: '/admin/recharge-requests',
    name: 'AdminRechargeRequests',
    component: () => import('@/views/AdminRechargeRequests.vue'),
    meta: { requiresAuth: true, role: 'ADMIN' }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { requiresAuth: true, role: 'ADMIN' }
  },
  {
    path: '/guide',
    name: 'UserGuide',
    component: () => import('@/views/UserGuide.vue'),
    meta: { requiresAuth: true, role: ['USER', 'ADMIN', 'INVOICE_CLERK'] }
  },
  {
    path: '/',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  const userRole = localStorage.getItem('role') || sessionStorage.getItem('role')

  // 如果没有 token，或者 token 存在但没有合法角色，清除残留凭证，避免状态异常
  const isValidRole = userRole && ['USER', 'ADMIN', 'INVOICE_CLERK'].includes(userRole)
  if (token && !isValidRole) {
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('role')
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('username')
    sessionStorage.removeItem('role')
    if (to.path !== '/login' && to.path !== '/register') {
      next('/login')
      return
    }
  }

  const effectiveToken = localStorage.getItem('token') || sessionStorage.getItem('token')
  const effectiveRole = localStorage.getItem('role') || sessionStorage.getItem('role')

  if (to.meta.requiresAuth) {
    if (!effectiveToken || !effectiveRole) {
      next('/login')
      return
    }
    const allowedRoles = Array.isArray(to.meta.role) ? to.meta.role : [to.meta.role]
    if (!allowedRoles.includes(effectiveRole)) {
      // 角色权限不符时重定向，并严格避免自身重定向死循环
      if (effectiveRole === 'ADMIN' || effectiveRole === 'INVOICE_CLERK') {
        if (to.path !== '/admin') {
          next('/admin')
        } else {
          next()
        }
      } else if (effectiveRole === 'USER') {
        if (to.path !== '/user') {
          next('/user')
        } else {
          next()
        }
      } else {
        next('/login')
      }
      return
    }
    next()
  } else {
    // 访问登录/注册页时，若已拥有有效登录态则按角色分流
    if (effectiveToken && effectiveRole && (to.path === '/login' || to.path === '/register')) {
      if (effectiveRole === 'ADMIN' || effectiveRole === 'INVOICE_CLERK') {
        next('/admin')
      } else {
        next('/user')
      }
      return
    }
    next()
  }
})

export default router
