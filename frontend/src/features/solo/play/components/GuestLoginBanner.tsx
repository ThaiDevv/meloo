import { useTranslation } from 'react-i18next'
import { useLocation } from 'react-router-dom'
import { useSessionStore } from '@/shared/session/store'
import { SocialLoginButtons } from '@/features/auth/social/SocialLoginButtons'

/**
 * Post-match banner encouraging a guest to sign in and save their history.
 * Only renders when the current session role is 'GUEST'.
 * The `returnTo` is set to the current path so the user comes back after login.
 */
export function GuestLoginBanner() {
  const { t } = useTranslation('auth')
  const role = useSessionStore((s) => s.role)
  const location = useLocation()

  if (role !== 'GUEST') return null

  return (
    <aside
      aria-label={t('guestBanner.heading')}
      className="rounded-2xl border border-border bg-raised p-6"
      data-testid="guest-login-banner"
    >
      <h2 className="text-lg font-bold">{t('guestBanner.heading')}</h2>
      <p className="mt-1 text-sm text-muted-foreground">{t('guestBanner.body')}</p>
      <div className="mt-4">
        <SocialLoginButtons returnTo={location.pathname} />
      </div>
    </aside>
  )
}
