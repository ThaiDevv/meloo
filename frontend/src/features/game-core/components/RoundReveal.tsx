import { ExternalLink } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/Button'
import { levelLabel } from '../lib/levels'
import { providerName } from '../lib/format'
import type { RoundResult } from '../types'
import { CoverArt } from './CoverArt'
import { ScoreBreakdown } from './ScoreBreakdown'
import '../game-core.css'

interface RoundRevealProps {
  /** Published by the server when the round is over. This is the only place the answer appears (NFR-07). */
  result: RoundResult
  matchFinished?: boolean
  /** Shown as "Next round", or "See results" after the last one. Omit it when the host has its own button. */
  onContinue?: () => void
  busy?: boolean
}

const LENGTHS = [2, 5, 10, 20, 30]

export function RoundReveal({
  result,
  matchFinished = false,
  onContinue,
  busy = false,
}: RoundRevealProps) {
  const { t } = useTranslation('game')
  const { answer } = result
  const correct = result.outcome === 'CORRECT'
  const level = result.correctLevel ?? 1
  const outcomeText = t(`reveal.outcome.${result.outcome}`, {
    label: levelLabel(LENGTHS[level - 1] ?? 30),
  })

  return (
    <section
      aria-labelledby="gc-answer-title"
      className="grid gap-6 md:grid-cols-[minmax(0,20rem)_1fr] md:gap-8"
    >
      <div className="relative">
        <CoverArt
          url={answer.coverUrl}
          title={answer.title}
          className="gc-cover mx-auto size-52 md:mx-0 md:size-auto md:w-full md:max-w-80"
        />
        {correct ? (
          <span
            aria-hidden="true"
            className="gc-burst pointer-events-none absolute inset-0 mx-auto size-52 md:mx-0 md:size-auto md:max-w-80 rounded-2xl border-2 border-primary"
          />
        ) : null}
      </div>

      <div className="gc-stagger flex min-w-0 flex-col gap-5">
        <div>
          <p className={cn('text-base font-semibold', correct ? 'text-primary' : 'text-negative')}>
            {outcomeText}
          </p>
          <h2
            id="gc-answer-title"
            className="mt-2 font-display text-3xl leading-tight font-extrabold tracking-tight break-words sm:text-4xl"
          >
            <span className="sr-only">{t('reveal.answer')}: </span>
            {answer.title}
          </h2>
          <p className="mt-1 text-lg text-muted-foreground">{answer.artistName}</p>
          {result.wrongCount > 0 ? (
            <p className="mt-2 text-sm text-faint">
              {t('reveal.wrongCount', { count: result.wrongCount })}
            </p>
          ) : null}
        </div>

        <ScoreBreakdown result={result} />

        <div className="flex flex-wrap items-center gap-3">
          {onContinue ? (
            <Button onClick={onContinue} disabled={busy} autoFocus>
              {matchFinished ? t('reveal.finish') : t('reveal.next')}
            </Button>
          ) : null}
          <Button variant="ghost" asChild>
            <a href={answer.listenUrl} target="_blank" rel="noopener noreferrer">
              {t('reveal.listenOn', { provider: providerName(answer.provider) })}
              <ExternalLink aria-hidden="true" className="size-4" strokeWidth={2} />
            </a>
          </Button>
        </div>
      </div>
    </section>
  )
}
