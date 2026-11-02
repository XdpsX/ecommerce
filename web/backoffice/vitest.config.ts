import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    env: { VITE_API_BASE_URL: 'http://localhost:8080' },
    maxWorkers: 2,
    environment: 'jsdom',
    environmentOptions: {
      jsdom: {
        url: 'http://localhost:8080',
      },
    },
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['./src/test/setup.ts'],
  },
})
