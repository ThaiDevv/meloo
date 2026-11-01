import { http, HttpResponse } from 'msw'
import { authHandlers } from '@/features/auth/mocks'
import { soloPlayHandlers } from '@/features/solo/play/mocks/handlers'

/**
 * Mock handlers, enabled with VITE_API_MOCK=true. Feature tickets add handlers here
 * from contracts/examples/**.
 */
export const handlers = [
  http.get('/api/health', () => HttpResponse.json({ status: 'UP' })),
  ...authHandlers,
  ...soloPlayHandlers,
]
