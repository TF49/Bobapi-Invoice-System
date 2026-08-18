// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminUsers from './AdminUsers.vue'
import { userApi, type ManagedUser, type UserPage } from '@/api/user'

vi.mock('@/api/user', () => ({
  userApi: {
    getUsers: vi.fn(),
    createUser: vi.fn(),
    updateRole: vi.fn(),
    updateStatus: vi.fn(),
    resetPassword: vi.fn()
  }
}))

const admin: ManagedUser = {
  id: 1,
  username: 'admin',
  role: 'ADMIN',
  enabled: true,
  createdAt: '2026-08-18T10:00:00',
  updatedAt: '2026-08-18T10:00:00',
  self: true
}

const alice: ManagedUser = {
  id: 2,
  username: 'alice',
  role: 'USER',
  enabled: true,
  createdAt: '2026-08-18T11:00:00',
  updatedAt: '2026-08-18T11:00:00',
  self: false
}

const pageResult: UserPage = {
  users: [admin, alice],
  total: 25,
  page: 1,
  pageSize: 10,
  totalPages: 3,
  stats: { totalUsers: 25, enabledUsers: 23, disabledUsers: 2, adminUsers: 2 }
}

const mockedApi = vi.mocked(userApi)
let wrapper: VueWrapper | null = null
let router: Router
const storage = new Map<string, string>()
const localStorageMock: Storage = {
  get length() {
    return storage.size
  },
  clear() {
    storage.clear()
  },
  getItem(key: string) {
    return storage.get(key) ?? null
  },
  key(index: number) {
    return [...storage.keys()][index] ?? null
  },
  removeItem(key: string) {
    storage.delete(key)
  },
  setItem(key: string, value: string) {
    storage.set(key, String(value))
  }
}

vi.stubGlobal('localStorage', localStorageMock)

async function mountPage() {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/users', component: AdminUsers },
      { path: '/login', component: { template: '<div>login</div>' } }
    ]
  })
  await router.push('/admin/users')
  await router.isReady()

  wrapper = mount(AdminUsers, {
    attachTo: document.body,
    global: {
      plugins: [createPinia(), router, ElementPlus],
      stubs: {
        AppHeader: { template: '<header>用户管理</header>' },
        AnimatedContent: { template: '<section><slot /></section>' },
        SpotlightCard: { template: '<div><slot /></div>' },
        CountUp: { props: ['value'], template: '<span>{{ value }}</span>' }
      }
    }
  })
  await flushPromises()
  return wrapper
}

describe('AdminUsers', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('token', 'admin-token')
    localStorage.setItem('username', 'admin')
    localStorage.setItem('role', 'ADMIN')
    mockedApi.getUsers.mockResolvedValue(pageResult)
    mockedApi.updateStatus.mockResolvedValue({ ...alice, enabled: false })
    mockedApi.resetPassword.mockResolvedValue(admin)
    vi.spyOn(ElMessage, 'success').mockImplementation(() => undefined as never)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('loads statistics and disables role/status controls for the current administrator', async () => {
    const page = await mountPage()

    expect(page.text()).toContain('当前账号')
    expect(page.text()).toContain('25')
    expect(page.findAll('.role-select')[0].find('.el-select__wrapper').classes()).toContain('is-disabled')
    expect(page.findAll('.el-switch')[0].classes()).toContain('is-disabled')
  })

  it('sends search and pagination changes back to the server', async () => {
    const page = await mountPage()
    const searchInput = page.find('input[placeholder="搜索用户名"]')
    await searchInput.setValue('alice')
    await searchInput.trigger('keyup.enter')
    await flushPromises()

    expect(mockedApi.getUsers).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 1,
      keyword: 'alice'
    }))

    const secondPage = page.findAll('.el-pager li').find(item => item.text() === '2')
    expect(secondPage).toBeDefined()
    await secondPage!.trigger('click')
    await flushPromises()
    expect(mockedApi.getUsers).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }))
  })

  it('confirms a status change before submitting it', async () => {
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue(undefined as never)
    const page = await mountPage()

    await page.findAll('.el-switch')[1].trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalled()
    expect(mockedApi.updateStatus).toHaveBeenCalledWith(2, false)
  })

  it('clears local credentials and redirects after resetting its own password', async () => {
    const page = await mountPage()
    await page.find('[aria-label="重置 admin 的密码"]').trigger('click')
    await flushPromises()

    const inputs = document.body.querySelectorAll<HTMLInputElement>('.el-dialog input[type="password"]')
    expect(inputs).toHaveLength(2)
    for (const input of inputs) {
      input.value = 'newpass9'
      input.dispatchEvent(new Event('input', { bubbles: true }))
    }
    await flushPromises()

    const submit = document.body.querySelector<HTMLButtonElement>('.el-dialog__footer .el-button--danger')
    expect(submit).not.toBeNull()
    submit!.click()
    await flushPromises()

    expect(mockedApi.resetPassword).toHaveBeenCalledWith(1, 'newpass9')
    expect(localStorage.getItem('token')).toBeNull()
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
