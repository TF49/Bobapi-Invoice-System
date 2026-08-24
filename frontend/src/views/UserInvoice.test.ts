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

    const inputs = page.find('.submit-invoice-dialog').findAll('input')

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

  it('filters invoices by status and upload time date range', async () => {
    const pendingInvoice: Invoice = {
      id: 2,
      companyName: '待开票公司',
      taxNumber: '91410100MAE5H38A0F',
      amount: 150.00,
      invoiceType: '技术服务费',
      remark: '',
      status: 'PENDING',
      userId: 2,
      createdAt: '2026-08-24T10:00:00',
      updatedAt: '2026-08-24T10:00:00',
      downloadable: false,
      fileExists: false,
      fileName: ''
    }
    const olderCompletedInvoice: Invoice = {
      ...completedInvoice,
      id: 3,
      companyName: '较早公司',
      createdAt: '2026-07-01T10:00:00'
    }

    mockedApi.getMyInvoices.mockResolvedValue([completedInvoice, pendingInvoice, olderCompletedInvoice])

    const page = await mountPage()
    expect(page.find('.result-count').text()).toContain('3 条记录')

    // 筛选状态为 PENDING (待开票)
    const statusComponent = page.findComponent({ name: 'ElSelect' })
    await statusComponent.setValue('PENDING')
    await flushPromises()

    expect(page.find('.result-count').text()).toContain('1 条记录')
    expect(page.text()).toContain('待开票公司')
    expect(page.text()).not.toContain('较早公司')

    // 重置状态筛选，按时间段筛选
    await statusComponent.setValue('ALL')
    await flushPromises()
    expect(page.find('.result-count').text()).toContain('3 条记录')

    // 设置日期范围 2026-08-15 至 2026-08-25
    const datePickerComponent = page.findComponent({ name: 'ElDatePicker' })
    await datePickerComponent.setValue(['2026-08-15', '2026-08-25'])
    await flushPromises()

    expect(page.find('.result-count').text()).toContain('2 条记录')
    expect(page.text()).not.toContain('较早公司')
  })

  it('renders aligned record actions for desktop and mobile layouts', async () => {
    mockedApi.getMyInvoices.mockResolvedValue([completedInvoice])

    const page = await mountPage()

    expect(page.find('.result-count').text()).toContain('1 条记录')
    expect(page.find('.invoice-id').text()).toBe('#0001')
    expect(page.findAll('.record-actions button')).toHaveLength(3)
    expect(page.findAll('.mobile-record-actions button')).toHaveLength(3)
    expect(page.find('.mobile-records').text()).toContain('¥300.01')
  })

  it('extracts, verifies, confirms and submits when using AI smart parse', async () => {
    mockedApi.parseInvoiceText.mockResolvedValue({
      companyName: '某某智能科技有限公司',
      taxNumber: '91110108MA01TEST99',
      amount: 888.5,
      invoiceType: null,
      confidence: 'HIGH',
      hint: null
    })
    mockedApi.verifyInvoiceText.mockResolvedValue({
      companyName: '某某智能科技有限公司（核查）',
      taxNumber: '91110108MA01TEST99',
      amount: 888.5,
      invoiceType: null,
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
    expect(document.body.innerHTML).toContain('AI 识别结果')

    // 点击「填入表单」按钮回填表单
    const buttons = Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
    const fillBtn = buttons.find(b => b.textContent?.includes('填入表单'))
    expect(fillBtn).toBeDefined()
    fillBtn!.click()
    await flushPromises()

    // 点击表单的「提交申请」按钮提交发票
    const submitBtn = Array.from(document.querySelectorAll<HTMLButtonElement>('.submit-invoice-dialog .dialog-footer button'))
      .find(b => b.textContent?.includes('提交申请'))
    expect(submitBtn).toBeDefined()
    submitBtn!.click()
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

  it('fills AI-recognized invoiceType into form when AI returns a non-default type', async () => {
    // AI 识别出文本中包含「AI订阅服务费」
    mockedApi.parseInvoiceText.mockResolvedValue({
      companyName: '某某AI科技有限公司',
      taxNumber: '91110108MA01TEST99',
      amount: 500.0,
      invoiceType: 'AI订阅服务费',
      confidence: 'HIGH',
      hint: null
    })
    mockedApi.verifyInvoiceText.mockResolvedValue({
      companyName: '某某AI科技有限公司',
      taxNumber: '91110108MA01TEST99',
      amount: 500.0,
      invoiceType: 'AI订阅服务费',
      confidence: 'HIGH',
      hint: null
    })

    const page = await mountPage()
    const openSubmitBtn = page.findAll('button').find(button => button.text().includes('提交申请'))
    await openSubmitBtn!.trigger('click')
    await flushPromises()

    const aiToggleBtn = page.find('.ai-parse-header')
    await aiToggleBtn.trigger('click')
    await flushPromises()

    const aiTextarea = page.find('.ai-parse-body textarea')
    await aiTextarea.setValue('AI订阅服务费 某某AI科技有限公司 税号：91110108MA01TEST99 金额：500元')

    vi.useFakeTimers()
    const aiParseBtn = page.findAll('.ai-parse-actions button').find(b => b.text().includes('识别'))
    await aiParseBtn!.trigger('click')

    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()

    vi.useRealTimers()
    await flushPromises()

    // 验证确认弹窗显示了正确的 AI 识别类型
    expect(document.body.innerHTML).toContain('AI订阅服务费')

    // 点击「填入表单」
    const fillBtn = Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
      .find(b => b.textContent?.includes('填入表单'))
    expect(fillBtn).toBeDefined()
    fillBtn!.click()
    await flushPromises()

    // 验证 verifyInvoiceText 第二阶段调用时携带了第一阶段识别的 invoiceType
    expect(mockedApi.verifyInvoiceText).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({ invoiceType: 'AI订阅服务费' })
    )

    // 提交申请，验证最终提交时使用的是 AI 识别出的 invoiceType
    const submitBtn = Array.from(document.querySelectorAll<HTMLButtonElement>('.submit-invoice-dialog .dialog-footer button'))
      .find(b => b.textContent?.includes('提交申请'))
    submitBtn!.click()
    await flushPromises()

    expect(mockedApi.createInvoice).toHaveBeenCalledWith(
      expect.objectContaining({
        companyName: '某某AI科技有限公司',
        taxNumber: '91110108MA01TEST99',
        amount: 500.0,
        invoiceType: 'AI订阅服务费'
      }),
      expect.any(String)
    )
  })

  it('allows submitting with AI订阅服务费 invoice type', async () => {
    const page = await mountPage()
    const openSubmitBtn = page.findAll('button').find(button => button.text().includes('提交申请'))
    await openSubmitBtn!.trigger('click')
    await flushPromises()

    const inputs = page.find('.submit-invoice-dialog').findAll('input')
    await inputs[0].setValue('某某AI科技有限公司')
    await inputs[1].setValue('91410100MAE5H38A0F')
    await inputs[2].setValue('500.00')

    const typeSelect = page.findAllComponents({ name: 'ElSelect' }).find(c => c.classes('type-select'))
    if (typeSelect) {
      await typeSelect.setValue('AI订阅服务费')
      await flushPromises()
    }

    const submitButtons = page.findAll('button').filter(button => button.text().includes('提交申请'))
    const submit = submitButtons[submitButtons.length - 1]
    await submit!.trigger('click')
    await flushPromises()

    expect(mockedApi.createInvoice).toHaveBeenCalledWith(
      expect.objectContaining({
        companyName: '某某AI科技有限公司',
        taxNumber: '91410100MAE5H38A0F',
        amount: 500.00,
        invoiceType: 'AI订阅服务费'
      }),
      expect.any(String)
    )
  })

  it('allows submitting with 计算服务费 invoice type', async () => {
    const page = await mountPage()
    const openSubmitBtn = page.findAll('button').find(button => button.text().includes('提交申请'))
    await openSubmitBtn!.trigger('click')
    await flushPromises()

    const inputs = page.find('.submit-invoice-dialog').findAll('input')
    await inputs[0].setValue('某某算力科技有限公司')
    await inputs[1].setValue('91410100MAE5H38A0F')
    await inputs[2].setValue('1000.00')

    const typeSelect = page.findAllComponents({ name: 'ElSelect' }).find(c => c.classes('type-select'))
    if (typeSelect) {
      await typeSelect.setValue('计算服务费')
      await flushPromises()
    }

    const submitButtons = page.findAll('button').filter(button => button.text().includes('提交申请'))
    const submit = submitButtons[submitButtons.length - 1]
    await submit!.trigger('click')
    await flushPromises()

    expect(mockedApi.createInvoice).toHaveBeenCalledWith(
      expect.objectContaining({
        companyName: '某某算力科技有限公司',
        taxNumber: '91410100MAE5H38A0F',
        amount: 1000.00,
        invoiceType: '计算服务费'
      }),
      expect.any(String)
    )
  })

  it('renders pagination and paginates items when multiple pages exist', async () => {
    const manyInvoices: Invoice[] = Array.from({ length: 25 }, (_, i) => ({
      ...completedInvoice,
      id: i + 1,
      companyName: `测试公司_${i + 1}`
    }))
    mockedApi.getMyInvoices.mockResolvedValue(manyInvoices)

    const page = await mountPage()
    expect(page.find('.result-count').text()).toContain('25 条记录')
    expect(page.find('.pagination-bar').exists()).toBe(true)
    // 默认每页 10 条
    expect(page.text()).toContain('测试公司_1')
    expect(page.text()).toContain('测试公司_10')
    expect(page.text()).not.toContain('测试公司_11')
  })
})
