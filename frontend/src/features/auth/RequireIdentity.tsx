import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useSessionStore } from '@/shared/session/store'
import { SessionRestoreFailed, SessionRestoring } from './SessionRestoreStates'
import { useSessionRestore } from './useSessionRestore'

/**
 * Layout guard: signed-in users and guests pass, everyone else goes to /start.
 * After a reload the first visit tries the refresh cookie before deciding.
 */
export function RequireIdentity() {
  const location = useLocation()
  const role = useSessionStore((s) => s.role)
  const { state, retry } = useSessionRestore()

  if (role !== null) return <Outlet />
  if (state === 'checking') return <SessionRestoring />
  if (state === 'failed') return <SessionRestoreFailed onRetry={retry} />
  return <Navigate to="/start" replace state={{ from: location.pathname + location.search }} />
}
