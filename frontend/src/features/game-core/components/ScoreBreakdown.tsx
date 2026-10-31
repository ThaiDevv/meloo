import { useTranslation } from 'react-i18next'
import { formatScore } from '../lib/format'
import type { RoundResult } from '../types'

/** Where the round's points came from: base (BR-02), combo (BR-08) and speed (BR-07), then the sum. */
export function ScoreBreakdown({ result }: { result: RoundResult }) {
  const { t, i18n } = useTranslation('game')
  const fmt = (value: number) => formatScore(value, i18n.language)
  const comboPoints = Math.round((result.baseScore * result.comboPercent) / 100)

  return (
    <section aria-labelledby="gc-score-heading" className="rounded-2xl bg-surface p-5 sm:p-6">
      <h3 id="gc-score-heading" className="text-sm font-semibold text-muted-foreground">
        {t('reveal.score.heading')}
      </h3>
      <dl className="mt-4 grid grid-cols-[1fr_auto] items-baseline gap-x-6 gap-y-3 text-sm">
        <dt className="text-muted-foreground">{t('reveal.score.base')}</dt>
        <dd className="num text-right text-base font-semibold">{fmt(result.baseScore)}</dd>

        <dt className="text-muted-foreground">{t('reveal.score.combo')}</dt>
        <dd className="num text-right text-base font-semibold">
          {result.comboPercent > 0 ? (
            t('reveal.score.comboValue', { percent: result.comboPercent, points: fmt(comboPoints) })
          ) : (
            <Dash />
          )}
        </dd>

        <dt className="text-muted-foreground">{t('reveal.score.speed')}</dt>
        <dd className="num text-right text-base font-semibold">
          {result.speedBonus > 0 ? `+${fmt(result.speedBonus)}` : <Dash />}
        </dd>

        <dt className="pt-3 text-base font-semibold">{t('reveal.score.round')}</dt>
        <dd className="num pt-3 text-right font-display text-4xl font-extrabold tracking-tight text-primary">
          {fmt(result.roundScore)}
        </dd>
      </dl>
      <p className="num mt-4 flex items-baseline justify-between gap-4 text-sm text-muted-foreground">
        <span>{t('reveal.score.total')}</span>
        <span className="text-base font-semibold text-foreground">{fmt(result.totalScore)}</span>
      </p>
      {result.comboStreak > 0 ? (
        <p className="mt-1 text-xs text-faint">
          {t('reveal.score.streak', { n: result.comboStreak })}
        </p>
      ) : null}
    </section>
  )
}

/** A value that does not apply this round (no combo yet, no clock in single play) is a quiet dash, not a "+0". */
function Dash() {
  const { t } = useTranslation('game')
  return (
    <span className="text-faint">
      <span aria-hidden="true">—</span>
      <span className="sr-only">{t('reveal.score.none')}</span>
    </span>
  )
}
