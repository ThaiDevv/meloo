import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import { Avatar } from '@/shared/ui/Avatar'
import type { PlayerRankChange, PlayerRanking } from '@/shared/realtime/messages'
import { signed } from '../model/format'
import type { RankView } from '../model/finalTable'
import type { Standing } from '../model/standings'

interface PodiumProps {
  table: readonly PlayerRanking[]
  standings: readonly Standing[]
  /** Quick match only (BR-13, BR-24): where the rank points stand. Undefined in other rooms. */
  rank?: { view: RankView; changes: readonly PlayerRankChange[] | null }
  me: string | null
}

/** Height of each step: the winner stands on the tallest. */
const STEP = ['h-28', 'h-20', 'h-14'] as const
/** Visual order of the top three: 2nd, 1st, 3rd. */
const ORDER = [1, 0, 2] as const

export function Podium({ table, standings, rank, me }: PodiumProps) {
  const { t, i18n } = useTranslation('room')
  const fmt = (value: number) => new Intl.NumberFormat(i18n.language).format(value)
  const avatarOf = (userId: string) =>
    standings.find((player) => player.userId === userId)?.avatarUrl ?? undefined
  const top = table.slice(0, 3)
  const rest = table.slice(3)

  const rankLine = (userId: string) => {
    if (!rank) return null
    const change = rank.changes?.find((item) => item.userId === userId)
    if (rank.view === 'waiting') {
      return <span className="text-xs text-muted-foreground">{t('match.rank.calculating')}</span>
    }
    if (rank.view === 'timeout' || !change) return null
    return (
      <span className="num text-xs">
        <span
          className={cn('font-semibold', change.rankDelta < 0 ? 'text-negative' : 'text-positive')}
        >
          {signed(change.rankDelta, i18n.language)}
        </span>{' '}
        <span className="text-muted-foreground">
          {t('match.rank.points', { n: fmt(change.rankPoints) })}
        </span>
      </span>
    )
  }

  return (
    <section aria-labelledby="match-podium" className="space-y-6">
      <h2 id="match-podium" className="font-display text-3xl font-extrabold tracking-tight">
        {t('match.podium.heading')}
      </h2>
      <ol className="grid grid-cols-3 items-end gap-2 sm:gap-4">
        {ORDER.map((slot) => {
          const row = top[slot]
          if (!row) return <li key={slot} aria-hidden="true" />
          return (
            <li key={row.userId} className="flex min-w-0 flex-col items-center gap-2 text-center">
              <Avatar
                name={row.nickname}
                src={avatarOf(row.userId)}
                avatarId={standings.find((player) => player.userId === row.userId)?.avatarId}
                size="lg"
              />
              <p className="w-full truncate text-sm font-semibold">
                {row.nickname}
                {row.userId === me ? <span className="text-faint"> {t('players.you')}</span> : null}
              </p>
              <p className="num text-lg font-bold">{fmt(row.totalScore)}</p>
              {row.left ? (
                <p className="text-xs text-muted-foreground">{t('match.card.left')}</p>
              ) : null}
              {rankLine(row.userId)}
              <div
                className={cn(
                  'flex w-full items-start justify-center rounded-t-xl pt-2 font-display text-2xl font-extrabold',
                  slot === 0 ? 'bg-primary text-primary-foreground' : 'bg-raised',
                  STEP[slot],
                )}
              >
                <span className="sr-only">{t('match.podium.place')} </span>
                {row.rank}
              </div>
            </li>
          )
        })}
      </ol>

      {rest.length > 0 ? (
        <ol start={4} className="space-y-2">
          {rest.map((row) => (
            <li key={row.userId} className="flex items-center gap-3 rounded-2xl bg-surface p-3">
              <span className="num w-6 text-center text-sm font-semibold text-muted-foreground">
                {row.rank}
              </span>
              <Avatar
                name={row.nickname}
                src={avatarOf(row.userId)}
                avatarId={standings.find((player) => player.userId === row.userId)?.avatarId}
                size="sm"
              />
              <span className="min-w-0 flex-1 truncate text-sm font-semibold">{row.nickname}</span>
              {row.left ? (
                <span className="text-xs text-muted-foreground">{t('match.card.left')}</span>
              ) : null}
              {rankLine(row.userId)}
              <span className="num text-sm font-bold">{fmt(row.totalScore)}</span>
            </li>
          ))}
        </ol>
      ) : null}

      {rank?.view === 'timeout' ? (
        <p className="text-sm text-muted-foreground">{t('match.rank.profile')}</p>
      ) : null}
    </section>
  )
}
