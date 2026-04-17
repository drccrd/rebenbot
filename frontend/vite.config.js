import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 3002,
    middleware: true,
    middlewareMode: false,
    hmr: {
      protocol: 'http',
      host: 'localhost',
      port: 3002
    },
    proxy: {
      '^/api/.*': {
        target: process.env.VITE_API_BASE_URL || 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path,
        ws: true
      }
    }
  }
})
