import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  build: {
    // 经过 Element Plus 与 ECharts 按需加载后，最大业务块约 560 kB。
    chunkSizeWarningLimit: 600
  },
  server: {
    port: 8080,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://47.108.25.167:9090',
        changeOrigin: true
      }
    }
  }
})
