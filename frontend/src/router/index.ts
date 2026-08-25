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
  const userRole = localStorage.getItem('role')

  // 只有当 role 确实存在且为非法值时才清除 token，避免 role 尚未写入时误删
  if (token && userRole && !['USER', 'ADMIN', 'INVOICE_CLERK'].includes(userRole)) {
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('role')
    next('/login')
    return
  }
  
  if (to.meta.requiresAuth) {
    if (!token) {
      next('/login')
    } else if (to.meta.role) {
      const allowedRoles = Array.isArray(to.meta.role) ? to.meta.role : [to.meta.role]
      if (!allowedRoles.includes(userRole)) {
        // 根据角色重定向到对应页面
        if (userRole === 'ADMIN') {
          next('/admin')
        } else if (userRole === 'INVOICE_CLERK') {
          next('/admin')
        } else {
          next('/user')
        }
      } else {
        next()
      }
    } else {
      next()
    }
  } else {
    if (token && (to.path === '/login' || to.path === '/register')) {
      if (userRole === 'ADMIN' || userRole === 'INVOICE_CLERK') {
        next('/admin')
      } else {
        next('/user')
      }
    } else {
      next()
    }
  }
})

export default router
