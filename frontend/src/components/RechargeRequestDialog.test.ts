// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RechargeRequestDialog from './RechargeRequestDialog.vue'
import { rechargeRequestApi } from '@/api/rechargeRequest'

vi.mock('@/api/rechargeRequest', () => ({
  rechargeRequestApi: {
    createRequest: vi.fn(),
    uploadScreenshot: vi.fn()
  }
}))

const mockedApi = vi.mocked(rechargeRequestApi)
let wrapper: VueWrapper | null = null

async function mountDialog(modelValue = true) {
  wrapper = mount(RechargeRequestDialog, {
    props: {
      modelValue
    },
    attachTo: document.body,
    global: {
      plugins: [ElementPlus]
    }
  })
  await flushPromises()
  return wrapper
}

describe('RechargeRequestDialog', () => {
  beforeEach(() => {
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn((_blob: any) => 'blob:http://localhost/mock-preview-id'),
      revokeObjectURL: vi.fn()
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('renders upload area when no screenshot is uploaded', async () => {
    const dialog = await mountDialog(true)
    expect(dialog.find('.screenshot-uploader').exists()).toBe(true)
    expect(dialog.find('.proof-preview-card').exists()).toBe(false)
  })

  it('shows image preview card with el-image when manual screenshot URL is provided', async () => {
    const dialog = await mountDialog(true)
    
    // Toggle manual URL input
    const toggleBtn = dialog.find('.url-fallback-toggle button')
    expect(toggleBtn.exists()).toBe(true)
    await toggleBtn.trigger('click')
    await flushPromises()

    const input = dialog.find('.url-fallback-toggle + .el-input input')
    expect(input.exists()).toBe(true)
    await input.setValue('https://example.com/voucher.png')
    await flushPromises()

    // Preview card should be visible
    expect(dialog.find('.proof-preview-card').exists()).toBe(true)
    expect(dialog.find('.uploaded-proof-img').exists()).toBe(true)
  })

  it('uploads file and shows local blob preview and success message', async () => {
    mockedApi.uploadScreenshot.mockResolvedValue('/recharge-requests/screenshot/abc.png')
    const dialog = await mountDialog(true)
    const file = new File(['fake content'], 'voucher.png', { type: 'image/png' })
    // Trigger file change on the upload file input
    const fileInput = dialog.find('.screenshot-uploader input[type="file"]')
    if (fileInput.exists()) {
      Object.defineProperty(fileInput.element, 'files', {
        value: [file],
        writable: true
      })
      await fileInput.trigger('change')
    } else {
      const uploader = dialog.findComponent({ name: 'ElUpload' })
      const onChange = uploader.props('onChange') as any
      if (onChange) await onChange({ raw: file, status: 'ready' })
    }
    await flushPromises()

    expect(URL.createObjectURL).toHaveBeenCalledWith(file)
    expect(mockedApi.uploadScreenshot).toHaveBeenCalledWith(file)
    expect(dialog.find('.proof-preview-card').exists()).toBe(true)
    expect(dialog.find('.proof-status-label').text()).toContain('凭证已就绪')
  })
})
