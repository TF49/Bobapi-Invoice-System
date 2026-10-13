// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, onMounted } from 'vue'
import Register from './Register.vue'
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
    register: vi.fn(),
    generateCaptcha: vi.fn(),
    verifyCaptcha: vi.fn(),
    captchaRequired: vi.fn()
  }
}))

vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn()
}))

const mockedAuthApi = vi.mocked(authApi)
const mockedUseUserStore = vi.mocked(useUserStore)
let wrapper: VueWrapper | null = null

const SlideCaptchaStub = defineComponent({
  name: 'SlideCaptcha',
  props: {
    resetKey: { type: [Number, String], default: 0 }
  },
  emits: ['verified', 'invalidate'],
  setup(_props, { emit }) {
    onMounted(() => {
      emit('verified', 'tok-from-stub')
    })
  },
  template: '<div class="slide-captcha-stub" data-testid="slide-captcha">reset:{{ resetKey }}</div>'
})

async function mountPage() {
  wrapper = mount(Register, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      stubs: {
        AuthShell: {
          props: ['eyebrow', 'title', 'subtitle'],
          template: '<div class="auth-shell"><slot /></div>'
        },
        SlideCaptcha: SlideCaptchaStub
      }
    }
  })
  await flushPromises()
  return wrapper
}

describe('Register.vue', () => {
  beforeEach(() => {
    mockedUseUserStore.mockReturnValue({
      setUser: vi.fn(),
      setToken: vi.fn()
    } as never)
    vi.spyOn(ElMessage, 'success').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'error').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => undefined as never)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('enables submit after captcha verified', async () => {
    const page = await mountPage()
    expect(page.find('.submit-button').attributes('disabled')).toBeUndefined()
  })

  it('resets captcha after username conflict (40901)', async () => {
    mockedAuthApi.register.mockRejectedValue(new ApiRequestError('用户名已存在', 40901))

    const page = await mountPage()
    const beforeReset = page.find('[data-testid="slide-captcha"]').text()

    const inputs = page.findAll('input')
    await inputs[0].setValue('taken_user')
    await inputs[1].setValue('pass1234')
    await inputs[2].setValue('pass1234')

    await page.find('form').trigger('submit')
    await flushPromises()

    expect(mockedAuthApi.register).toHaveBeenCalledWith({
      username: 'taken_user',
      password: 'pass1234',
      captchaToken: 'tok-from-stub'
    })
    const afterReset = page.find('[data-testid="slide-captcha"]').text()
    expect(afterReset).not.toBe(beforeReset)
    expect(page.find('.submit-button').attributes('disabled')).toBeDefined()
  })
})
