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
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/AdminInvoice.vue'),
    meta: { requiresAuth: true, role: 'ADMIN' }
  },
  {
    path: '/admin/users',
    name: 'AdminUsers',
    component: () => import('@/views/AdminUsers.vue'),
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
  const token = localStorage.getItem('token')
  const userRole = localStorage.getItem('role')

  if (token && !['USER', 'ADMIN'].includes(userRole || '')) {
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('role')
    next('/login')
    return
  }
  
  if (to.meta.requiresAuth) {
    if (!token) {
      next('/login')
    } else if (to.meta.role && to.meta.role !== userRole) {
      next(userRole === 'ADMIN' ? '/admin' : '/user')
    } else {
      next()
    }
  } else {
    if (token && (to.path === '/login' || to.path === '/register')) {
      next(userRole === 'ADMIN' ? '/admin' : '/user')
    } else {
      next()
    }
  }
})

export default router
