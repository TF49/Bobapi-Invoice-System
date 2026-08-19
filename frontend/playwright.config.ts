import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  // E2E 测试文件目录
  testDir: './e2e',

  // 测试文件匹配模式
  testMatch: '**/*.spec.ts',

  // 全局超时（每个测试最多 60 秒）
  timeout: 60_000,

  // 期望断言超时（等待元素出现最多 15 秒）
  expect: {
    timeout: 15_000,
  },

  // 失败时不重试（CI 可设为 2）
  retries: 0,

  // 并发 worker 数量（E2E 避免并发以防数据干扰）
  workers: 1,

  // 报告格式：终端 + HTML 报告
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],

  use: {
    // 前端开发服务器地址
    baseURL: 'http://localhost:8080',

    // 截图：仅在失败时保留
    screenshot: 'only-on-failure',

    // 视频：仅在失败时保留
    video: 'retain-on-failure',

    // 每个操作超时
    actionTimeout: 10_000,

    // 导航超时
    navigationTimeout: 20_000,
  },

  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // 运行时不显示浏览器窗口（CI 模式）
        headless: true,
        viewport: { width: 1280, height: 800 },
      },
    },
  ],
})
