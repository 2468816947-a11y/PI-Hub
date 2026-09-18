import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// 文档约定(04-接口文档.md 附录 C):
// - 开发期 /api 代理到 Nginx 统一入口 http://localhost:80, 与生产路径一致, 不产生跨域
// - 构建产物直接输出到 deploy/nginx/html, 由 Nginx 容器挂载托管(docker-compose.yml)
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:80', changeOrigin: true }
    }
  },
  build: {
    outDir: '../deploy/nginx/html',
    emptyOutDir: true
  }
})
