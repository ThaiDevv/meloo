import { useEffect } from 'react'
import { create } from 'zustand'
import { refreshSession } from '@/shared/api/refresh'
import { useSessionStore } from '@/shared/session/store'

/**
 * - `checking`: asking the server whether the refresh cookie still holds a session.
 * - `done`: answered. Look at the session role: set = restored, null = no session.
 * - `failed`: could not tell (offline, 5xx, repeated REFRESH_RACE). That is not "no session":
 *   treating it as one would let a new guest replace one whose cookie is still valid.
 */
export type RestoreState = 'checking' | 'done' | 'failed'

interface RestoreStore {
  state: RestoreState
  setState: (state: RestoreState) => void
}

const useRestoreStore = create<RestoreStore>((set) => ({
  state: 'checking',
  setState: (state) => {
    set({ state })
  },
}))

let restoreRequest: Promise<void> | null = null

function restoreOnce() {
  if (useRestoreStore.getState().state !== 'checking' || restoreRequest) return

  restoreRequest = refreshSession()
    .then((outcome) => {
      useRestoreStore.getState().setState(outcome === 'unavailable' ? 'failed' : 'done')
    })
    .finally(() => {
      restoreRequest = null
    })
}

/** The access token lives in memory, so after a reload it has to be won back from the cookie. */
export function useSessionRestore(): { state: RestoreState; retry: () => void } {
  const state = useRestoreStore((store) => store.state)
  const role = useSessionStore((store) => store.role)

  useEffect(() => {
    if (role === null) restoreOnce()
  }, [role])

  return {
    state,
    retry: () => {
      useRestoreStore.getState().setState('checking')
      restoreOnce()
    },
  }
}

/** Reset the module-level result between isolated component tests. */
export function resetSessionRestoreForTests() {
  restoreRequest = null
  useRestoreStore.getState().setState('checking')
}
