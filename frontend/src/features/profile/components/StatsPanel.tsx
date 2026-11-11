import { useTranslation } from 'react-i18next'
import { Skeleton } from '@/shared/ui/Skeleton'
import type { PlayerStats } from '../api/profileApi'

/** The four headline figures of the ticket: matches, win rate, average level, favourite genre. */
export function StatsPanel({ stats }: { stats: PlayerStats }) {
  const { t, i18n } = useTranslation('profile')
  const number = new Intl.NumberFormat(i18n.language)
  const percent = new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 })
  const decimal = new Intl.NumberFormat(i18n.language, {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  })
  const english = i18n.language.startsWith('en')

  const level =
    stats.averageCorrectLevel === null || stats.averageCorrectLevel === undefined
      ? t('stats.none')
      : t('stats.levelValue', { level: decimal.format(stats.averageCorrectLevel) })
  const listen =
    stats.averageListenSeconds === null || stats.averageListenSeconds === undefined
      ? null
      : t('stats.listenValue', { seconds: decimal.format(stats.averageListenSeconds) })
  const genre = stats.favoriteGenre
    ? english
      ? stats.favoriteGenre.nameEn
      : stats.favoriteGenre.nameVi
    : t('stats.none')

  return (
    <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <Stat
        term={t('stats.matchesPlayed')}
        value={number.format(stats.matchesPlayed)}
        note={t('stats.matchesWon', { count: stats.matchesWon })}
      />
      <Stat
        term={t('stats.winRate')}
        value={`${percent.format(stats.winRate)}%`}
        note={t('stats.rounds', {
          correct: number.format(stats.roundsCorrect),
          total: number.format(stats.roundsPlayed),
        })}
      />
      <Stat term={t('stats.averageLevel')} value={level} note={listen ?? t('stats.noCorrect')} />
      <Stat
        term={t('stats.favoriteGenre')}
        value={genre}
        note={t('stats.bestCombo', { count: stats.bestCombo })}
      />
    </dl>
  )
}

function Stat({ term, value, note }: { term: string; value: string; note: string }) {
  return (
    <div className="flex flex-col gap-1 rounded-2xl border border-border bg-surface p-4 sm:p-5">
      <dt className="text-sm text-muted-foreground">{term}</dt>
      <dd className="num font-display text-2xl leading-tight font-extrabold tracking-tight break-words sm:text-3xl">
        {value}
      </dd>
      <dd className="text-sm text-muted-foreground">{note}</dd>
    </div>
  )
}

export function StatsSkeleton() {
  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="space-y-3 rounded-2xl border border-border bg-surface p-4 sm:p-5">
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-8 w-24" />
          <Skeleton className="h-4 w-28" />
        </div>
      ))}
    </div>
  )
}
