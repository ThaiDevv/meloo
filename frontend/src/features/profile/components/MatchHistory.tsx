import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { cn } from '@/shared/lib/utils'
import { Badge } from '@/shared/ui/Badge'
import { Button } from '@/shared/ui/Button'
import { Skeleton } from '@/shared/ui/Skeleton'
import type { MatchHistoryItem, MatchHistoryPage } from '../api/profileApi'

interface MatchHistoryProps {
  history: MatchHistoryPage
  /** True while the next page loads behind the current one. */
  fetching: boolean
  onPage: (page: number) => void
}

/** One page of past matches, newest first, each opening its round-by-round detail. */
export function MatchHistory({ history, fetching, onPage }: MatchHistoryProps) {
  const { t } = useTranslation('profile')
  const { page, totalPages } = history
  return (
    <div aria-busy={fetching}>
      <ul className={cn('space-y-2 transition-opacity', fetching && 'opacity-60')}>
        {history.items.map((match) => (
          <MatchRow key={match.matchId} match={match} />
        ))}
      </ul>
      {totalPages > 1 ? (
        <nav
          aria-label={t('history.pagination')}
          className="mt-4 flex items-center justify-between gap-3"
        >
          <Button
            variant="outline"
            size="sm"
            disabled={page <= 0 || fetching}
            onClick={() => {
              onPage(page - 1)
            }}
          >
            <ChevronLeft aria-hidden="true" className="size-4" />
            {t('history.previous')}
          </Button>
          <p className="num text-sm text-muted-foreground" aria-live="polite">
            {t('history.pageOf', { page: page + 1, total: totalPages })}
          </p>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages - 1 || fetching}
            onClick={() => {
              onPage(page + 1)
            }}
          >
            {t('history.next')}
            <ChevronRight aria-hidden="true" className="size-4" />
          </Button>
        </nav>
      ) : null}
    </div>
  )
}

function MatchRow({ match }: { match: MatchHistoryItem }) {
  const { t, i18n } = useTranslation('profile')
  const number = new Intl.NumberFormat(i18n.language)
  const date = new Intl.DateTimeFormat(i18n.language, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(match.endedAt))
  const solo = match.type === 'SOLO'
  return (
    <li>
      <Link
        to={`/profile/matches/${match.matchId}`}
        className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-4 rounded-2xl border border-border bg-surface p-4 transition-colors hover:bg-raised"
      >
        <div className="min-w-0 space-y-1">
          <p className="flex flex-wrap items-center gap-2 font-semibold">
            {t(`matchType.${match.type}`)}
            {match.status === 'ABANDONED' ? (
              <Badge tone="warning">{t('history.abandoned')}</Badge>
            ) : null}
            {match.ranked ? <Badge>{t('history.ranked')}</Badge> : null}
          </p>
          <p className="text-sm text-muted-foreground">
            <time dateTime={match.endedAt}>{date}</time>
          </p>
          <p className="text-sm">
            {t('history.correct', { correct: match.correctCount, total: match.roundCount })}
            {solo
              ? null
              : ` · ${t('history.placement', { rank: match.rank, players: match.playerCount })}`}
          </p>
        </div>
        <div className="flex items-center gap-2 text-right">
          <div>
            <p className="num font-display text-xl leading-none font-extrabold tracking-tight">
              {number.format(match.totalScore)}
              <span className="sr-only"> {t('unit')}</span>
            </p>
            {match.rankDelta !== null && match.rankDelta !== undefined ? (
              <RankDelta delta={match.rankDelta} />
            ) : null}
          </div>
          <ChevronRight aria-hidden="true" className="size-4 text-muted-foreground" />
        </div>
      </Link>
    </li>
  )
}

export function RankDelta({ delta }: { delta: number }) {
  const { t } = useTranslation('profile')
  return (
    <p
      className={cn(
        'num mt-1 text-sm font-semibold',
        delta > 0 ? 'text-positive' : delta < 0 ? 'text-negative' : 'text-muted-foreground',
      )}
    >
      {t('rankDelta', { delta: delta > 0 ? `+${String(delta)}` : String(delta) })}
    </p>
  )
}

export function MatchHistorySkeleton() {
  return (
    <ul className="space-y-2">
      {[0, 1, 2].map((i) => (
        <li
          key={i}
          className="flex items-center justify-between gap-4 rounded-2xl border border-border bg-surface p-4"
        >
          <div className="space-y-2">
            <Skeleton className="h-5 w-32" />
            <Skeleton className="h-4 w-40" />
            <Skeleton className="h-4 w-24" />
          </div>
          <Skeleton className="h-6 w-16" />
        </li>
      ))}
    </ul>
  )
}
