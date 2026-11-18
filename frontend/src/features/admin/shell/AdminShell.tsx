import { NavLink, Navigate, Outlet, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { SessionRestoreFailed, SessionRestoring } from '@/features/auth/SessionRestoreStates'
import { useSessionRestore } from '@/features/auth/useSessionRestore'
import { cn } from '@/shared/lib/utils'
import { useSessionStore } from '@/shared/session/store'
import { ADMIN_MENU } from './menu'

/**
 * Guard and layout for /admin/**. Only role ADMIN gets in. After a reload the session is restored
 * from the refresh cookie first, so an admin is not bounced before the server answers.
 * The server still checks every admin call (403 otherwise); this only hides the screens.
 */
export function AdminShell() {
  const location = useLocation()
  const role = useSessionStore((s) => s.role)
  const { state, retry } = useSessionRestore()

  if (role === 'ADMIN') return <AdminLayout />
  if (role !== null) return <Navigate to="/" replace />
  if (state === 'checking') return <SessionRestoring />
  if (state === 'failed') return <SessionRestoreFailed onRetry={retry} />
  return <Navigate to="/start" replace state={{ from: location.pathname + location.search }} />
}

function AdminLayout() {
  const { t } = useTranslation('admin')
  return (
    <div className="mx-auto w-full max-w-6xl space-y-6 px-4 py-8">
      <header className="space-y-4">
        <h1 className="text-2xl font-bold">{t('title')}</h1>
        <nav aria-label={t('shell.menu')} className="-mx-4 overflow-x-auto px-4">
          <ul className="flex min-w-max gap-1 border-b border-border">
            {ADMIN_MENU.map((item) => (
              <li key={item.key}>
                <NavLink
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    cn(
                      '-mb-px inline-flex h-11 items-center border-b-2 px-3 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-ring',
                      isActive
                        ? 'border-primary text-foreground'
                        : 'border-transparent text-muted-foreground hover:text-foreground',
                    )
                  }
                >
                  {t(`shell.nav.${item.key}`)}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </header>
      <div>
        <Outlet />
      </div>
    </div>
  )
}
