import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'

interface SocialLoginButtonsProps {
  /**
   * Optional path to send the user back to after a successful login.
   * Passed as `returnTo` query param to the OAuth start URL.
   */
  returnTo?: string
}

/** Initiates an OAuth2 login flow by navigating to the backend start URL. */
function startOAuth(provider: 'google' | 'facebook', returnTo?: string) {
  const params = new URLSearchParams({ mode: 'login' })
  if (returnTo) params.set('returnTo', returnTo)
  window.location.href = `/api/v1/auth/oauth2/${provider}?${params.toString()}`
}

/**
 * Google login button (always visible) and Facebook login button (gated by
 * `VITE_FEATURE_FACEBOOK`). Both are disabled while a navigation is in progress.
 */
export function SocialLoginButtons({ returnTo }: SocialLoginButtonsProps) {
  const { t } = useTranslation('auth')
  const [pending, setPending] = useState<'google' | 'facebook' | null>(null)
  const facebookEnabled = import.meta.env.VITE_FEATURE_FACEBOOK === 'true'

  function handleGoogle() {
    if (pending) return
    setPending('google')
    startOAuth('google', returnTo)
  }

  function handleFacebook() {
    if (pending) return
    setPending('facebook')
    startOAuth('facebook', returnTo)
  }

  return (
    <div className="flex flex-col gap-3">
      <Button
        type="button"
        variant="outline"
        className="w-full"
        disabled={pending !== null}
        onClick={handleGoogle}
        aria-label={t('social.loginWithGoogle')}
        data-testid="btn-login-google"
      >
        {/* Google "G" logo inline SVG — no extra library needed */}
        <svg
          aria-hidden="true"
          viewBox="0 0 24 24"
          className="size-5 shrink-0"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
            fill="#4285F4"
          />
          <path
            d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
            fill="#34A853"
          />
          <path
            d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z"
            fill="#FBBC05"
          />
          <path
            d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84C6.71 7.31 9.14 5.38 12 5.38z"
            fill="#EA4335"
          />
        </svg>
        {pending === 'google' ? t('social.signingIn') : t('social.loginWithGoogle')}
      </Button>

      {facebookEnabled && (
        <Button
          type="button"
          variant="outline"
          className="w-full"
          disabled={pending !== null}
          onClick={handleFacebook}
          aria-label={t('social.loginWithFacebook')}
          data-testid="btn-login-facebook"
        >
          {/* Facebook "f" logo inline SVG */}
          <svg
            aria-hidden="true"
            viewBox="0 0 24 24"
            className="size-5 shrink-0"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              d="M24 12.073C24 5.405 18.627 0 12 0S0 5.405 0 12.073C0 18.1 4.388 23.094 10.125 24v-8.437H7.078v-3.49h3.047v-2.66c0-3.025 1.792-4.697 4.533-4.697 1.312 0 2.686.236 2.686.236v2.97h-1.513c-1.491 0-1.956.93-1.956 1.886v2.265h3.328l-.532 3.49h-2.796V24C19.612 23.094 24 18.1 24 12.073z"
              fill="#1877F2"
            />
          </svg>
          {pending === 'facebook' ? t('social.signingIn') : t('social.loginWithFacebook')}
        </Button>
      )}
    </div>
  )
}
