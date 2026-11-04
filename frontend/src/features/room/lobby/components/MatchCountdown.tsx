import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Button } from '@/shared/ui/Button'

interface MatchCountdownProps {
  /** Seconds before the match starts; null while nothing is counting. */
  seconds: number | null
  /** A matchmaking room: also says how many are here while it waits (BR-13). */
  matchmaking?: { players: number; max: number }
  stoppedReason?: 'PLAYER_LEFT' | 'NOT_ENOUGH_PLAYERS' | null
}

/**
 * The clock that starts a match: shown once a countdown runs, and in a matchmaking room (no host,
 * no start button) also while it waits for a second player. Meant to sit on the lobby's coloured
 * field, so it takes its colour from the field.
 */
export function MatchCountdown({ seconds, matchmaking, stoppedReason }: MatchCountdownProps) {
  const { t } = useTranslation('room')
  if (seconds === null && !matchmaking) return null
  return (
    <section aria-labelledby="countdown-heading" className="space-y-3">
      <h2 id="countdown-heading" className="text-base font-semibold opacity-80">
        {t('countdown.heading')}
      </h2>
      {seconds !== null ? (
        <p
          role="timer"
          className="num font-display text-[clamp(4.5rem,20vw,7rem)] leading-none font-extrabold"
        >
          {seconds}
          <span className="ml-3 text-2xl font-semibold opacity-80">{t('countdown.unit')}</span>
        </p>
      ) : (
        <p className="font-display text-2xl leading-snug font-extrabold tracking-[-0.01em]">
          {t('countdown.waiting', { n: matchmaking?.players ?? 0, max: matchmaking?.max ?? 0 })}
        </p>
      )}
      {stoppedReason && seconds === null ? (
        <p className="text-sm opacity-80">{t(`countdown.stopped.${stoppedReason}`)}</p>
      ) : null}
    </section>
  )
}

/** After 60 s alone in a matchmaking room: play solo, or make a private room for friends (BR-13). */
export function SoloHint() {
  const { t } = useTranslation('room')
  return (
    <section className="space-y-3 rounded-2xl bg-raised p-5">
      <p>{t('countdown.soloHint')}</p>
      <div className="flex flex-wrap gap-2">
        <Button asChild variant="outline" size="sm">
          <Link to="/solo">{t('countdown.playSolo')}</Link>
        </Button>
        <Button asChild variant="outline" size="sm">
          <Link to="/rooms/new">{t('countdown.createPrivate')}</Link>
        </Button>
      </div>
    </section>
  )
}
