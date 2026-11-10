import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  server: {
    port: 3000,
    strictPort: true,
    proxy: {
      '/bff': { target: process.env.BFF_UPSTREAM ?? 'http://localhost:8081' },
      '/oauth2': {
        target: process.env.BFF_UPSTREAM ?? 'http://localhost:8081',
      },
      '/login/oauth2': {
        target: process.env.BFF_UPSTREAM ?? 'http://localhost:8081',
      },
      '/api': { target: process.env.BFF_UPSTREAM ?? 'http://localhost:8081' },
    },
  },
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
