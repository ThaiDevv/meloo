import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest'
import i18n from '@/i18n'
import { server } from '@/mocks/server'

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' })
})
beforeEach(async () => {
  await i18n.changeLanguage('vi')
})
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => {
  server.close()
})
