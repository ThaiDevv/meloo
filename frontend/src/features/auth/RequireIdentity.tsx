import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { refreshSession } from '@/shared/api/refresh'
import { useSessionStore } from '@/shared/session/store'
import { ErrorState } from '@/shared/ui/ErrorState'
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
  // `failed` = could not tell (offline, 5xx, repeated REFRESH_RACE). That is not "no session":
  // sending the player to /start would let them replace a guest whose cookie is still valid.
  const [restore, setRestore] = useState<'checking' | 'done' | 'failed'>(
    role === null ? 'checking' : 'done',
  )

  useEffect(() => {
    if (restore !== 'checking') return
    let cancelled = false
    void refreshSession().then((outcome) => {
      if (!cancelled) setRestore(outcome === 'unavailable' ? 'failed' : 'done')
    })
    return () => {
      cancelled = true
    }
  }, [restore])

  if (role !== null) return <Outlet />
  if (restore === 'checking') {
    return (
      <div role="status" aria-busy="true" className="mx-auto w-full max-w-6xl space-y-4 px-4 py-12">
        <span className="sr-only">{t('session.restoring')}</span>
        <Skeleton className="h-10 w-72" />
        <Skeleton className="h-5 w-96 max-w-full" />
      </div>
    )
  }
  if (restore === 'failed') {
    return (
      <div className="mx-auto w-full max-w-6xl px-4 py-12">
        <ErrorState
          title={t('session.unavailable.title')}
          description={t('session.unavailable.description')}
          onRetry={() => {
            setRestore('checking')
          }}
        />
      </div>
    )
  }
  return <Navigate to="/start" replace state={{ from: location.pathname + location.search }} />
}
