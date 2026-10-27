import { useTranslation } from 'react-i18next'
import { Navigate, useLocation } from 'react-router-dom'
import { useSessionStore } from '@/shared/session/store'
import { SessionRestoreFailed, SessionRestoring } from '../SessionRestoreStates'
import { useSessionRestore } from '../useSessionRestore'
import { GuestForm } from './GuestForm'

/** Only in-app paths: `from` comes from router state, but never trust it to leave the site. */
function safeFrom(state: unknown): string {
  const from =
    typeof state === 'object' && state !== null && 'from' in state ? state.from : undefined
  return typeof from === 'string' &&
    from.startsWith('/') &&
    !from.startsWith('//') &&
    from !== '/start' &&
    !from.startsWith('/start?') &&
    !from.startsWith('/start#')
    ? from
    : '/'
}

export function GuestStartPage() {
  const { t } = useTranslation('auth')
  const role = useSessionStore((s) => s.role)
  const location = useLocation()
  const { state, retry } = useSessionRestore()

  // Signed in (or just became a guest through the form): nothing to ask here.
  if (role !== null) return <Navigate to={safeFrom(location.state)} replace />

  // Ask before offering the form: someone whose cookie is still valid must not become a new guest.
  if (state === 'checking') return <SessionRestoring />
  if (state === 'failed') return <SessionRestoreFailed onRetry={retry} />

  return (
    <section className="mx-auto grid w-full max-w-6xl gap-10 px-4 py-12 md:grid-cols-2 md:items-center md:gap-16 md:py-24">
      <div>
        <h1 className="font-display text-4xl leading-tight font-extrabold tracking-tight text-balance sm:text-5xl md:text-6xl">
          {t('guest.heading')}
        </h1>
        <p className="mt-5 max-w-md text-lg text-foreground">{t('guest.lead')}</p>
      </div>
      <GuestForm />
    </section>
  )
}
