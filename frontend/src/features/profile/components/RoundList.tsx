import { ExternalLink } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { CoverArt } from '@/features/game-core'
import { cn } from '@/shared/lib/utils'
import type { MatchHistoryRound } from '../api/profileApi'
import { secondsForLevel } from '../lib/format'

/** Every round of a finished match: the song, the level it was solved at, and the points it gave. */
export function RoundList({ rounds }: { rounds: MatchHistoryRound[] }) {
  return (
    <ol className="space-y-2">
      {rounds.map((round) => (
        <RoundRow key={round.roundNo} round={round} />
      ))}
    </ol>
  )
}

function RoundRow({ round }: { round: MatchHistoryRound }) {
  const { t, i18n } = useTranslation('profile')
  const number = new Intl.NumberFormat(i18n.language)
  const correct = round.outcome === 'CORRECT'
  const seconds =
    round.listenSeconds ??
    (round.correctLevel === null || round.correctLevel === undefined
      ? null
      : secondsForLevel(round.correctLevel))

  return (
    <li
      className="grid grid-cols-[3.5rem_minmax(0,1fr)_auto] items-center gap-4 rounded-2xl border border-border bg-surface p-3 sm:grid-cols-[4rem_minmax(0,1fr)_auto]"
      data-testid="round-row"
    >
      <CoverArt url={round.answer.coverUrl} title={round.answer.title} className="rounded-xl" />
      <div className="min-w-0">
        <p className="text-sm text-muted-foreground">{t('detail.round', { n: round.roundNo })}</p>
        <p className="truncate font-semibold">{round.answer.title}</p>
        <p className="truncate text-sm text-muted-foreground">{round.answer.artistName}</p>
        <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
          <span className={cn(correct ? 'font-semibold text-positive' : 'text-muted-foreground')}>
            {correct && round.correctLevel !== null && round.correctLevel !== undefined
              ? t('detail.correctAt', {
                  level: round.correctLevel,
                  seconds: seconds ?? '?',
                })
              : t(`outcome.${round.outcome}`)}
          </span>
          {round.wrongCount > 0 ? (
            <span className="text-muted-foreground">
              {t('detail.wrong', { count: round.wrongCount })}
            </span>
          ) : null}
          <a
            href={round.answer.listenUrl}
            target="_blank"
            rel="noreferrer noopener"
            className="inline-flex items-center gap-1 font-semibold underline-offset-4 hover:underline"
          >
            {t('detail.listen')}
            <ExternalLink aria-hidden="true" className="size-3.5" />
            <span className="sr-only">{t('detail.newTab')}</span>
          </a>
        </p>
      </div>
      <div className="text-right">
        <p className="num font-display text-xl leading-none font-extrabold tracking-tight">
          {number.format(round.roundScore)}
          <span className="sr-only"> {t('unit')}</span>
        </p>
        {round.comboPercent > 0 ? (
          <p className="num mt-1 text-sm font-semibold text-positive">
            {t('detail.combo', { percent: round.comboPercent })}
          </p>
        ) : null}
      </div>
    </li>
  )
}
