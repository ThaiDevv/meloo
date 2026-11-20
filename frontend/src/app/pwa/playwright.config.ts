import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: '.',
  testMatch: 'pwa.spec.ts',
  fullyParallel: true,
  use: { baseURL: 'http://localhost:4179', trace: 'retain-on-failure' },
  projects: [
    { name: 'desktop-vi', use: { ...devices['Desktop Chrome'], locale: 'vi-VN' } },
    { name: 'desktop-en', use: { ...devices['Desktop Chrome'], locale: 'en-US' } },
    { name: 'android-vi', use: { ...devices['Pixel 7'], locale: 'vi-VN' } },
    { name: 'phone-360-vi', use: { viewport: { width: 360, height: 800 }, locale: 'vi-VN' } },
  ],
  webServer: {
    command: 'pnpm preview --host localhost --port 4179 --strictPort',
    url: 'http://localhost:4179',
    reuseExistingServer: !process.env.CI,
    cwd: import.meta.dirname + '/../../..',
  },
})
