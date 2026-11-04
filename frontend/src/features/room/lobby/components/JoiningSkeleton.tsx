import { useTranslation } from 'react-i18next'
import { Skeleton } from '@/shared/ui/Skeleton'

/** The lobby's frame while the join request is in flight, at the size of the real content. */
export function JoiningSkeleton() {
  const { t } = useTranslation('room')
  return (
    <div
      aria-busy="true"
      aria-label={t('entry.joining')}
      role="status"
      className="mx-auto w-full max-w-6xl px-4 py-8 lg:py-12"
    >
      <Skeleton className="h-9 w-48" />
      <div className="mt-8 grid gap-8 lg:grid-cols-[minmax(0,1fr)_24rem] lg:gap-12">
        <div className="space-y-3">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </div>
        <Skeleton className="h-72 w-full rounded-2xl" />
      </div>
    </div>
  )
}
