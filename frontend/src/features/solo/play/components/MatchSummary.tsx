import { RotateCcw, Share2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { CoverArt, type RoundResult } from '@/features/game-core'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/Button'
import type { MatchResult } from '../api/soloMatchApi'
import { rise } from '../lib/rise'
import { outcomeText } from '../lib/share'
import { longestCombo } from '../model/matchState'

interface MatchSummaryProps {
  result: MatchResult
  replaying?: boolean
  onReplay: () => void
  onShare: () => void
}

/** The end of a match, every figure as the server sent it (MatchResult). */
export function MatchSummary({ result, replaying = false, onReplay, onShare }: MatchSummaryProps) {
  const { t, i18n } = useTranslation('solo')
  const number = new Intl.NumberFormat(i18n.language)
  const abandoned = result.status === 'ABANDONED'
  const combo = longestCombo(result)

  return (
    <div className="grid gap-12 lg:grid-cols-[minmax(0,24rem)_minmax(0,1fr)] lg:items-start lg:gap-16">
      <section aria-labelledby="summary-title" className="lg:sticky lg:top-20">
        <h1
          id="summary-title"
          style={rise(0)}
          className="motion-rise font-display text-3xl leading-tight font-extrabold tracking-tight sm:text-4xl"
        >
          {t(`play.summary.${abandoned ? 'abandonedTitle' : 'title'}.${result.mode}`)}
        </h1>

        <p
          style={rise(1)}
          className="num motion-rise mt-6 flex flex-wrap items-baseline gap-x-3"
          data-testid="match-total"
        >
          <span className="sr-only">{t('play.summary.total')}: </span>
          <span className="font-display text-[clamp(4.5rem,16vw,7.5rem)] leading-[0.85] font-extrabold tracking-[-0.04em] text-primary">
            {number.format(result.totalScore)}
          </span>
          <span className="text-xl font-semibold">{t('play.summary.unit')}</span>
        </p>

        <ul style={rise(2)} className="motion-rise mt-8 space-y-2 text-lg">
          <li>
            {t('play.summary.correct', { correct: result.correctCount, total: result.roundCount })}
          </li>
          <li>
            {combo > 0
              ? t('play.summary.longestCombo', { count: combo })
              : t('play.summary.noCombo')}
          </li>
          {abandoned ? (
            <li className="font-semibold text-warning">
              {t('play.summary.abandoned', {
                done: result.rounds.length,
                total: result.roundCount,
              })}
            </li>
          ) : null}
        </ul>

        <div style={rise(3)} className="motion-rise mt-8 flex flex-wrap items-center gap-3">
          <Button className="h-12 px-6 text-base sm:h-12" disabled={replaying} onClick={onReplay}>
            <RotateCcw aria-hidden="true" className="size-4" strokeWidth={2.25} />
            {replaying ? t('play.summary.starting') : t('play.summary.playAgain')}
          </Button>
          <Button variant="outline" className="h-12 px-5 sm:h-12" onClick={onShare}>
            <Share2 aria-hidden="true" className="size-4" strokeWidth={2} />
            {t('play.summary.share')}
          </Button>
        </div>
        <Link
          to="/solo"
          className="mt-5 inline-block text-sm font-semibold underline-offset-4 hover:underline"
        >
          {t('play.summary.change')}
        </Link>
      </section>

      <section aria-labelledby="rounds-heading">
        <h2
          id="rounds-heading"
          className="font-display text-2xl leading-tight font-extrabold tracking-tight"
        >
          {t('play.summary.roundsHeading')}
        </h2>
        {result.rounds.length === 0 ? (
          <p className="mt-6 rounded-2xl bg-surface px-5 py-6 text-muted-foreground">
            {t('play.summary.noRounds')}
          </p>
        ) : (
          <ol className="mt-6 rounded-3xl bg-surface p-2 sm:p-3">
            {result.rounds.map((round, index) => (
              <RoundRow key={round.roundNo} round={round} index={index} />
            ))}
          </ol>
        )}
      </section>
    </div>
  )
}

function RoundRow({ round, index }: { round: RoundResult; index: number }) {
  const { t, i18n } = useTranslation('solo')
  const correct = round.outcome === 'CORRECT'
  return (
    <li
      className="motion-rise grid grid-cols-[3.5rem_minmax(0,1fr)_auto] items-center gap-4 rounded-2xl p-2 transition-colors hover:bg-raised sm:grid-cols-[4.5rem_minmax(0,1fr)_auto] sm:p-3"
      style={rise(Math.min(index, 10) + 2)}
    >
      <CoverArt url={round.answer.coverUrl} title={round.answer.title} className="rounded-xl" />
      <div className="min-w-0">
        <p className="text-sm text-muted-foreground">
          {t('play.summary.round', { n: round.roundNo })}
        </p>
        <p className="truncate font-semibold">{round.answer.title}</p>
        <p className="truncate text-sm text-muted-foreground">{round.answer.artistName}</p>
        <p className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
          <LevelStrip round={round} />
          <span
            className={cn(
              'whitespace-nowrap',
              correct ? 'font-semibold text-positive' : 'text-muted-foreground',
            )}
          >
            {outcomeText(round, t)}
          </span>
        </p>
      </div>
      <div className="text-right">
        <p className="num font-display text-2xl leading-none font-extrabold tracking-tight">
          {new Intl.NumberFormat(i18n.language).format(round.roundScore)}
          <span className="sr-only"> {t('play.summary.unit')}</span>
        </p>
        {round.comboPercent > 0 ? (
          <p className="num mt-1 text-sm font-semibold text-positive">
            {t('play.summary.combo', { percent: round.comboPercent })}
          </p>
        ) : null}
      </div>
    </li>
  )
}

/** Each segment is as long as the stretch of clip its level adds (2, +3, +5, +10, +10 seconds). */
const SEGMENTS = [2, 3, 5, 10, 10]

/** How much of the clip the round needed: lit up to the right level, or heard to the end and missed. */
function LevelStrip({ round }: { round: RoundResult }) {
  const correct = round.outcome === 'CORRECT'
  const reached = round.correctLevel ?? 5
  return (
    <span aria-hidden="true" className="flex h-2 w-20 shrink-0 gap-0.5 sm:w-32">
      {SEGMENTS.map((grow, index) => (
        <span
          key={grow * 10 + index}
          style={{ flexGrow: grow }}
          className={cn(
            'basis-0 rounded-full',
            correct ? (index < reached ? 'bg-primary' : 'bg-foreground/10') : 'bg-foreground/30',
          )}
        />
      ))}
    </span>
  )
}
