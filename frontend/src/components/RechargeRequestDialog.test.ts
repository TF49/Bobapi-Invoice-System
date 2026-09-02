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
    vi.stubGlobal('crypto', {
      randomUUID: () => '12345678-1234-1234-1234-123456789012'
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('renders upload area when no screenshot is uploaded', async () => {
    const dialog = await mountDialog(true)
    expect(dialog.find('.screenshot-uploader').exists()).toBe(true)
    expect(dialog.find('.proof-preview-card').exists()).toBe(false)
  })

  it('calculates the quota from the fee at a fixed three percent rate', async () => {
    const dialog = await mountDialog(true)
    const feeInput = dialog.findComponent({ name: 'ElInputNumber' })

    feeInput.vm.$emit('update:modelValue', 300)
    await flushPromises()

    expect(dialog.find('.conversion-result strong').text()).toContain('10,000.00')

    feeInput.vm.$emit('update:modelValue', 100)
    await flushPromises()

    expect(dialog.find('.conversion-result strong').text()).toContain('3,333.33')
  })

  it('submits only the fee amount as the user-controlled monetary value', async () => {
    mockedApi.uploadScreenshot.mockResolvedValue('/recharge-requests/screenshot/abc.png')
    mockedApi.createRequest.mockResolvedValue({} as any)
    const dialog = await mountDialog(true)
    const feeInput = dialog.findComponent({ name: 'ElInputNumber' })
    feeInput.vm.$emit('update:modelValue', 300)

    const uploader = dialog.findComponent({ name: 'ElUpload' })
    const onChange = uploader.props('onChange') as any
    await onChange({
      raw: new File(['fake content'], 'voucher.png', { type: 'image/png' }),
      status: 'ready'
    })
    await flushPromises()

    const submitButton = dialog.findAll('button').find((button) => button.text().includes('确认提交'))
    expect(submitButton).toBeTruthy()
    await submitButton!.trigger('click')
    await flushPromises()

    expect(mockedApi.createRequest).toHaveBeenCalledWith(
      {
        feeAmount: 300,
        screenshotUrl: '/recharge-requests/screenshot/abc.png',
        remark: undefined
      },
      'recharge-12345678-1234-1234-1234-123456789012'
    )
    expect(mockedApi.createRequest.mock.calls[0][0]).not.toHaveProperty('amount')
  })

  it('reuses the idempotency key when retrying the same payload', async () => {
    mockedApi.uploadScreenshot.mockResolvedValue('/recharge-requests/screenshot/abc.png')
    mockedApi.createRequest
      .mockRejectedValueOnce(new Error('network error'))
      .mockResolvedValueOnce({} as any)
    const dialog = await mountDialog(true)
    dialog.findComponent({ name: 'ElInputNumber' }).vm.$emit('update:modelValue', 300)

    const uploader = dialog.findComponent({ name: 'ElUpload' })
    const onChange = uploader.props('onChange') as any
    await onChange({
      raw: new File(['fake content'], 'voucher.png', { type: 'image/png' }),
      status: 'ready'
    })
    await flushPromises()

    const submitButton = dialog.findAll('button').find((button) => button.text().includes('确认提交'))!
    await submitButton.trigger('click')
    await flushPromises()
    await dialog.find('textarea').setValue('   ')
    await submitButton.trigger('click')
    await flushPromises()

    expect(mockedApi.createRequest).toHaveBeenCalledTimes(2)
    expect(mockedApi.createRequest.mock.calls[0][1]).toBe(mockedApi.createRequest.mock.calls[1][1])
    expect(mockedApi.createRequest.mock.calls[1][0].remark).toBeUndefined()
  })

  it('ignores an upload response after the dialog is reset', async () => {
    let resolveUpload!: (url: string) => void
    mockedApi.uploadScreenshot.mockReturnValue(new Promise((resolve) => {
      resolveUpload = resolve
    }))
    const dialog = await mountDialog(true)
    const uploader = dialog.findComponent({ name: 'ElUpload' })
    const onChange = uploader.props('onChange') as any
    const uploadTask = onChange({
      raw: new File(['fake content'], 'voucher.png', { type: 'image/png' }),
      status: 'ready'
    })
    await flushPromises()

    const elDialog = dialog.findComponent({ name: 'ElDialog' })
    elDialog.vm.$emit('close')
    await flushPromises()
    resolveUpload('/recharge-requests/screenshot/late.png')
    await uploadTask
    await flushPromises()

    expect(dialog.find('.proof-preview-card').exists()).toBe(false)
    expect(dialog.find('.screenshot-uploader').exists()).toBe(true)
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
