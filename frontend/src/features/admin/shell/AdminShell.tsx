import { Navigate, Outlet } from 'react-router-dom'
import { useSessionStore } from '@/shared/session/store'

/** Placeholder shell for /admin/**: only role ADMIN gets in. FE-ADM-01 adds layout and menu. */
export function AdminShell() {
  const role = useSessionStore((s) => s.role)
  return role === 'ADMIN' ? <Outlet /> : <Navigate to="/" replace />
}
