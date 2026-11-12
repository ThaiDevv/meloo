import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useAuthStore } from '@/shared/api/authStore'

/** Before the session is known (a reload that has not restored it yet) every call uses this scope. */
const ANONYMOUS = 'anonymous'

/**
 * The identity daily data belongs to. Every daily query is keyed by it, so a new session (log out,
 * then a new guest in the same tab) never reads the previous player's today, result or history.
 * Data cached under any other identity is dropped as soon as the identity changes.
 */
export function useDailyScope(): string {
  const scope = useAuthStore((state) => state.user?.id ?? ANONYMOUS)
  const queryClient = useQueryClient()
  useEffect(() => {
    queryClient.removeQueries({ queryKey: ['daily'], predicate: (q) => q.queryKey[1] !== scope })
  }, [queryClient, scope])
  return scope
}
