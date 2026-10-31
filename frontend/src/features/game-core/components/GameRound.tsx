import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/Button'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Skeleton } from '@/shared/ui/Skeleton'
import { Equalizer } from '@/shared/music-picker'
import { Play } from 'lucide-react'
import { SnippetPlayer } from '../audio/snippetPlayer'
import { useSnippetPlayer } from '../audio/useSnippetPlayer'
import { formatScore } from '../lib/format'
import { levelLabel, pointsForLevel } from '../lib/levels'
import { useCountTo } from '../lib/motion'
import type {
  GuessVerdict,
  RoundResult,
  RoundState,
  RoundTimerState,
  TrackSuggestion,
} from '../types'
import { ClipStrip } from './ClipStrip'
import { GuessBox } from './GuessBox'
import { GuessFeedback } from './GuessFeedback'
import { ListenControls } from './ListenControls'
import { RoundReveal } from './RoundReveal'
import { RoundTimer } from './RoundTimer'
import { Turntable } from './Turntable'

export interface GameRoundProps {
  /** The open round, straight from the server. It carries no answer (NFR-07). */
  round: RoundState
  /** Match score before this round. */
  totalScore?: number
  /** Present where combos exist (single play, rooms); leave out for the daily challenge (BR-08). */
  combo?: { streak: number }
  /** Present where the round has a time limit. */
  timer?: RoundTimerState
  suggestions?: readonly TrackSuggestion[]
  suggestionsLoading?: boolean
  verdict?: GuessVerdict | null
  /** A request from this screen is in flight; actions wait for it. */
  busy?: boolean
  /** Set once the server has published the answer. The screen then shows the reveal instead of the round. */
  result?: RoundResult | null
  matchFinished?: boolean
  /** Clips needed soon (e.g. the next round's), decoded ahead of time. */
  preloadUrls?: readonly string[]
  autoPlay?: boolean
  onSearch: (query: string) => void
  onGuess: (trackId: string) => void
  /** `expectedLevel` is the level being opened, to guard against a double click (CT-01c). */
  onListenMore: (expectedLevel: number) => void
  onSkip: () => void
  onTimeout?: () => void
  /** The clip link expired: fetch the round again for a fresh `audio.url`. */
  onAudioExpired?: () => void
  onContinue?: () => void
}

/**
 * The round screen shared by single play, the daily challenge and rooms. It takes the round and
 * hands back actions; it never calls an API or a socket (so it works under every mode) and it shows
 * the answer only when the host passes the server's `result`.
 */
export function GameRound(props: GameRoundProps) {
  const { round, result, totalScore, combo, matchFinished, busy = false, onContinue } = props
  const { t } = useTranslation('game')
  // One player for the whole match: once a tap has unlocked sound, later rounds can start by themselves.
  const [player] = useState(() => new SnippetPlayer())
  useEffect(
    () => () => {
      player.dispose()
    },
    [player],
  )

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3">
        <div className="flex items-end justify-between gap-6">
          <div className="flex flex-col gap-2">
            <p className="num text-sm font-semibold text-muted-foreground">
              {t('header.round', { current: round.roundNo, total: round.roundCount })}
            </p>
            <RoundPips current={round.roundNo} total={round.roundCount} />
          </div>
          {totalScore !== undefined ? <TotalScore value={totalScore} /> : null}
        </div>
        {combo && !result ? <ComboLine streak={combo.streak} /> : null}
      </header>

      {result ? (
        <RoundReveal
          key={result.roundNo}
          result={result}
          matchFinished={matchFinished}
          onContinue={
            onContinue
              ? () => {
                  void player.unlock()
                  onContinue()
                }
              : undefined
          }
          busy={busy}
        />
      ) : (
        <RoundPlay key={round.roundNo} {...props} player={player} />
      )}
    </div>
  )
}

function TotalScore({ value }: { value: number }) {
  const { t, i18n } = useTranslation('game')
  const shown = useCountTo(value, { ms: 900 })
  return (
    <p className="num flex flex-col items-end">
      <span className="text-xs text-faint">{t('header.total')}</span>
      <span className="font-display text-3xl leading-none font-extrabold tracking-tight">
        {formatScore(shown, i18n.language)}
      </span>
    </p>
  )
}

/** One mark per round of the match: past rounds dim, this one long and lit. */
function RoundPips({ current, total }: { current: number; total: number }) {
  return (
    <span aria-hidden="true" className="flex items-center gap-1">
      {Array.from({ length: total }, (_, index) => {
        const n = index + 1
        return (
          <span
            key={n}
            className={cn(
              'h-1.5 rounded-full transition-[width,background-color] duration-300',
              n < current && 'w-2.5 bg-foreground/40 sm:w-3',
              n === current && 'w-6 bg-primary sm:w-8',
              n > current && 'w-2.5 bg-foreground/12 sm:w-3',
            )}
          />
        )
      })}
    </span>
  )
}

/** BR-08 as three notches: one right round starts the chain, the second earns +10%, the third +20%. */
function ComboLine({ streak }: { streak: number }) {
  const { t } = useTranslation('game')
  const percent = streak >= 2 ? 20 : 10
  return (
    <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
      <span aria-hidden="true" className="flex gap-1">
        {[1, 2, 3].map((notch) => (
          <span
            key={notch}
            className={cn(
              'h-3 w-1.5 -skew-x-12 rounded-sm transition-colors duration-300',
              streak >= notch ? 'bg-primary' : 'bg-foreground/15',
            )}
          />
        ))}
      </span>
      <span className={cn('num font-semibold', streak > 0 ? 'text-primary' : 'text-faint')}>
        {streak > 0 ? t('combo.streak', { count: streak }) : t('combo.none')}
      </span>
      <span>{streak > 0 ? t('combo.next', { percent }) : t('combo.nextFirst')}</span>
    </p>
  )
}

/** What a right guess is still worth. It counts down when a level opens, and says how much went. */
function PointsLeft({ level }: { level: number }) {
  const { t, i18n } = useTranslation('game')
  const points = pointsForLevel(level)
  const shown = useCountTo(points)
  const [firstLevel] = useState(level)
  return (
    <p className="num" data-testid="points-left">
      <span className="relative inline-block font-display text-6xl leading-none font-extrabold tracking-tight text-primary sm:text-7xl">
        {formatScore(shown, i18n.language)}
        {level > firstLevel ? (
          <span
            key={level}
            aria-hidden="true"
            className="gc-float absolute top-0 left-full ml-3 text-lg font-bold text-negative"
          >
            −{formatScore(200, i18n.language)}
          </span>
        ) : null}
      </span>
      <span className="mt-2 block text-sm text-muted-foreground">{t('stage.pointsLeft')}</span>
    </p>
  )
}

function RoundPlay({
  round,
  player,
  timer,
  suggestions = [],
  suggestionsLoading = false,
  verdict,
  busy = false,
  preloadUrls,
  autoPlay = true,
  onSearch,
  onGuess,
  onListenMore,
  onSkip,
  onTimeout,
  onAudioExpired,
}: GameRoundProps & { player: SnippetPlayer }) {
  const { t } = useTranslation('game')
  const audio = useSnippetPlayer({
    url: round.audio.url,
    seconds: round.listenSeconds,
    autoPlay,
    preloadUrls,
    onExpired: onAudioExpired,
    player,
  })
  const label = levelLabel(round.listenSeconds)

  return (
    <div className="flex flex-col gap-6">
      <section
        aria-label={t('stage.level', { n: round.level, max: 5 })}
        aria-busy={audio.status === 'loading'}
        className="grid gap-6 rounded-3xl bg-surface p-5 sm:p-8 md:grid-cols-[minmax(0,25rem)_1fr] md:items-center md:gap-x-10 md:p-10"
      >
        <Turntable
          level={round.level}
          listenSeconds={round.listenSeconds}
          playing={audio.playing}
          player={player}
          joltKey={verdict && !verdict.correct ? verdict.nonce : undefined}
          className="mx-auto w-full max-w-80 md:max-w-none"
        />
        <div className="flex min-w-0 flex-col gap-6">
          <h2 className="font-display text-2xl font-extrabold tracking-tight md:text-3xl">
            {t('stage.heading')}
          </h2>
          <PointsLeft level={round.level} />
          <AudioStatus
            status={audio.status}
            failure={audio.failure}
            playing={audio.playing}
            label={label}
            onTap={audio.play}
            onRetry={audio.retry}
          />
          <ListenControls
            level={round.level}
            canListenMore={round.canListenMore}
            canSkip={round.canSkip}
            busy={busy}
            canReplay={audio.status === 'ready' || audio.status === 'blocked'}
            onReplay={audio.play}
            onListenMore={() => {
              audio.unlock()
              onListenMore(round.level + 1)
            }}
            onSkip={() => {
              audio.unlock()
              onSkip()
            }}
          />
        </div>
        <ClipStrip
          level={round.level}
          listenSeconds={round.listenSeconds}
          url={round.audio.url}
          decoded={audio.status === 'ready' || audio.status === 'blocked'}
          playing={audio.playing}
          player={player}
          className="md:col-span-2"
        />
      </section>

      {timer ? <RoundTimer timer={timer} onTimeout={onTimeout} /> : null}

      <div className="flex flex-col gap-4">
        <GuessBox
          suggestions={suggestions}
          loading={suggestionsLoading}
          disabled={busy}
          onSearch={onSearch}
          onGuess={(trackId) => {
            audio.unlock()
            onGuess(trackId)
          }}
        />
        <GuessFeedback verdict={verdict} listenSeconds={round.listenSeconds} />
      </div>
    </div>
  )
}

function AudioStatus({
  status,
  failure,
  playing,
  label,
  onTap,
  onRetry,
}: {
  status: 'loading' | 'ready' | 'blocked' | 'error'
  failure: 'expired' | 'network' | 'decode' | 'blocked' | null
  playing: boolean
  label: string
  onTap: () => void
  onRetry: () => void
}) {
  const { t } = useTranslation('game')

  if (status === 'error') {
    return (
      <ErrorState
        className="w-full border-0 bg-raised py-6"
        title={t('audio.errorTitle')}
        description={t(
          `audio.error.${failure === 'expired' || failure === 'decode' ? failure : 'network'}`,
        )}
        onRetry={onRetry}
      />
    )
  }
  if (status === 'blocked') {
    return (
      <div className="flex flex-col items-center gap-2">
        <Button onClick={onTap} className="h-12 px-6">
          <Play aria-hidden="true" className="size-4" strokeWidth={2} />
          {t('audio.tapToListen')}
        </Button>
        <p className="text-xs text-faint">{t('audio.tapHint')}</p>
      </div>
    )
  }
  if (status === 'loading') {
    return (
      <div role="status" className="flex items-center gap-3 text-sm text-muted-foreground">
        <Skeleton className="h-4 w-24" />
        <span className="sr-only">{t('audio.loading')}</span>
      </div>
    )
  }
  return (
    <p role="status" className="flex h-6 items-center gap-2 text-sm text-muted-foreground">
      {playing ? (
        <>
          <Equalizer className="text-base text-primary" />
          {t('audio.playing', { label })}
        </>
      ) : null}
    </p>
  )
}
