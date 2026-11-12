import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Skeleton } from '@/shared/ui/Skeleton'

export function DailySkeleton() {
  const { t } = useTranslation('daily')
  return (
    <div aria-busy="true" className="space-y-5">
      <p role="status" className="sr-only">
        {t('loading')}
      </p>
      <Skeleton className="h-[clamp(2.5rem,7vw,4.5rem)] w-3/4" />
      <Skeleton className="h-6 w-1/2" />
      <Skeleton className="mt-8 h-32 max-w-xl rounded-2xl" />
      <Skeleton className="mt-8 h-12 w-44 rounded-full" />
    </div>
  )
}

export function DailyLoadError({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation('daily')
  return (
    <div className="mx-auto max-w-xl">
      <h1 className="sr-only">{t('title')}</h1>
      <ErrorState
        title={t('errors.loadTitle')}
        description={t('errors.loadBody')}
        onRetry={onRetry}
      />
    </div>
  )
}

export function DailyNotAvailable() {
  const { t } = useTranslation('daily')
  return (
    <div className="mx-auto max-w-xl">
      <h1 className="sr-only">{t('title')}</h1>
      <EmptyState
        title={t('notAvailable.title')}
        description={t('notAvailable.body')}
        action={
          <Button asChild>
            <a href="/">{t('notAvailable.action')}</a>
          </Button>
        }
      />
    </div>
  )
}
