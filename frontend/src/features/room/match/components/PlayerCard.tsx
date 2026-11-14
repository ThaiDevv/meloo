import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import { Avatar } from '@/shared/ui/Avatar'
import { levelLabel } from '../model/format'
import type { Standing } from '../model/standings'

/**
 * One player of the live table: avatar, score, provisional place and what they did this round.
 * Under 640 px the card shrinks to avatar and score; tapping it shows the name. A correct answer is
 * shown by its level and points only, never by the answer itself.
 */
export function PlayerCard({ player, leader }: { player: Standing; leader: boolean }) {
  const { t, i18n } = useTranslation('room')
  const [expanded, setExpanded] = useState(false)
  const gone = player.left || !player.connected
  const status = player.left
    ? t('match.card.left')
    : !player.connected
      ? t('match.card.away')
      : player.guessed
        ? t('match.card.guessedAt', { label: levelLabel(player.guessed.level) })
        : player.gaveUp
          ? t('match.card.gaveUp')
          : null
  const score = new Intl.NumberFormat(i18n.language).format(player.score)

  return (
    <li
      aria-label={player.nickname}
      className={cn(
        'rounded-2xl bg-surface p-2 sm:p-3',
        leader && 'ring-2 ring-primary',
        gone && 'opacity-50',
      )}
    >
      <button
        type="button"
        aria-expanded={expanded}
        aria-label={t('match.card.toggle', { name: player.nickname })}
        onClick={() => {
          setExpanded((value) => !value)
        }}
        className="flex w-full min-w-0 items-center gap-2 text-left sm:pointer-events-none sm:gap-3"
      >
        <span className="num w-5 shrink-0 text-center text-sm font-semibold text-muted-foreground">
          {player.rank}
        </span>
        <Avatar
          name={player.nickname}
          src={player.avatarUrl ?? undefined}
          avatarId={player.avatarId}
          size="sm"
        />
        <span className="min-w-0 flex-1">
          <span
            className={cn('block truncate text-sm font-semibold', !expanded && 'max-sm:hidden')}
          >
            {player.nickname}
            {player.self ? <span className="text-faint"> {t('players.you')}</span> : null}
          </span>
          {status ? (
            <span
              className={cn('block text-xs text-muted-foreground', !expanded && 'max-sm:hidden')}
            >
              {status}
            </span>
          ) : null}
        </span>
        <span className="flex flex-col items-end">
          <span className="num text-base font-bold">{score}</span>
          {player.guessed ? (
            <span className="num text-xs font-semibold text-positive">
              +{new Intl.NumberFormat(i18n.language).format(player.guessed.roundScore)}
            </span>
          ) : null}
        </span>
      </button>
    </li>
  )
}
