import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'

interface MatchCountdownProps {
  /** Seconds before the match starts; null while nothing is counting. */
  seconds: number | null
  /** A matchmaking room: also says how many are here and offers the 60 s hint (BR-13). */
  matchmaking?: { players: number; max: number; soloSuggestion: string | null }
  stoppedReason?: 'PLAYER_LEFT' | 'NOT_ENOUGH_PLAYERS' | null
}

/**
 * The clock that starts a match: shown to everyone once a countdown runs, and in a matchmaking
 * room (no host, no start button) also while it waits for a second player.
 */
export function MatchCountdown({ seconds, matchmaking, stoppedReason }: MatchCountdownProps) {
  const { t } = useTranslation('room')
  if (seconds === null && !matchmaking) return null
  return (
    <section aria-labelledby="countdown-heading" className="space-y-3">
      <h2 id="countdown-heading" className="text-sm font-medium text-muted-foreground">
        {t('countdown.heading')}
      </h2>
      {seconds !== null ? (
        <p role="timer" className="num font-display text-6xl leading-none font-extrabold">
          {seconds}
          <span className="ml-2 text-lg font-semibold text-muted-foreground">
            {t('countdown.unit')}
          </span>
        </p>
      ) : (
        <p className="font-semibold">
          {t('countdown.waiting', { n: matchmaking?.players ?? 0, max: matchmaking?.max ?? 0 })}
        </p>
      )}
      {stoppedReason && seconds === null ? (
        <p className="text-sm text-muted-foreground">{t(`countdown.stopped.${stoppedReason}`)}</p>
      ) : null}
      {matchmaking?.soloSuggestion ? (
        <div className="space-y-3 rounded-xl bg-raised p-4">
          <p className="text-sm">{t('countdown.soloHint')}</p>
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline" size="sm">
              <Link to="/solo">{t('countdown.playSolo')}</Link>
            </Button>
            <Button asChild variant="outline" size="sm">
              <Link to="/rooms/new">{t('countdown.createPrivate')}</Link>
            </Button>
          </div>
        </div>
      ) : null}
    </section>
  )
}
