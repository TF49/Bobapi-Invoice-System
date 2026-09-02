// @vitest-environment happy-dom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UserInvoice from './UserInvoice.vue'
import { invoiceApi, type Invoice } from '@/api/invoice'
import { quotaApi } from '@/api/quota'
import { useUserStore } from '@/stores/user'

vi.mock('@/api/invoice', () => ({
  invoiceApi: {
    getMyInvoices: vi.fn(),
    createInvoice: vi.fn(),
    previewInvoice: vi.fn(),
    downloadInvoice: vi.fn(),
    parseInvoiceText: vi.fn(),
    verifyInvoiceText: vi.fn(),
    updateProcessed: vi.fn(),
    batchUpdateProcessed: vi.fn(),
    cancelInvoice: vi.fn(),
    applyRedFlush: vi.fn(),
    confirmRedFlush: vi.fn(),
    rejectRedFlush: vi.fn(),
    getPendingRedFlushCount: vi.fn()
  }
}))

vi.mock('@/api/quota', () => ({
  quotaApi: {
    getMyQuota: vi.fn()
  }
}))

vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn()
}))

const mockedQuotaApi = vi.mocked(quotaApi)
const mockedUseUserStore = vi.mocked(useUserStore)

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
      plugins: [ElementPlus, createPinia()],
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
    mockedUseUserStore.mockReturnValue({
      user: { id: 2, username: 'test_user', role: 'USER' }
    } as never)
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

  it('submits a new invoice successfully without tax number', async () => {
    mockedApi.getMyInvoices.mockResolvedValue([completedInvoice])
    mockedApi.createInvoice.mockResolvedValue(completedInvoice)

    const page = await mountPage()
    const openSubmitBtn = page.findAll('button').find(button => button.text().includes('提交申请'))
    expect(openSubmitBtn).toBeDefined()
    await openSubmitBtn!.trigger('click')
    await flushPromises()

    const inputs = page.find('.submit-invoice-dialog').findAll('input')
    await inputs[0].setValue('个人抬头')
    await inputs[1].setValue('')
    await inputs[2].setValue('100.00')

    const submitButtons = page.findAll('button').filter(button => button.text().includes('提交申请'))
    const submit = submitButtons[submitButtons.length - 1]
    await submit!.trigger('click')
    await flushPromises()

    expect(mockedApi.createInvoice).toHaveBeenCalledWith(
      {
        companyName: '个人抬头',
        taxNumber: undefined,
        amount: 100,
        invoiceType: '技术服务费',
        remark: undefined
      },
      expect.stringMatching(/^invoice-[a-z0-9]+-[a-z0-9]+$/)
    )
    expect(ElMessage.success).toHaveBeenCalledWith('提交成功')
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

  it('filters invoices by keyword search and search button', async () => {
    const invoiceA: Invoice = {
      id: 101,
      companyName: '阿里巴巴云计算有限公司',
      taxNumber: '913301007432123456',
      amount: 1000.00,
      invoiceType: '云服务费',
      remark: '8月份账单',
      status: 'COMPLETED',
      userId: 2,
      createdAt: '2026-08-20T10:00:00',
      updatedAt: '2026-08-20T10:00:00',
      downloadable: true,
      fileExists: true,
      fileName: 'ali.png'
    }
    const invoiceB: Invoice = {
      id: 102,
      companyName: '腾讯云计算有限责任公司',
      taxNumber: '91440300123456789X',
      amount: 2000.00,
      invoiceType: '技术服务费',
      remark: '服务器代付',
      status: 'PENDING',
      userId: 2,
      createdAt: '2026-08-22T10:00:00',
      updatedAt: '2026-08-22T10:00:00',
      downloadable: false,
      fileExists: false,
      fileName: ''
    }

    mockedApi.getMyInvoices.mockResolvedValue([invoiceA, invoiceB])

    const page = await mountPage()
    expect(page.find('.result-count').text()).toContain('2 条记录')

    // 1. 按公司名称搜索
    const searchInput = page.find('.search-input input')
    expect(searchInput.exists()).toBe(true)
    await searchInput.setValue('阿里')
    await flushPromises()

    expect(page.find('.result-count').text()).toContain('1 条记录')
    expect(page.text()).toContain('阿里巴巴云计算有限公司')
    expect(page.text()).not.toContain('腾讯云计算有限责任公司')

    // 2. 按税号搜索
    await searchInput.setValue('123456789X')
    await flushPromises()
    expect(page.find('.result-count').text()).toContain('1 条记录')
    expect(page.text()).toContain('腾讯云计算有限责任公司')
    expect(page.text()).not.toContain('阿里巴巴云计算有限公司')

    // 3. 按备注搜索
    await searchInput.setValue('8月份账单')
    await flushPromises()
    expect(page.find('.result-count').text()).toContain('1 条记录')
    expect(page.text()).toContain('阿里巴巴云计算有限公司')

    // 4. 按开票编号搜索 (如 #0102 或 102)
    await searchInput.setValue('#0102')
    await flushPromises()
    expect(page.find('.result-count').text()).toContain('1 条记录')
    expect(page.text()).toContain('腾讯云计算有限责任公司')

    // 5. 测试点击搜索按钮及回车键
    const searchButton = page.findAll('button').find(btn => btn.text().includes('搜索'))
    expect(searchButton?.exists()).toBe(true)
    await searchButton?.trigger('click')
    await flushPromises()
    expect(page.find('.result-count').text()).toContain('1 条记录')

    // 6. 清空搜索关键字
    await searchInput.setValue('')
    await flushPromises()
    expect(page.find('.result-count').text()).toContain('2 条记录')
  })

  it('renders aligned record actions for desktop and mobile layouts', async () => {
    mockedApi.getMyInvoices.mockResolvedValue([completedInvoice])

    const page = await mountPage()

    expect(page.find('.result-count').text()).toContain('1 条记录')
    expect(page.find('.invoice-id').text()).toBe('#0001')
    expect(page.findAll('.record-actions .record-action-button')).toHaveLength(4)
    expect(page.findAll('.mobile-record-actions button')).toHaveLength(4)
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

  it('allows submitting with 研发和技术服务 invoice type', async () => {
    const page = await mountPage()
    const openSubmitBtn = page.findAll('button').find(button => button.text().includes('提交申请'))
    await openSubmitBtn!.trigger('click')
    await flushPromises()

    const inputs = page.find('.submit-invoice-dialog').findAll('input')
    await inputs[0].setValue('某某研发科技有限公司')
    await inputs[1].setValue('91410100MAE5H38A0F')
    await inputs[2].setValue('2000.00')

    const typeSelect = page.findAllComponents({ name: 'ElSelect' }).find(c => c.classes('type-select'))
    if (typeSelect) {
      await typeSelect.setValue('研发和技术服务')
      await flushPromises()
    }

    const submitButtons = page.findAll('button').filter(button => button.text().includes('提交申请'))
    const submit = submitButtons[submitButtons.length - 1]
    await submit!.trigger('click')
    await flushPromises()

    expect(mockedApi.createInvoice).toHaveBeenCalledWith(
      expect.objectContaining({
        companyName: '某某研发科技有限公司',
        taxNumber: '91410100MAE5H38A0F',
        amount: 2000.00,
        invoiceType: '研发和技术服务'
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
  }, 15000)

  it('renders warning danger tag for non-default invoiceType and warning text for remark', async () => {
    mockedApi.getMyInvoices.mockResolvedValue([
      {
        ...completedInvoice,
        id: 10,
        companyName: '自定义类目公司',
        invoiceType: 'AI订阅服务费',
        remark: '请加急开具'
      }
    ])

    const page = await mountPage()
    const warningTag = page.find('.warning-type-tag')
    expect(warningTag.exists()).toBe(true)
    expect(warningTag.text()).toContain('AI订阅服务费')

    const remarkWarning = page.find('.remark-warning-text')
    expect(remarkWarning.exists()).toBe(true)
    expect(remarkWarning.text()).toContain('请加急开具')
  })

  it('renders processed indicator when isProcessed is true and not when false', async () => {
    mockedApi.getMyInvoices.mockResolvedValue([
      {
        ...completedInvoice,
        id: 101,
        companyName: '已处理公司',
        isProcessed: true
      },
      {
        ...completedInvoice,
        id: 102,
        companyName: '未处理公司',
        isProcessed: false
      }
    ])

    const page = await mountPage()
    const indicators = page.findAll('.processed-row-indicator-btn.is-done')
    expect(indicators.length).toBe(1)
  })

  it('calls updateProcessed when checkbox is toggled in preview dialog', async () => {
    const invoice = {
      ...completedInvoice,
      id: 201,
      isProcessed: false
    }
    mockedApi.getMyInvoices.mockResolvedValue([invoice])
    mockedApi.previewInvoice.mockResolvedValue({
      data: new Blob(['fake-image'], { type: 'image/png' })
    } as never)
    mockedApi.updateProcessed.mockResolvedValue({
      ...invoice,
      isProcessed: true
    } as never)

    const page = await mountPage()
    const previewBtn = page.findAll('button').find(b => b.text().includes('查看'))
    expect(previewBtn).toBeDefined()
    await previewBtn!.trigger('click')
    await flushPromises()

    const checkbox = page.find('.preview-processed-check input[type="checkbox"]')
    expect(checkbox.exists()).toBe(true)
    await checkbox.setValue(true)
    await flushPromises()

    expect(mockedApi.updateProcessed).toHaveBeenCalledWith(201, true)
  })

  it('renders cancel button for pending invoices and does not render for completed invoices', async () => {
    const pendingInvoice: Invoice = {
      id: 122,
      companyName: '合肥工业大学',
      taxNumber: '12100000400016984P',
      amount: 420.00,
      invoiceType: '技术服务费',
      remark: '',
      status: 'PENDING',
      userId: 2,
      createdAt: '2026-08-28T15:01:00',
      updatedAt: '2026-08-28T15:01:00',
      downloadable: false,
      fileExists: false
    }

    mockedApi.getMyInvoices.mockResolvedValue([
      pendingInvoice,
      completedInvoice
    ])

    const page = await mountPage()
    const cancelButtons = page.findAll('.cancel-action-btn')
    expect(cancelButtons.length).toBe(1)
    expect(cancelButtons[0].text()).toContain('取消申请')

    // 已开票的发票包含下载/查看等按钮，但不包含取消按钮
    const allButtons = page.findAll('button')
    const completedCancelBtn = allButtons.find(b => b.text().includes('取消申请') && b.element.closest('tr')?.textContent?.includes('测试公司'))
    expect(completedCancelBtn).toBeUndefined()
  })

  it('handles cancellation confirmation and successfully calls cancelInvoice', async () => {
    const pendingInvoice: Invoice = {
      id: 122,
      companyName: '合肥工业大学',
      taxNumber: '12100000400016984P',
      amount: 420.00,
      invoiceType: '技术服务费',
      remark: '',
      status: 'PENDING',
      userId: 2,
      createdAt: '2026-08-28T15:01:00',
      updatedAt: '2026-08-28T15:01:00',
      downloadable: false,
      fileExists: false
    }

    mockedApi.getMyInvoices.mockResolvedValue([pendingInvoice])
    mockedApi.cancelInvoice.mockResolvedValue({
      ...pendingInvoice,
      status: 'CANCELLED'
    } as never)

    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)

    const page = await mountPage()
    const cancelBtn = page.find('.cancel-action-btn')
    expect(cancelBtn.exists()).toBe(true)

    await cancelBtn.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(mockedApi.cancelInvoice).toHaveBeenCalledWith(122)
    expect(ElMessage.success).toHaveBeenCalledWith('发票申请已取消，金额已退还到账户余额')
  })

  it('does not cancel invoice if user cancels confirmation dialog', async () => {
    const pendingInvoice: Invoice = {
      id: 122,
      companyName: '合肥工业大学',
      taxNumber: '12100000400016984P',
      amount: 420.00,
      invoiceType: '技术服务费',
      remark: '',
      status: 'PENDING',
      userId: 2,
      createdAt: '2026-08-28T15:01:00',
      updatedAt: '2026-08-28T15:01:00',
      downloadable: false,
      fileExists: false
    }

    mockedApi.getMyInvoices.mockResolvedValue([pendingInvoice])
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')

    const page = await mountPage()
    const cancelBtn = page.find('.cancel-action-btn')
    await cancelBtn.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(mockedApi.cancelInvoice).not.toHaveBeenCalled()
  })

  it('displays turnaround time notice in submit dialog footer', async () => {
    const page = await mountPage()
    const openSubmitBtn = page.findAll('button').find(button => button.text().includes('提交申请'))
    expect(openSubmitBtn).toBeDefined()
    await openSubmitBtn!.trigger('click')
    await flushPromises()

    const submitTip = page.find('.submit-invoice-dialog .submit-dialog-tip')
    expect(submitTip.exists()).toBe(true)
    expect(submitTip.text()).toContain('开发票需要 2 到 3 个工作日')
  })

  it('opens apply red flush dialog and submits red flush application', async () => {
    mockedApi.getMyInvoices.mockResolvedValue([completedInvoice])
    mockedApi.applyRedFlush.mockResolvedValue({
      ...completedInvoice,
      redFlushStatus: 'PENDING',
      redFlushReason: '抬头填写错误需重开'
    } as never)

    const page = await mountPage()
    const dropdown = page.findComponent({ name: 'ElDropdown' })
    if (dropdown.exists()) {
      dropdown.vm.$emit('command', 'applyRedFlush')
    } else {
      const redFlushBtn = page.find('.red-flush-action-btn')
      expect(redFlushBtn.exists()).toBe(true)
      await redFlushBtn.trigger('click')
    }
    await flushPromises()

    // 弹窗可见，输入申请原因
    const redFlushDialog = page.find('.red-flush-dialog')
    expect(redFlushDialog.exists()).toBe(true)
    const textarea = redFlushDialog.find('textarea')
    await textarea.setValue('企业抬头填写有误需重开')
    await flushPromises()

    // 点击提交红冲申请
    const submitRedFlushBtn = redFlushDialog.findAll('.dialog-footer button').find(b => b.text().includes('确认提交红冲申请'))
    expect(submitRedFlushBtn).toBeDefined()
    await submitRedFlushBtn!.trigger('click')
    await flushPromises()

    expect(mockedApi.applyRedFlush).toHaveBeenCalledWith(completedInvoice.id, expect.any(String))
    expect(ElMessage.success).toHaveBeenCalledWith('红冲申请已提交，请等待开票员核验标记')
  })
})
