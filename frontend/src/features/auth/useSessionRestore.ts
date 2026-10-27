import { useEffect, useState } from 'react'
import { refreshSession } from '@/shared/api/refresh'
import { useSessionStore } from '@/shared/session/store'

/**
 * - `checking`: asking the server whether the refresh cookie still holds a session.
 * - `done`: answered. Look at the session role: set = restored, null = no session.
 * - `failed`: could not tell (offline, 5xx, repeated REFRESH_RACE). That is not "no session":
 *   treating it as one would let a new guest replace one whose cookie is still valid.
 */
export type RestoreState = 'checking' | 'done' | 'failed'

/** The access token lives in memory, so after a reload it has to be won back from the cookie. */
export function useSessionRestore(): { state: RestoreState; retry: () => void } {
  const [state, setState] = useState<RestoreState>(() =>
    useSessionStore.getState().role === null ? 'checking' : 'done',
  )

  useEffect(() => {
    if (state !== 'checking') return
    let cancelled = false
    void refreshSession().then((outcome) => {
      if (!cancelled) setState(outcome === 'unavailable' ? 'failed' : 'done')
    })
    return () => {
      cancelled = true
    }
  }, [state])

  return {
    state,
    retry: () => {
      setState('checking')
    },
  }
}
