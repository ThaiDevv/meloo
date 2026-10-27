import { create } from 'zustand'
import { useSessionStore, type Role } from '@/shared/session/store'
import type { components } from './schema.gen'

export type AuthSession = components['schemas']['AuthSession']
export type CurrentUser = components['schemas']['Me']

interface AuthState {
  /**
   * Memory only, never persisted (AC3): a page reload drops it and `refreshSession()` brings it
   * back from the HttpOnly refresh cookie.
   */
  accessToken: string | null
  user: CurrentUser | null
  setSession: (session: AuthSession) => void
  clear: () => void
}

function roleOf(user: CurrentUser): Role {
  if (user.role === 'ADMIN') return 'ADMIN'
  return user.kind === 'GUEST' ? 'GUEST' : 'USER'
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  setSession: (session) => {
    set({ accessToken: session.accessToken, user: session.user })
    useSessionStore.getState().setRole(roleOf(session.user))
  },
  clear: () => {
    set({ accessToken: null, user: null })
    useSessionStore.getState().setRole(null)
  },
}))

export function getAccessToken(): string | null {
  return useAuthStore.getState().accessToken
}
