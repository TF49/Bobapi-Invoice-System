// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UserGuideDialog from './UserGuideDialog.vue'

const mockPush = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mockPush
  })
}))

let wrapper: VueWrapper | null = null

async function mountDialog(props = { modelValue: true }) {
  wrapper = mount(UserGuideDialog, {
    props,
    attachTo: document.body,
    global: {
      plugins: [ElementPlus]
    }
  })
  await flushPromises()
  return wrapper
}

describe('UserGuideDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
  })

  it('renders dialog when modelValue is true', async () => {
    const w = await mountDialog({ modelValue: true })
    expect(w.exists()).toBe(true)
    expect(document.body.innerHTML).toContain('系统使用说明')
    expect(document.body.innerHTML).toContain('快速了解额度申请与发票开具全流程')
  })

  it('navigates to /guide when clicking open full page button', async () => {
    const w = await mountDialog({ modelValue: true })
    const openBtn = document.body.querySelector('.open-page-btn') as HTMLElement
    expect(openBtn).not.toBeNull()

    openBtn.click()
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith('/guide')
    expect(w.emitted('update:modelValue')).toBeTruthy()
    expect(w.emitted('update:modelValue')![0]).toEqual([false])
  })

  it('closes dialog when clicking footer close button', async () => {
    const w = await mountDialog({ modelValue: true })
    const footerBtn = document.body.querySelector('.guide-dialog-footer .el-button--primary') as HTMLElement
    expect(footerBtn).not.toBeNull()

    footerBtn.click()
    await flushPromises()

    expect(w.emitted('update:modelValue')).toBeTruthy()
    expect(w.emitted('update:modelValue')![0]).toEqual([false])
  })
})
