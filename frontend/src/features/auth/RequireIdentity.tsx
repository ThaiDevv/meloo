import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { refreshSession } from '@/shared/api/refresh'
import { useSessionStore } from '@/shared/session/store'
import { Skeleton } from '@/shared/ui/Skeleton'

/**
 * Layout guard: signed-in users and guests pass, everyone else goes to /start.
 * The access token lives in memory, so after a reload the first visit tries the refresh cookie
 * before deciding.
 */
export function RequireIdentity() {
  const { t } = useTranslation('auth')
  const location = useLocation()
  const role = useSessionStore((s) => s.role)
  const [restoring, setRestoring] = useState(role === null)

  useEffect(() => {
    if (!restoring) return
    let cancelled = false
    void refreshSession().then(() => {
      if (!cancelled) setRestoring(false)
    })
    return () => {
      cancelled = true
    }
  }, [restoring])

  if (role !== null) return <Outlet />
  if (restoring) {
    return (
      <div role="status" aria-busy="true" className="mx-auto w-full max-w-6xl space-y-4 px-4 py-12">
        <span className="sr-only">{t('session.restoring')}</span>
        <Skeleton className="h-10 w-72" />
        <Skeleton className="h-5 w-96 max-w-full" />
      </div>
    )
  }
  return <Navigate to="/start" replace state={{ from: location.pathname + location.search }} />
}
