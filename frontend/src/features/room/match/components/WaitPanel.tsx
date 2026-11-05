import { useTranslation } from 'react-i18next'
import type { RoundScoreDetail } from '@/shared/realtime/messages'
import { levelLabel } from '../model/format'

interface WaitPanelProps {
  /** Present when this player guessed right. */
  score: RoundScoreDetail | null
  level: number
}

/** After this player is done with the round: what they got, while the others finish. */
export function WaitPanel({ score, level }: WaitPanelProps) {
  const { t, i18n } = useTranslation('room')
  const fmt = (value: number) => new Intl.NumberFormat(i18n.language).format(value)
  return (
    <section aria-live="polite" className="space-y-4 rounded-3xl bg-surface p-6 sm:p-8">
      <h2 className="font-display text-2xl font-extrabold tracking-tight">
        {score ? t('match.done.correct', { label: levelLabel(level) }) : t('match.done.out')}
      </h2>
      {score ? (
        <dl className="grid max-w-xs grid-cols-[1fr_auto] gap-x-6 gap-y-2 text-sm">
          <dt className="text-muted-foreground">{t('match.result.base')}</dt>
          <dd className="num text-right font-semibold">{fmt(score.baseScore)}</dd>
          <dt className="text-muted-foreground">{t('match.result.combo')}</dt>
          <dd className="num text-right font-semibold">{score.comboPercent}%</dd>
          <dt className="text-muted-foreground">{t('match.result.speed')}</dt>
          <dd className="num text-right font-semibold">{fmt(score.speedBonus)}</dd>
          <dt className="font-semibold">{t('match.result.round')}</dt>
          <dd className="num text-right text-xl font-extrabold text-primary">
            {fmt(score.roundScore)}
          </dd>
        </dl>
      ) : null}
      <p className="text-sm text-muted-foreground">{t('match.done.waiting')}</p>
    </section>
  )
}
