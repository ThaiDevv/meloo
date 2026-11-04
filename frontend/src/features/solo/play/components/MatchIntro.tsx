import { Play } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import type { SoloFilter, SoloMatch } from '../api/soloMatchApi'
import { rise } from '../lib/rise'
import { ListeningLadder } from './ListeningLadder'

interface MatchIntroProps {
  match: SoloMatch
  onStart: () => void
  onExit: () => void
}

function filterCount(filter: SoloFilter | undefined): number {
  if (!filter) return 0
  return (
    (filter.genreCodes?.length ?? 0) +
    (filter.countryCodes?.length ?? 0) +
    (filter.decades?.length ?? 0) +
    (filter.artistIds?.length ?? 0)
  )
}

/**
 * Before the first round (or when coming back to a match): what kind of match this is, whether it
 * counts for the ranking (BR-23) and how points work. The start button is also the tap that lets
 * the browser play sound.
 */
export function MatchIntro({ match, onStart, onExit }: MatchIntroProps) {
  const { t, i18n } = useTranslation('solo')
  const round = match.currentRound
  const resuming = round.roundNo > 1 || round.level > 1 || match.totalScore > 0
  const count = filterCount(match.filter)
  const music =
    count === 0
      ? t('play.music.wholeCatalog')
      : t('play.music.filters', { n: count })
  const notice = 'practice'

  return (
    <div className="grid gap-12 lg:grid-cols-[minmax(0,1fr)_minmax(0,26rem)] lg:items-start lg:gap-16">
      <section aria-labelledby="intro-title">
        <h1
          id="intro-title"
          style={rise(0)}
          className="motion-rise font-display text-[clamp(2.5rem,7vw,4.5rem)] leading-none font-extrabold tracking-[-0.03em] text-balance"
        >
          {t(`play.mode.${match.mode}`)}
        </h1>
        <p style={rise(1)} className="motion-rise mt-5 text-lg">
          {resuming
            ? t('play.intro.progress', {
                round: round.roundNo,
                total: round.roundCount,
                score: new Intl.NumberFormat(i18n.language).format(match.totalScore),
              })
            : `${t('play.rounds', { n: match.roundCount })} · ${music}`}
        </p>

        <div
          role="note"
          aria-labelledby="ranking-notice"
          style={rise(2)}
          className="motion-rise mt-8 max-w-xl text-base rounded-2xl p-5 sm:p-6 bg-warning-soft"
        >
          <p
            id="ranking-notice"
            className="font-display text-xl leading-tight font-extrabold tracking-tight sm:text-2xl text-warning"
          >
            {t(`play.intro.${notice}.title`)}
          </p>
          <p className="mt-2">{t(`play.intro.${notice}.body`)}</p>
        </div>

        <div style={rise(3)} className="motion-rise mt-8 flex flex-wrap items-center gap-3">
          <Button className="h-12 px-6 text-base sm:h-12" onClick={onStart}>
            <Play aria-hidden="true" className="size-4 fill-current" />
            {resuming ? t('play.intro.resume', { n: round.roundNo }) : t('play.intro.start')}
          </Button>
          <Button variant="ghost" onClick={onExit}>
            {t('play.exit.button')}
          </Button>
        </div>
      </section>

      <ListeningLadder />
    </div>
  )
}
