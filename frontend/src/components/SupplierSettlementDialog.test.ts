// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SupplierSettlementDialog from './SupplierSettlementDialog.vue'
import { createSettlement } from '@/api/supplierSettlement'
import type { SellerCategoryStat } from '@/api/dashboard'

vi.mock('@/api/supplierSettlement', () => ({
  createSettlement: vi.fn(() => Promise.resolve({}))
}))

const mockedCreate = vi.mocked(createSettlement)

function channel(partial: Partial<SellerCategoryStat> & Pick<SellerCategoryStat, 'key' | 'label' | 'sellerCompany' | 'invoiceCategory'>): SellerCategoryStat {
  return {
    sellerCompanyName: partial.sellerCompany === 'TECH' ? '科技公司' : '普通公司',
    categoryName: partial.invoiceCategory === 'VAT_SPECIAL' ? '增值税专用发票' : '普通发票',
    completedAmount: 0,
    completedCount: 0,
    deductedQuota: 0,
    feeRate: 0.03,
    amountRatio: 0,
    ...partial
  }
}

const channels: SellerCategoryStat[] = [
  channel({ key: 'default_normal', label: '普通公司·普票', sellerCompany: 'DEFAULT', invoiceCategory: 'NORMAL', completedAmount: 1000, settledAmount: 600, unsettledAmount: 400 }),
  channel({ key: 'default_special', label: '普通公司·专票', sellerCompany: 'DEFAULT', invoiceCategory: 'VAT_SPECIAL', completedAmount: 800, settledAmount: 0, unsettledAmount: 800 }),
  channel({ key: 'tech_normal', label: '科技公司·普票', sellerCompany: 'TECH', invoiceCategory: 'NORMAL', completedAmount: 300, settledAmount: 0, unsettledAmount: 300 }),
  channel({ key: 'tech_special', label: '科技公司·专票', sellerCompany: 'TECH', invoiceCategory: 'VAT_SPECIAL', completedAmount: 200, settledAmount: 500, unsettledAmount: -300 })
]

let wrapper: VueWrapper | null = null

async function mountDialog(props: Record<string, unknown> = {}) {
  wrapper = mount(SupplierSettlementDialog, {
    props: { modelValue: true, channels, ...props },
    attachTo: document.body,
    global: { plugins: [ElementPlus] }
  })
  await flushPromises()
  return wrapper
}

function findSubmitButton() {
  return Array.from(document.body.querySelectorAll('button')).find((b) => b.textContent?.includes('确认结算')) as HTMLButtonElement
}

describe('SupplierSettlementDialog', () => {
  const spyConfirm = () => vi.spyOn(ElMessageBox, 'confirm')
  let confirmSpy: ReturnType<typeof spyConfirm>

  beforeEach(() => {
    vi.stubGlobal('crypto', { randomUUID: () => '12345678-1234-1234-1234-123456789012' })
    confirmSpy = spyConfirm()
    confirmSpy.mockResolvedValue(undefined as never)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('lists the four channels with their own unsettled amount', async () => {
    await mountDialog()
    const radios = document.body.querySelectorAll('.channel-radio')
    expect(radios).toHaveLength(4)
    expect(radios[0].textContent).toContain('普通公司·普票')
    expect(radios[0].textContent).toContain('未结 ¥400.00')
    expect(radios[3].textContent).toContain('未结 -¥300.00')
  })

  it('requires a channel before submitting', async () => {
    await mountDialog({ initialAmount: 100 })
    findSubmitButton().click()
    await flushPromises()

    expect(mockedCreate).not.toHaveBeenCalled()
  })

  it('submits the preselected channel with sellerCompany and invoiceCategory', async () => {
    await mountDialog({ initialChannelKey: 'tech_normal', initialAmount: 300 })
    expect(document.body.textContent).toContain('[科技公司·普票]')

    findSubmitButton().click()
    await flushPromises()

    expect(mockedCreate).toHaveBeenCalledTimes(1)
    const [payload, key] = mockedCreate.mock.calls[0]
    expect(payload).toEqual({ amount: 300, sellerCompany: 'TECH', invoiceCategory: 'NORMAL', remark: undefined })
    expect(key).toBeTruthy()
  })

  it('fills the amount with the channel unsettled amount when choosing a channel', async () => {
    await mountDialog()
    const radio = document.body.querySelector('[data-channel="default_special"] input') as HTMLInputElement
    radio.click()
    await flushPromises()

    findSubmitButton().click()
    await flushPromises()

    expect(mockedCreate).toHaveBeenCalledTimes(1)
    expect(mockedCreate.mock.calls[0][0]).toMatchObject({ amount: 800, sellerCompany: 'DEFAULT', invoiceCategory: 'VAT_SPECIAL' })
  })

  async function pickChannel(key: string) {
    ;(document.body.querySelector(`[data-channel="${key}"] input`) as HTMLInputElement).click()
    await flushPromises()
  }

  it('replaces an auto-filled amount when switching to another channel', async () => {
    await mountDialog()
    await pickChannel('default_normal') // 自动带入 400
    await pickChannel('tech_normal') // 应覆盖为 300，而不是沿用 400

    findSubmitButton().click()
    await flushPromises()

    expect(mockedCreate.mock.calls[0][0]).toMatchObject({ amount: 300, sellerCompany: 'TECH', invoiceCategory: 'NORMAL' })
  })

  it('clears the auto-filled amount when switching to a pre-settled channel', async () => {
    await mountDialog({ initialChannelKey: 'default_normal', initialAmount: 400 })
    await pickChannel('tech_special') // 未结 -300，不应带着 400 过去

    findSubmitButton().click()
    await flushPromises()

    expect(mockedCreate).not.toHaveBeenCalled()
  })

  it('keeps a manually entered amount when switching channels', async () => {
    const dialog = await mountDialog()
    await pickChannel('default_normal')
    const input = dialog.findComponent({ name: 'ElInputNumber' })
    input.vm.$emit('update:modelValue', 123.45)
    await flushPromises()
    await pickChannel('default_special')

    findSubmitButton().click()
    await flushPromises()

    expect(mockedCreate.mock.calls[0][0]).toMatchObject({ amount: 123.45, sellerCompany: 'DEFAULT', invoiceCategory: 'VAT_SPECIAL' })
  })

  it('asks for confirmation showing channel, amount and the remaining unsettled amount', async () => {
    await mountDialog({ initialChannelKey: 'default_normal', initialAmount: 150 })
    findSubmitButton().click()
    await flushPromises()

    expect(confirmSpy).toHaveBeenCalledTimes(1)
    const message = String(confirmSpy.mock.calls[0][0])
    expect(message).toContain('【普通公司·普票】')
    expect(message).toContain('¥150.00')
    expect(message).toContain('未结将变为 ¥250.00')
    expect(mockedCreate).toHaveBeenCalledTimes(1)
  })

  it('describes pre-settlement in the confirmation when paying more than unsettled', async () => {
    await mountDialog({ initialChannelKey: 'tech_normal', initialAmount: 500 })
    findSubmitButton().click()
    await flushPromises()

    expect(String(confirmSpy.mock.calls[0][0])).toContain('预结 ¥200.00')
  })

  it('does not submit when the confirmation is cancelled', async () => {
    confirmSpy.mockRejectedValue('cancel')
    await mountDialog({ initialChannelKey: 'default_normal', initialAmount: 150 })
    findSubmitButton().click()
    await flushPromises()

    expect(confirmSpy).toHaveBeenCalledTimes(1)
    expect(mockedCreate).not.toHaveBeenCalled()
  })
})
