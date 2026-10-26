// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UserGuide from './UserGuide.vue'

const mockPush = vi.fn()
let currentQuery: Record<string, string> = {}

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({
      push: mockPush
    }),
    useRoute: () => ({
      query: currentQuery
    })
  }
})

let wrapper: VueWrapper | null = null

async function mountPage() {
  wrapper = mount(UserGuide, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      stubs: {
        AppHeader: { template: '<header>使用说明</header>' },
        AnimatedContent: { template: '<section><slot /></section>' },
        SpotlightCard: { template: '<div><slot /></div>' }
      }
    }
  })
  await flushPromises()
  return wrapper
}

describe('UserGuide View', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    currentQuery = {}
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
  })

  it('renders page layout with hero section and guide content', async () => {
    const w = await mountPage()
    expect(w.text()).toContain('系统操作指引与帮助中心')
    expect(w.text()).toContain('前往额度充值')
    expect(w.text()).toContain('前往文件盖章')
    expect(w.text()).toContain('前往发票申请')
    expect(w.text()).toContain('额度申请指引')
  })

  it('navigates to pages when clicking shortcuts', async () => {
    const w = await mountPage()
    const buttons = w.findAll('.hero-shortcuts .el-button')
    expect(buttons.length).toBe(3)

    await buttons[0].trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/user/quota')

    await buttons[1].trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/user/documents')

    await buttons[2].trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/user')
  })

  it('honors query parameter tab=invoice', async () => {
    currentQuery = { tab: 'invoice' }
    const w = await mountPage()
    expect(w.text()).toContain('发票申请指引')
    expect(w.text()).toContain('AI 智能识别自动填单')
  })
})
