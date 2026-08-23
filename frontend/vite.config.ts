import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

const timestamp = Date.now()

export default defineConfig({
  define: {
    'import.meta.env.VITE_BUILD_TIME': JSON.stringify(timestamp)
  },
  plugins: [
    vue(),
    {
      name: 'html-transform-build-timestamp',
      transformIndexHtml(html) {
        return html.replace(
          '</head>',
          `  <meta name="build-time" content="${timestamp}">\n  </head>`
        )
      }
    }
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  build: {
    // 经过 Element Plus 与 ECharts 按需加载后，最大业务块约 560 kB。
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        entryFileNames: `assets/[name]-[hash]-${timestamp}.js`,
        chunkFileNames: `assets/[name]-[hash]-${timestamp}.js`,
        assetFileNames: `assets/[name]-[hash]-${timestamp}.[ext]`
      }
    }
  },
  server: {
    port: 8080,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:9090',
        changeOrigin: true
      }
    }
  }
})
