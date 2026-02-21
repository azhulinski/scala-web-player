import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    allowedHosts: [
      '8fea7e3a7546.ngrok.app',
    ],
    proxy: {
      '/list': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/stream': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/folders': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})

