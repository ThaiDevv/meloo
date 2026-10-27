import { useTranslation } from 'react-i18next'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Skeleton } from '@/shared/ui/Skeleton'

const FRAME = 'mx-auto w-full max-w-6xl px-4 py-12'

export function SessionRestoring() {
  const { t } = useTranslation('auth')
  return (
    <div role="status" aria-busy="true" className={`${FRAME} space-y-4`}>
      <span className="sr-only">{t('session.restoring')}</span>
      <Skeleton className="h-10 w-72" />
      <Skeleton className="h-5 w-96 max-w-full" />
    </div>
  )
}

export function SessionRestoreFailed({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation('auth')
  return (
    <div className={FRAME}>
      <ErrorState
        title={t('session.unavailable.title')}
        description={t('session.unavailable.description')}
        onRetry={onRetry}
      />
    </div>
  )
}
