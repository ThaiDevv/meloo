import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Button } from '@/shared/ui/Button'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Skeleton } from '@/shared/ui/Skeleton'

/** The intro's layout with bars at the real size of what will land, so nothing jumps. */
export function MatchSkeleton() {
  const { t } = useTranslation('solo')
  return (
    <div
      aria-busy="true"
      className="grid gap-12 lg:grid-cols-[minmax(0,1fr)_minmax(0,26rem)] lg:gap-16"
    >
      <p role="status" className="sr-only">
        {t('play.loading')}
      </p>
      <div className="space-y-5">
        <Skeleton className="h-[clamp(2.5rem,7vw,4.5rem)] w-3/4" />
        <Skeleton className="h-6 w-1/2" />
        <Skeleton className="mt-8 h-32 max-w-xl rounded-2xl" />
        <Skeleton className="mt-8 h-12 w-44 rounded-full" />
      </div>
      <Skeleton className="h-80 rounded-3xl" />
    </div>
  )
}

export function MatchLoadError({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation('solo')
  return (
    <div className="mx-auto max-w-xl">
      <h1 className="sr-only">{t('title')}</h1>
      <ErrorState
        title={t('play.errors.loadTitle')}
        description={t('play.errors.loadBody')}
        onRetry={onRetry}
      />
    </div>
  )
}

export function MatchMissing() {
  const { t } = useTranslation('solo')
  return (
    <div className="mx-auto max-w-xl">
      <h1 className="sr-only">{t('title')}</h1>
      <EmptyState
        title={t('play.missing.title')}
        description={t('play.missing.body')}
        action={
          <Button asChild>
            <Link to="/solo">{t('play.missing.action')}</Link>
          </Button>
        }
      />
    </div>
  )
}

export function OtherMatchInProgress({ activeMatchId }: { activeMatchId: string }) {
  const { t } = useTranslation('solo')
  return (
    <div className="mx-auto max-w-xl">
      <h1 className="sr-only">{t('title')}</h1>
      <EmptyState
        title={t('play.other.title')}
        description={t('play.other.body')}
        action={
          <Button asChild>
            <Link to={`/solo/${activeMatchId}`}>{t('play.other.action')}</Link>
          </Button>
        }
      />
    </div>
  )
}
