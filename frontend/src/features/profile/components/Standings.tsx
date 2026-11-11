import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import { Avatar } from '@/shared/ui/Avatar'
import type { MatchHistoryPlayer } from '../api/profileApi'
import { RankDelta } from './MatchHistory'

interface StandingsProps {
  players: MatchHistoryPlayer[]
  /** The caller, to highlight their own row. */
  meId: string | undefined
}

/** Final placements of a room match, in the order the server ranked them (BR-15). */
export function Standings({ players, meId }: StandingsProps) {
  const { t, i18n } = useTranslation('profile')
  const number = new Intl.NumberFormat(i18n.language)
  const ordered = [...players].sort((a, b) => a.rank - b.rank)
  return (
    <ol className="space-y-2">
      {ordered.map((player) => {
        const me = meId !== undefined && player.userId === meId
        return (
          <li
            key={`${String(player.rank)}-${player.userId ?? player.nickname}`}
            className={cn(
              'grid grid-cols-[2rem_auto_minmax(0,1fr)_auto] items-center gap-3 rounded-2xl border bg-surface p-3',
              me ? 'border-primary' : 'border-border',
            )}
          >
            <span className="num text-center font-display text-lg font-extrabold">
              <span className="sr-only">{t('detail.rank')} </span>
              {player.rank}
            </span>
            <Avatar name={player.nickname} size="sm" />
            <div className="min-w-0">
              <p className="truncate font-semibold">
                {player.nickname}
                {me ? <span className="text-muted-foreground"> · {t('detail.you')}</span> : null}
                {player.isGuest ? (
                  <span className="text-muted-foreground"> · {t('detail.guest')}</span>
                ) : null}
              </p>
              <p className="text-sm text-muted-foreground">
                {t('detail.playerCorrect', { count: player.correctCount })}
              </p>
            </div>
            <div className="text-right">
              <p className="num font-semibold">
                {number.format(player.totalScore)}
                <span className="sr-only"> {t('unit')}</span>
              </p>
              {player.rankDelta !== null && player.rankDelta !== undefined ? (
                <RankDelta delta={player.rankDelta} />
              ) : null}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
