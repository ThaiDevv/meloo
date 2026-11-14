import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Button } from '@/shared/ui/Button'
import { Skeleton } from '@/shared/ui/Skeleton'
import { useOAuthCallback } from './useOAuthCallback'

function LoadingState({ message }: { message: string }) {
  return (
    <div
      role="status"
      aria-label={message}
      className="mx-auto flex max-w-sm flex-col items-center gap-6 px-4 py-20 text-center"
    >
      <Skeleton className="h-8 w-48" />
      <Skeleton className="h-4 w-64" />
      <span className="sr-only">{message}</span>
    </div>
  )
}

export function OAuthCallbackPage() {
  const { t } = useTranslation('auth')
  const { callbackState } = useOAuthCallback()

  if (callbackState.status === 'loading') {
    return <LoadingState message={t('callback.loading')} />
  }

  if (callbackState.status === 'setup') return null

  if (callbackState.status === 'linking') {
    return <LoadingState message={t('callback.linking')} />
  }

  // Error state
  const errorCode = callbackState.code
  const errorMessage =
    errorCode === 'OAUTH_STATE_INVALID' ||
    errorCode === 'OAUTH_EMAIL_UNVERIFIED' ||
    errorCode === 'ACCOUNT_LOCKED' ||
    errorCode === 'OAUTH_IDENTITY_ALREADY_LINKED'
      ? t(`callback.error.${errorCode}`)
      : t('callback.error.unknown')

  return (
    <section
      aria-labelledby="callback-error-title"
      className="mx-auto flex max-w-sm flex-col items-center gap-6 px-4 py-20 text-center"
    >
      <h1 id="callback-error-title" className="text-2xl font-bold">
        {t('callback.errorTitle')}
      </h1>
      <p className="text-muted-foreground">{errorMessage}</p>
      <div className="flex flex-wrap justify-center gap-3">
        <Button
          onClick={() => {
            window.location.href = '/start'
          }}
        >
          {t('callback.retryButton')}
        </Button>
        <Button variant="ghost" asChild>
          <Link to="/">{t('callback.homeButton')}</Link>
        </Button>
      </div>
    </section>
  )
}
