import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Skeleton } from '@/shared/ui/Skeleton'
import { LeaderboardRow, type RowData } from './LeaderboardRow'

interface LeaderboardBoardProps {
  /** Accessible name of the list. */
  label: string
  rows: RowData[] | undefined
  myRow: RowData | null | undefined
  page: number
  totalPages: number
  isPending: boolean
  isError: boolean
  isFetching: boolean
  emptyTitle: string
  emptyDescription: string
  onRetry: () => void
  onPageChange: (page: number) => void
}

/** One board: the ranked page, the caller's own place when it is off this page, and paging. */
export function LeaderboardBoard({
  label,
  rows,
  myRow,
  page,
  totalPages,
  isPending,
  isError,
  isFetching,
  emptyTitle,
  emptyDescription,
  onRetry,
  onPageChange,
}: LeaderboardBoardProps) {
  const { t } = useTranslation('leaderboard')

  if (isPending) {
    return (
      <div aria-busy="true" aria-label={t('loading')} className="space-y-2">
        {Array.from({ length: 5 }, (_, i) => (
          <Skeleton key={i} className="h-14 w-full" />
        ))}
      </div>
    )
  }
  if (isError || !rows) return <ErrorState onRetry={onRetry} />
  if (rows.length === 0 && page === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />
  }

  const meOnPage = myRow ? rows.some((r) => r.userId === myRow.userId) : false

  return (
    <div className="space-y-4" aria-busy={isFetching ? 'true' : undefined}>
      {myRow && !meOnPage ? (
        <section aria-labelledby="leaderboard-my-position" className="space-y-2">
          <h3 id="leaderboard-my-position" className="text-sm font-semibold text-muted-foreground">
            {t('myPosition')}
          </h3>
          <LeaderboardRow row={myRow} isMe as="div" />
        </section>
      ) : null}

      <ol aria-label={label} className="space-y-1">
        {rows.map((row) => (
          <LeaderboardRow key={row.userId} row={row} isMe={row.userId === myRow?.userId} />
        ))}
      </ol>

      {totalPages > 1 ? (
        <nav aria-label={t('pagination.label')} className="flex items-center justify-between gap-3">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0 || isFetching}
            onClick={() => {
              onPageChange(page - 1)
            }}
          >
            {t('pagination.previous')}
          </Button>
          <span className="text-sm text-muted-foreground tabular-nums" aria-live="polite">
            {t('pagination.status', { page: page + 1, total: totalPages })}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages - 1 || isFetching}
            onClick={() => {
              onPageChange(page + 1)
            }}
          >
            {t('pagination.next')}
          </Button>
        </nav>
      ) : null}
    </div>
  )
}
