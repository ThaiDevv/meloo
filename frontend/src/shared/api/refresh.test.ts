import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import { useSessionStore } from '@/shared/session/store'
import { useAuthStore, type AuthSession } from './authStore'
import { refreshSession } from './refresh'

const BASE = 'http://localhost'

const session: AuthSession = {
  accessToken: 'fresh-token',
  tokenType: 'Bearer',
  expiresAt: '2026-10-23T13:40:00Z',
  user: {
    id: '7c1d2a64-3b5e-4f08-9a1b-2e6f0c4d8a11',
    kind: 'GUEST',
    role: 'USER',
    nickname: 'Meloman',
    createdAt: '2026-10-23T13:10:00Z',
  },
}

function problem(status: number, code: string, field: 'code' | 'errorCode' = 'code') {
  return HttpResponse.json(
    { type: 'about:blank', title: code, status, [field]: code },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

afterEach(() => {
  useAuthStore.getState().clear()
})

describe('refreshSession', () => {
  it('stores the new session on success', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json(session)))

    await expect(refreshSession(BASE)).resolves.toBe('refreshed')
    expect(useAuthStore.getState().accessToken).toBe('fresh-token')
    expect(useSessionStore.getState().role).toBe('GUEST')
  })

  it('shares one request between callers that arrive together', async () => {
    let calls = 0
    server.use(
      http.post('*/api/v1/auth/refresh', () => {
        calls++
        return HttpResponse.json(session)
      }),
    )

    await Promise.all([refreshSession(BASE), refreshSession(BASE), refreshSession(BASE)])
    expect(calls).toBe(1)
  })

  it('ends the session only on 401 UNAUTHORIZED', async () => {
    useAuthStore.getState().setSession(session)
    server.use(http.post('*/api/v1/auth/refresh', () => problem(401, 'UNAUTHORIZED')))

    await expect(refreshSession(BASE)).resolves.toBe('expired')
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useSessionStore.getState().role).toBeNull()
  })

  it('reads Spring ProblemDetail errorCode while retaining the contract code field', async () => {
    useAuthStore.getState().setSession(session)
    server.use(http.post('*/api/v1/auth/refresh', () => problem(401, 'UNAUTHORIZED', 'errorCode')))

    await expect(refreshSession(BASE)).resolves.toBe('expired')
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useSessionStore.getState().role).toBeNull()
  })

  it('ends the session when the account is locked (403 ACCOUNT_LOCKED)', async () => {
    useAuthStore.getState().setSession(session)
    server.use(http.post('*/api/v1/auth/refresh', () => problem(403, 'ACCOUNT_LOCKED')))

    await expect(refreshSession(BASE)).resolves.toBe('expired')
    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('keeps the session and retries once after REFRESH_RACE', async () => {
    useAuthStore.getState().setSession({ ...session, accessToken: 'old-token' })
    let calls = 0
    server.use(
      http.post('*/api/v1/auth/refresh', () => {
        calls++
        return calls === 1 ? problem(401, 'REFRESH_RACE') : HttpResponse.json(session)
      }),
    )

    await expect(refreshSession(BASE, { raceRetryDelayMs: 0 })).resolves.toBe('refreshed')
    expect(calls).toBe(2)
    expect(useAuthStore.getState().accessToken).toBe('fresh-token')
  })

  it('recognizes REFRESH_RACE from the backend errorCode field', async () => {
    let calls = 0
    server.use(
      http.post('*/api/v1/auth/refresh', () => {
        calls++
        return calls === 1 ? problem(401, 'REFRESH_RACE', 'errorCode') : HttpResponse.json(session)
      }),
    )

    await expect(refreshSession(BASE, { raceRetryDelayMs: 0 })).resolves.toBe('refreshed')
    expect(calls).toBe(2)
    expect(useAuthStore.getState().accessToken).toBe('fresh-token')
  })

  it('does not sign out when REFRESH_RACE repeats, and stops after the single retry', async () => {
    useAuthStore.getState().setSession({ ...session, accessToken: 'old-token' })
    let calls = 0
    server.use(
      http.post('*/api/v1/auth/refresh', () => {
        calls++
        return problem(401, 'REFRESH_RACE')
      }),
    )

    await expect(refreshSession(BASE, { raceRetryDelayMs: 0 })).resolves.toBe('unavailable')
    expect(calls).toBe(2)
    expect(useAuthStore.getState().accessToken).toBe('old-token')
    expect(useSessionStore.getState().role).toBe('GUEST')
  })

  it('leaves the session alone on a server error or a network failure', async () => {
    useAuthStore.getState().setSession({ ...session, accessToken: 'old-token' })
    server.use(http.post('*/api/v1/auth/refresh', () => problem(503, 'UNAVAILABLE')))
    await expect(refreshSession(BASE)).resolves.toBe('unavailable')

    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.error()))
    await expect(refreshSession(BASE)).resolves.toBe('unavailable')

    expect(useAuthStore.getState().accessToken).toBe('old-token')
  })
})
