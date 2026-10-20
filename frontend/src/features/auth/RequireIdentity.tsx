import { Navigate, Outlet } from 'react-router-dom'
import { useSessionStore } from '@/shared/session/store'

/** Layout guard: signed-in users and guests pass, everyone else goes to /start. */
export function RequireIdentity() {
  const role = useSessionStore((s) => s.role)
  return role === null ? <Navigate to="/start" replace /> : <Outlet />
}
