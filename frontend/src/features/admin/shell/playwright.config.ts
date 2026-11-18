import { defineConfig } from '@playwright/test'

/** Local admin browser smoke. Run against VITE_API_MOCK=false; API responses are contract fixtures. */
export default defineConfig({
  testDir: '.',
  testMatch: 'admin.smoke.e2e.ts',
  outputDir: '../../../../../test-results/admin',
  use: {
    locale: 'vi-VN',
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:5174',
    trace: 'retain-on-failure',
  },
})
