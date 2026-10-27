import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import { useAuthStore, type AuthSession } from './authStore'
import { createApiClient } from './client'

const BASE = 'http://localhost'

const meBody = {
  id: '7c1d2a64-3b5e-4f08-9a1b-2e6f0c4d8a11',
  kind: 'GUEST',
  role: 'USER',
  nickname: 'Meloman',
  createdAt: '2026-10-23T13:10:00Z',
} as const

function sessionWith(accessToken: string): AuthSession {
  return { accessToken, tokenType: 'Bearer', expiresAt: '2026-10-23T13:40:00Z', user: meBody }
}

const unauthorized = () =>
  HttpResponse.json(
    { type: 'about:blank', title: 'Unauthorized', status: 401, code: 'UNAUTHORIZED' },
    { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
  )

afterEach(() => {
  useAuthStore.getState().clear()
})

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
    await createApiClient(BASE).GET('/api/v1/health' as never)
    expect(requested).toBe('/api/v1/health')
  })

  it('sends the bearer token when there is one, and none when there is not', async () => {
    const seen: (string | null)[] = []
    server.use(
      http.get('*/api/v1/me', ({ request }) => {
        seen.push(request.headers.get('Authorization'))
        return HttpResponse.json(meBody)
      }),
    )
    const client = createApiClient(BASE)

    await client.GET('/api/v1/me')
    useAuthStore.getState().setSession(sessionWith('token-a'))
    await client.GET('/api/v1/me')

    expect(seen).toEqual([null, 'Bearer token-a'])
  })

  it('refreshes once when many requests get 401 together, then replays each of them', async () => {
    useAuthStore.getState().setSession(sessionWith('expired-token'))
    let refreshCalls = 0
    server.use(
      http.get('*/api/v1/me', ({ request }) =>
        request.headers.get('Authorization') === 'Bearer new-token'
          ? HttpResponse.json(meBody)
          : unauthorized(),
      ),
      http.post('*/api/v1/auth/refresh', async () => {
        refreshCalls++
        await new Promise((resolve) => setTimeout(resolve, 30))
        return HttpResponse.json(sessionWith('new-token'))
      }),
    )
    const client = createApiClient(BASE)

    const results = await Promise.all([
      client.GET('/api/v1/me'),
      client.GET('/api/v1/me'),
      client.GET('/api/v1/me'),
      client.GET('/api/v1/me'),
    ])

    expect(refreshCalls).toBe(1)
    expect(results.map((r) => r.response.status)).toEqual([200, 200, 200, 200])
    expect(useAuthStore.getState().accessToken).toBe('new-token')
  })

  it('replays a request that was already in flight when the token changed, without a second refresh', async () => {
    useAuthStore.getState().setSession(sessionWith('expired-token'))
    let refreshCalls = 0
    server.use(
      http.get('*/api/v1/me', async ({ request }) => {
        const token = request.headers.get('Authorization')
        if (token === 'Bearer new-token') return HttpResponse.json(meBody)
        // The slow request is answered only after the fast one finished refreshing.
        if (request.headers.get('X-Slow')) await new Promise((resolve) => setTimeout(resolve, 80))
        return unauthorized()
      }),
      http.post('*/api/v1/auth/refresh', () => {
        refreshCalls++
        return HttpResponse.json(sessionWith('new-token'))
      }),
    )
    const client = createApiClient(BASE)

    const [fast, slow] = await Promise.all([
      client.GET('/api/v1/me'),
      client.GET('/api/v1/me', { headers: { 'X-Slow': '1' } }),
    ])

    expect(fast.response.status).toBe(200)
    expect(slow.response.status).toBe(200)
    expect(refreshCalls).toBe(1)
  })

  it('replays a request body after the refresh', async () => {
    useAuthStore.getState().setSession(sessionWith('expired-token'))
    const bodies: unknown[] = []
    server.use(
      http.post('*/api/v1/solo/matches', async ({ request }) => {
        if (request.headers.get('Authorization') !== 'Bearer new-token') return unauthorized()
        bodies.push(await request.json())
        return HttpResponse.json({}, { status: 500 })
      }),
      http.post('*/api/v1/auth/refresh', () => HttpResponse.json(sessionWith('new-token'))),
    )

    const { response } = await createApiClient(BASE).POST('/api/v1/solo/matches', {
      body: { mode: 'PRACTICE' } as never,
    })

    expect(response.status).toBe(500)
    expect(bodies).toEqual([{ mode: 'PRACTICE' }])
  })

  it('gives up with the original 401 and signs out when the refresh says the session is over', async () => {
    useAuthStore.getState().setSession(sessionWith('expired-token'))
    let meCalls = 0
    server.use(
      http.get('*/api/v1/me', () => {
        meCalls++
        return unauthorized()
      }),
      http.post('*/api/v1/auth/refresh', unauthorized),
    )

    const { response } = await createApiClient(BASE).GET('/api/v1/me')

    expect(response.status).toBe(401)
    expect(meCalls).toBe(1)
    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('replays only once: a second 401 is returned as is', async () => {
    useAuthStore.getState().setSession(sessionWith('expired-token'))
    let meCalls = 0
    let refreshCalls = 0
    server.use(
      http.get('*/api/v1/me', () => {
        meCalls++
        return unauthorized()
      }),
      http.post('*/api/v1/auth/refresh', () => {
        refreshCalls++
        return HttpResponse.json(sessionWith('new-token'))
      }),
    )

    const { response } = await createApiClient(BASE).GET('/api/v1/me')

    expect(response.status).toBe(401)
    expect(meCalls).toBe(2)
    expect(refreshCalls).toBe(1)
  })

  it('never refreshes because of a 401 from an auth endpoint', async () => {
    let refreshCalls = 0
    server.use(
      http.post('*/api/v1/auth/guest', unauthorized),
      http.post('*/api/v1/auth/refresh', () => {
        refreshCalls++
        return HttpResponse.json(sessionWith('x'))
      }),
    )

    const { response } = await createApiClient(BASE).POST('/api/v1/auth/guest', {
      body: { nickname: 'Meloman' },
    })

    expect(response.status).toBe(401)
    expect(refreshCalls).toBe(0)
  })
})
