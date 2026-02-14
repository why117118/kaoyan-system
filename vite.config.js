import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        timeout: 300000,       // 5 min – first model build can take ~90s
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
