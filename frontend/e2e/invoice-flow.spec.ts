import { test, expect, request } from '@playwright/test'
import path from 'path'
import { fileURLToPath } from 'url'

// ─── 测试配置常量 ───────────────────────────────────────────────────────────
const BASE_URL   = process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:8080'
const API_URL    = process.env.PLAYWRIGHT_API_URL || 'http://localhost:9090/api'
const ADMIN_USER = { username: 'admin', password: 'admin123' }
const TEST_USER  = { username: 'user',  password: 'user123'  }
const FIXTURE_PNG = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  'fixtures/test_invoice.png'
)

test.describe.configure({ mode: 'serial' })

// 每次测试用带时间戳的公司名，方便识别测试数据
const COMPANY_NAME = `E2E测试公司_${Date.now()}`
const TAX_NUMBER   = 'E2ETEST12345678'   // 15 位大写字母数字

// ─── 辅助函数 ──────────────────────────────────────────────────────────────
/** 通过 UI 登录，等待跳转完成 */
async function loginAs(page: import('@playwright/test').Page, user: { username: string; password: string }) {
  await page.goto('/login')
  await page.getByLabel('用户名').fill(user.username)
  await page.getByLabel('密码').fill(user.password)
  await page.getByRole('button', { name: '登录系统' }).click()
}

// ─── 场景 1：普通用户登录并提交发票申请 ─────────────────────────────────────
test('用户登录并提交发票申请', async ({ page }) => {
  await loginAs(page, TEST_USER)

  // 登录后应跳转到用户页
  await expect(page).toHaveURL(`${BASE_URL}/user`)

  // 填写申请表单
  await page.getByLabel('公司名称').fill(COMPANY_NAME)
  await page.getByLabel('税号').fill(TAX_NUMBER)

  // 金额输入框（el-input-number）：先清空再输入
  const amountInput = page.locator('.amount-input input')
  await amountInput.click({ clickCount: 3 })
  await amountInput.fill('1000')

  // 提交
  await page.getByRole('button', { name: '提交申请' }).click()

  // 等待成功提示
  await expect(page.locator('.el-message--success')).toBeVisible()

  // 列表中出现新申请，状态为"待开票"
  const row = page.locator('tr', { hasText: COMPANY_NAME })
  await expect(row).toBeVisible()
  await expect(row.locator('.el-tag')).toHaveText('待开票')
})

// ─── 场景 2：管理员查看申请并上传发票图片 ───────────────────────────────────
test('管理员查看申请并上传发票图片', async ({ page }) => {
  await loginAs(page, ADMIN_USER)
  await expect(page).toHaveURL(`${BASE_URL}/admin`)

  // 找到目标申请行（公司名匹配）
  const row = page.locator('tr', { hasText: COMPANY_NAME })
  await expect(row).toBeVisible({ timeout: 20_000 })

  // el-upload 的原生 input 是隐藏元素，直接设置文件可稳定触发 change 事件。
  await row.locator('.el-upload input[type="file"]').setInputFiles(FIXTURE_PNG)

  // 等待上传成功提示
  await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 20_000 })

  // 等待列表刷新，状态变为"已开票"
  await expect(row.locator('.el-tag')).toHaveText('已开票', { timeout: 20_000 })
})

// ─── 场景 3：用户刷新后能看到已开票状态并预览图片 ───────────────────────────
test('用户查看已开票发票并预览图片', async ({ page }) => {
  await loginAs(page, TEST_USER)
  await expect(page).toHaveURL(`${BASE_URL}/user`)

  // 找到已完成的申请行
  const row = page.locator('tr', { hasText: COMPANY_NAME })
  await expect(row).toBeVisible({ timeout: 20_000 })
  await expect(row.locator('.el-tag')).toHaveText('已开票')

  // 点击"查看"按钮
  await row.getByRole('button', { name: '查看' }).click()

  // 弹窗应出现
  const dialog = page.locator('.el-dialog')
  await expect(dialog).toBeVisible()

  // 弹窗内的图片应成功加载（img 元素出现）
  await expect(dialog.locator('img.preview-image')).toBeVisible({ timeout: 20_000 })
})

// ─── 场景 4：普通用户 token 越权访问管理员接口应被拒绝（403）────────────────
test('普通用户越权访问管理员接口返回 403', async ({ page }) => {
  // 先通过 UI 登录获取 token
  await loginAs(page, TEST_USER)
  await expect(page).toHaveURL(`${BASE_URL}/user`)

  // 从 localStorage 取出 token
  const token = await page.evaluate(() => localStorage.getItem('token'))
  expect(token).not.toBeNull()

  // 直接发起 API 请求（绕过前端路由守卫）
  const apiContext = await request.newContext({ baseURL: `${API_URL.replace(/\/$/, '')}/` })
  const response = await apiContext.get('invoices/admin/all', {
    headers: { Authorization: `Bearer ${token}` },
  })

  // 应返回 403 Forbidden
  expect(response.status()).toBe(403)
  await apiContext.dispose()
})

// ─── 场景 5：管理员查看 Dashboard 并切换趋势图 ─────────────────────────────
test('管理员查看 Dashboard 统计和切换趋势图', async ({ page }) => {
  await loginAs(page, ADMIN_USER)
  await page.getByRole('link', { name: '数据概览' }).click()

  await expect(page).toHaveURL(`${BASE_URL}/dashboard`)
  await expect(page.getByText('发票总数', { exact: true })).toBeVisible()
  await expect(page.getByText('用户发票分布', { exact: true })).toBeVisible()
  await expect(page.locator('.chart-container')).toHaveCount(2)

  await page.getByText('折线图', { exact: true }).click()
  await expect(page.getByRole('radio', { name: '折线图' })).toBeChecked()
})
