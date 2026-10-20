import { create } from 'zustand'

export type Role = 'GUEST' | 'USER' | 'ADMIN'

interface SessionState {
  /** null = not signed in and not a guest. Filled by FE-AUTH-01 / FE-AUTH-02. */
  role: Role | null
  setRole: (role: Role | null) => void
}

export const useSessionStore = create<SessionState>((set) => ({
  role: null,
  setRole: (role) => {
    set({ role })
  },
}))
