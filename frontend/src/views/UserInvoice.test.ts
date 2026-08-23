// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UserInvoice from './UserInvoice.vue'
import { invoiceApi, type Invoice } from '@/api/invoice'
import { quotaApi } from '@/api/quota'

vi.mock('@/api/invoice', () => ({
  invoiceApi: {
    getMyInvoices: vi.fn(),
    createInvoice: vi.fn(),
    previewInvoice: vi.fn(),
    downloadInvoice: vi.fn(),
    parseInvoiceText: vi.fn(),
    verifyInvoiceText: vi.fn()
  }
}))

vi.mock('@/api/quota', () => ({
  quotaApi: {
    getMyQuota: vi.fn()
  }
}))

const mockedQuotaApi = vi.mocked(quotaApi)

const mockedApi = vi.mocked(invoiceApi)
let wrapper: VueWrapper | null = null

const completedInvoice: Invoice = {
  id: 1,
  companyName: '测试公司',
  taxNumber: '91410100MAE5H38A0F',
  amount: 300.01,
  invoiceType: '技术服务费',
  remark: '',
  status: 'COMPLETED',
  userId: 2,
  createdAt: '2026-08-19T21:52:00',
  updatedAt: '2026-08-19T21:53:00',
  downloadable: true,
  fileExists: true,
  fileName: 'invoice.png'
}

async function mountPage() {
  wrapper = mount(UserInvoice, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      stubs: {
        AppHeader: { template: '<header>我的发票</header>' },
        AnimatedContent: { template: '<section><slot /></section>' },
        SpotlightCard: { template: '<div><slot /></div>' },
        CountUp: { props: ['value', 'decimals', 'prefix'], template: '<span>{{ prefix }}{{ value }}</span>' },
        InvoiceBatchImportDialog: { template: '<div />' }
      }
    }
  })
  await flushPromises()
  return wrapper
}

describe('UserInvoice', () => {
  beforeEach(() => {
    mockedApi.getMyInvoices.mockResolvedValue([])
    mockedApi.createInvoice.mockResolvedValue({} as never)
    // 默认提供足够的额度，避免提交时被额度检查拦截
    mockedQuotaApi.getMyQuota.mockResolvedValue({
      userId: 1,
      balance: 99999,
      totalRecharged: 99999,
      totalDeducted: 0
    })
    vi.stubGlobal('crypto', {
      getRandomValues: (values: Uint32Array) => {
        values.set([11, 22, 33, 44])
        return values
      }
    })
    vi.spyOn(ElMessage, 'success').mockImplementation(() => undefined as never)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('submits successfully when randomUUID is unavailable', async () => {
    const page = await mountPage()
    const openSubmitBtn = page.findAll('button').find(button => button.text().includes('提交申请'))
    expect(openSubmitBtn).toBeDefined()
    await openSubmitBtn!.trigger('click')
    await flushPromises()

    const inputs = page.findAll('input')

    await inputs[0].setValue(' 测试公司 ')
    await inputs[1].setValue('91410100MAE5H38A0F')
    await inputs[2].setValue('300.01')

    const submitButtons = page.findAll('button').filter(button => button.text().includes('提交申请'))
    const submit = submitButtons[submitButtons.length - 1]
    expect(submit).toBeDefined()
    await submit!.trigger('click')
    await flushPromises()

    expect(mockedApi.createInvoice).toHaveBeenCalledWith(
      {
        companyName: '测试公司',
        taxNumber: '91410100MAE5H38A0F',
        amount: 300.01,
        invoiceType: '技术服务费',
        remark: undefined
      },
      expect.stringMatching(/^invoice-[a-z0-9]+-[a-z0-9]+$/)
    )
    expect(ElMessage.success).toHaveBeenCalledWith('提交成功')
    expect(mockedApi.getMyInvoices).toHaveBeenCalledTimes(2)
  })

  it('renders aligned record actions for desktop and mobile layouts', async () => {
    mockedApi.getMyInvoices.mockResolvedValue([completedInvoice])

    const page = await mountPage()

    expect(page.find('.result-count').text()).toContain('1 条记录')
    expect(page.find('.invoice-id').text()).toBe('#0001')
    expect(page.findAll('.record-actions button')).toHaveLength(2)
    expect(page.findAll('.mobile-record-actions button')).toHaveLength(2)
    expect(page.find('.mobile-records').text()).toContain('¥300.01')
  })

  it('extracts, verifies, confirms and submits when using AI smart parse', async () => {
    mockedApi.parseInvoiceText.mockResolvedValue({
      companyName: '某某智能科技有限公司',
      taxNumber: '91110108MA01TEST99',
      amount: 888.5,
      confidence: 'HIGH',
      hint: null
    })
    mockedApi.verifyInvoiceText.mockResolvedValue({
      companyName: '某某智能科技有限公司（核查）',
      taxNumber: '91110108MA01TEST99',
      amount: 888.5,
      confidence: 'HIGH',
      hint: null
    })

    const page = await mountPage()
    const openSubmitBtn = page.findAll('button').find(button => button.text().includes('提交申请'))
    await openSubmitBtn!.trigger('click')
    await flushPromises()

    // 点击展开 AI 识别
    const aiToggleBtn = page.find('.ai-parse-header')
    expect(aiToggleBtn.exists()).toBe(true)
    await aiToggleBtn.trigger('click')
    await flushPromises()

    // 输入原始发票文本
    const aiTextarea = page.find('.ai-parse-body textarea')
    expect(aiTextarea.exists()).toBe(true)
    await aiTextarea.setValue('请开具发票，抬头：某某智能科技有限公司，税号：91110108MA01TEST99，金额：888.5元')

    // 点击识别按钮
    vi.useFakeTimers()
    const aiParseBtn = page.findAll('.ai-parse-actions button').find(b => b.text().includes('识别'))
    expect(aiParseBtn).toBeDefined()
    await aiParseBtn!.trigger('click')

    // 阶段 1：推进 2000ms 动画，刷新 Promise 启动阶段 2
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()

    // 阶段 2：推进 2000ms 动画，刷新 Promise 启动完成阶段
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()

    // 完成阶段 100% 动画：推进 1000ms 动画
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    // 完成阶段 200ms 延迟：推进 500ms 触发 setTimeout 延迟并弹出确认弹窗
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()

    vi.useRealTimers()
    await flushPromises()

    // 验证确认弹窗已出现
    expect(document.body.innerHTML).toContain('AI 识别结果确认')

    // 点击确认并提交按钮
    const buttons = Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
    const confirmBtn = buttons.find(b => b.textContent?.includes('确认并提交'))
    expect(confirmBtn).toBeDefined()
    confirmBtn!.click()
    await flushPromises()

    // 验证发票申请提交成功
    expect(mockedApi.createInvoice).toHaveBeenCalledWith(
      expect.objectContaining({
        companyName: '某某智能科技有限公司（核查）',
        taxNumber: '91110108MA01TEST99',
        amount: 888.5
      }),
      expect.any(String)
    )
    expect(ElMessage.success).toHaveBeenCalledWith('提交成功')
  })
})
