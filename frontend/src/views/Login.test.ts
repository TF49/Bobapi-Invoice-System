// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Login from './Login.vue'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { ApiRequestError } from '@/utils/request'

const mockPush = vi.fn()
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({
      push: mockPush
    })
  }
})

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn()
  }
}))

vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn()
}))

const mockedAuthApi = vi.mocked(authApi)
const mockedUseUserStore = vi.mocked(useUserStore)
let wrapper: VueWrapper | null = null

const setUserMock = vi.fn()
const setTokenMock = vi.fn()

async function mountPage() {
  wrapper = mount(Login, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      stubs: {
        AuthShell: {
          props: ['eyebrow', 'title', 'subtitle'],
          template: '<div class="auth-shell"><slot /></div>'
        }
      }
    }
  })
  await flushPromises()
  return wrapper
}

describe('Login.vue', () => {
  beforeEach(() => {
    mockedUseUserStore.mockReturnValue({
      setUser: setUserMock,
      setToken: setTokenMock
    } as never)

    vi.spyOn(ElMessage, 'success').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'error').mockImplementation(() => undefined as never)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('successful admin login redirects to /admin', async () => {
    mockedAuthApi.login.mockResolvedValue({
      token: 'jwt-admin-token',
      username: 'admin',
      role: 'ADMIN'
    })

    const page = await mountPage()
    const inputs = page.findAll('input')
    await inputs[0].setValue('admin')
    await inputs[1].setValue('123456')

    const form = page.find('form')
    await form.trigger('submit')
    await flushPromises()

    expect(mockedAuthApi.login).toHaveBeenCalledWith({
      username: 'admin',
      password: '123456',
      rememberMe: false
    })
    expect(setUserMock).toHaveBeenCalledWith({ username: 'admin', role: 'ADMIN' })
    expect(setTokenMock).toHaveBeenCalledWith('jwt-admin-token', false)
    expect(mockPush).toHaveBeenCalledWith('/admin')
  })

  it('triggers lock countdown and disables submit button when locked (42901)', async () => {
    vi.useFakeTimers()
    mockedAuthApi.login.mockRejectedValue(
      new ApiRequestError('登录失败次数过多，请 1800 秒后再试', 42901, 'trace-1', 1800)
    )

    const page = await mountPage()
    const inputs = page.findAll('input')
    await inputs[0].setValue('admin')
    await inputs[1].setValue('wrong_pwd')

    const form = page.find('form')
    await form.trigger('submit')
    await flushPromises()

    // 验证出现锁定提示，并且按钮被禁用
    expect(page.find('.lock-alert').exists()).toBe(true)
    expect(page.text()).toContain('请等待 1800 秒后重试')

    const submitBtn = page.find('.submit-button')
    expect(submitBtn.attributes('disabled')).toBeDefined()
    expect(submitBtn.text()).toContain('已锁定 (1800s)')

    // 推进 1 秒
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(submitBtn.text()).toContain('已锁定 (1799s)')

    vi.useRealTimers()
  })

  it('restores lock state and countdown from sessionStorage on mount', async () => {
    const lockUntil = Date.now() + 500 * 1000
    sessionStorage.setItem('invoice_login_lock_until', String(lockUntil))

    const page = await mountPage()
    expect(page.find('.lock-alert').exists()).toBe(true)
    const submitBtn = page.find('.submit-button')
    expect(submitBtn.attributes('disabled')).toBeDefined()
    expect(submitBtn.text()).toContain('已锁定 (500s)')
  })
})
