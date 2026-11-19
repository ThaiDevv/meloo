import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: '.',
  testMatch: 'admin-tools.smoke.e2e.ts',
  outputDir: '../../../../../test-results/admin-tools',
  use: {
    locale: 'vi-VN',
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:5177',
    trace: 'retain-on-failure',
  },
})
