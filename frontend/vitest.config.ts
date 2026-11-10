import { configDefaults, defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(viteConfig, defineConfig({
  test: {
    exclude: [...configDefaults.exclude, 'e2e/**'],
    testTimeout: 20000,
    server: {
      deps: {
        // 让 Vite 处理 element-plus，使其对 async-validator（CJS）的默认导入正确互操作；
        // 否则 Node 直接加载时表单校验会抛错并被 el-form 当作“校验通过”。
        inline: ['element-plus']
      }
    }
  }
}))
