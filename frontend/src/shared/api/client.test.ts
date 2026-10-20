import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import { createApiClient } from './client'

describe('apiClient', () => {
  it('requests contract paths as-is, without doubling /api', async () => {
    let requested = ''
    server.use(
      http.get('*/api/v1/health', ({ request }) => {
        requested = new URL(request.url).pathname
        return HttpResponse.json({ status: 'UP' })
      }),
    )
    // jsdom has no origin for relative fetch, so use an absolute origin like a split deployment.
    await createApiClient('http://localhost').GET('/api/v1/health' as never)
    expect(requested).toBe('/api/v1/health')
  })
})
