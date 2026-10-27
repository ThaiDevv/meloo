import { afterEach, describe, expect, it } from 'vitest'
import { useSessionStore } from '@/shared/session/store'
import { useAuthStore, type AuthSession } from './authStore'

const guestSession: AuthSession = {
  accessToken: 'secret-access-token',
  tokenType: 'Bearer',
  expiresAt: '2026-10-23T13:25:00Z',
  user: {
    id: '7c1d2a64-3b5e-4f08-9a1b-2e6f0c4d8a11',
    kind: 'GUEST',
    role: 'USER',
    nickname: 'Meloman',
    createdAt: '2026-10-23T13:10:00Z',
  },
}

afterEach(() => {
  useAuthStore.getState().clear()
})

describe('useAuthStore', () => {
  it('keeps the access token in memory only', () => {
    useAuthStore.getState().setSession(guestSession)

    expect(useAuthStore.getState().accessToken).toBe('secret-access-token')
    for (const storage of [localStorage, sessionStorage]) {
      const dump = JSON.stringify(Object.entries(storage))
      expect(dump).not.toContain('secret-access-token')
    }
    expect(document.cookie).not.toContain('secret-access-token')
  })

  it.each([
    ['a guest', { kind: 'GUEST', role: 'USER' }, 'GUEST'],
    ['a registered user', { kind: 'REGISTERED', role: 'USER' }, 'USER'],
    ['an admin', { kind: 'REGISTERED', role: 'ADMIN' }, 'ADMIN'],
  ] as const)('maps %s to the session role', (_label, who, role) => {
    useAuthStore.getState().setSession({ ...guestSession, user: { ...guestSession.user, ...who } })
    expect(useSessionStore.getState().role).toBe(role)
  })

  it('forgets the token and the role on clear', () => {
    useAuthStore.getState().setSession(guestSession)
    useAuthStore.getState().clear()

    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useAuthStore.getState().user).toBeNull()
    expect(useSessionStore.getState().role).toBeNull()
  })
})
