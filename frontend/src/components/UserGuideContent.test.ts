// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, describe, expect, it } from 'vitest'
import UserGuideContent from './UserGuideContent.vue'

let wrapper: VueWrapper | null = null

async function mountComponent(props = {}) {
  wrapper = mount(UserGuideContent, {
    props,
    global: {
      plugins: [ElementPlus]
    }
  })
  await flushPromises()
  return wrapper
}

describe('UserGuideContent', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  it('renders all 3 sections in a single vertical scroll layout', async () => {
    const w = await mountComponent()
    expect(w.text()).toContain('额度申请指引')
    expect(w.text()).toContain('发票申请指引')
    expect(w.text()).toContain('常见问题速查')

    // 充值 4 步极简流程
    expect(w.text()).toContain('进入充值')
    expect(w.text()).toContain('输入手续费')
    expect(w.text()).toContain('转账与回执')
    expect(w.text()).toContain('上传提交')

    // 发票申请 3 种方式
    expect(w.text()).toContain('常规手动填单')
    expect(w.text()).toContain('AI 智能识别自动填单')
    expect(w.text()).toContain('Excel 批量导入')
  })

  it('renders quick jump pill buttons and scroll hint', async () => {
    const w = await mountComponent()
    const pills = w.findAll('.pill-btn')
    expect(pills.length).toBe(3)
    expect(w.find('.scroll-hint').text()).toContain('上下滑动浏览全部')

    // Click pill to activate
    await pills[1].trigger('click')
    await flushPromises()
    expect(pills[1].classes()).toContain('active')
  })

  it('emits navigate event when clicking action buttons', async () => {
    const w = await mountComponent()
    const actionBtns = w.findAll('.section-action-btn')
    expect(actionBtns.length).toBeGreaterThanOrEqual(2)

    await actionBtns[0].trigger('click')
    expect(w.emitted('navigate')).toBeTruthy()
    expect(w.emitted('navigate')![0]).toEqual(['quota'])

    await actionBtns[1].trigger('click')
    expect(w.emitted('navigate')![1]).toEqual(['invoice'])
  })
})
