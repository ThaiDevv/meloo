import { useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import type { SnippetPlayer } from '../audio/snippetPlayer'
import { formatScore } from '../lib/format'
import { pointsForLevel } from '../lib/levels'
import { useFrameLoop } from '../lib/motion'

/** The longest clip; the strip is drawn to this scale. */
const TOTAL_SECONDS = 30
/** The level lengths of BR-01, as marks on the strip. The server confirms the opened one. */
const LENGTHS = [2, 5, 10, 20, 30]
const BARS_PER_SECOND = 4
const BAR_COUNT = TOTAL_SECONDS * BARS_PER_SECOND
const HEIGHT = 40

interface ClipStripProps {
  /** Opened level, 1 to 5. */
  level: number
  /** Length of the opened level in seconds. */
  listenSeconds: number
  /** The opened clip; its waveform is drawn once it has been decoded. */
  url: string
  /** The clip has been decoded and can be drawn. */
  decoded: boolean
  playing: boolean
  player: SnippetPlayer
  className?: string
}

/**
 * The 30 seconds of the preview on one strip, to scale: 2 s is a fifteenth of it. The part the
 * player has unlocked shows the real waveform of what they heard, the rest stays dotted, and a
 * playhead runs along while the clip plays. Each level mark carries what a right guess is worth.
 */
export function ClipStrip({
  level,
  listenSeconds,
  url,
  decoded,
  playing,
  player,
  className,
}: ClipStripProps) {
  const { t, i18n } = useTranslation('game')
  const clipId = `gc-played-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`
  const [wave, setWave] = useState<{ url: string; peaks: number[] } | null>(null)
  const playedRef = useRef<SVGRectElement>(null)
  const headRef = useRef<HTMLDivElement>(null)
  const unlockedBars = Math.round(listenSeconds * BARS_PER_SECOND)
  const peaks = wave?.url === url ? wave.peaks : null

  useEffect(() => {
    if (!decoded) return
    let current = true
    player
      .waveform(url, BARS_PER_SECOND)
      .then((next) => {
        if (current) setWave({ url, peaks: next })
      })
      .catch(() => undefined)
    return () => {
      current = false
    }
  }, [decoded, player, url])

  useFrameLoop(playing, () => {
    const elapsed = player.position()?.elapsed ?? 0
    playedRef.current?.setAttribute('width', (elapsed * BARS_PER_SECOND).toFixed(2))
    if (headRef.current) {
      headRef.current.style.transform = `translateX(${String((elapsed / TOTAL_SECONDS) * 100)}%)`
    }
  })

  useEffect(() => {
    if (playing) return
    playedRef.current?.setAttribute('width', '0')
  }, [playing])

  const bars = Array.from({ length: BAR_COUNT }, (_, index) => {
    const unlocked = index < unlockedBars
    const peak = unlocked ? (peaks?.[index] ?? 0.08) : 0
    const height = unlocked ? Math.max(2.4, peak * (HEIGHT - 4)) : 1.6
    return { index, unlocked, height }
  })
  const unlockedShare = (listenSeconds / TOTAL_SECONDS) * 100

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      {/* overflow-x-clip: the playhead layer travels by transform and must not widen the page. */}
      <div aria-hidden="true" className="relative h-14 overflow-x-clip">
        <div
          className="absolute inset-y-0 left-0 rounded-lg bg-raised transition-[width] duration-500 ease-out motion-reduce:transition-none"
          style={{ width: `${String(unlockedShare)}%` }}
        />
        <svg
          viewBox={`0 0 ${String(BAR_COUNT)} ${String(HEIGHT)}`}
          preserveAspectRatio="none"
          className="absolute inset-0 size-full"
        >
          <defs>
            <clipPath id={clipId}>
              <rect ref={playedRef} x={0} y={0} width={0} height={HEIGHT} />
            </clipPath>
          </defs>
          {bars.map(({ index, unlocked, height }) => (
            <rect
              key={index}
              x={index + 0.2}
              y={(HEIGHT - height) / 2}
              width={0.6}
              height={height}
              className={unlocked ? 'fill-primary' : 'fill-foreground'}
              fillOpacity={unlocked ? 0.45 : 0.16}
            />
          ))}
          <g clipPath={`url(#${clipId})`}>
            {bars
              .filter((bar) => bar.unlocked)
              .map(({ index, height }) => (
                <rect
                  key={index}
                  x={index + 0.2}
                  y={(HEIGHT - height) / 2}
                  width={0.6}
                  height={height}
                  className="fill-primary"
                />
              ))}
          </g>
        </svg>
        {LENGTHS.map((length, index) => (
          <span
            key={length}
            className={cn(
              'absolute inset-y-0 w-0.5 -translate-x-1/2 rounded-full',
              index + 1 === level ? 'bg-primary' : 'bg-foreground/20',
              index === LENGTHS.length - 1 && '-translate-x-full',
            )}
            style={{ left: `${String((length / TOTAL_SECONDS) * 100)}%` }}
          />
        ))}
        <div
          ref={headRef}
          className={cn(
            'pointer-events-none absolute inset-0 transition-opacity duration-200',
            playing ? 'opacity-100' : 'opacity-0',
          )}
        >
          <span className="absolute -inset-y-1 left-0 w-0.5 -translate-x-1/2 rounded-full bg-foreground" />
        </div>
      </div>

      <ol aria-label={t('ladder.label')} className="relative h-10">
        {LENGTHS.map((length, index) => {
          const n = index + 1
          const state = n < level ? 'opened' : n === level ? 'current' : 'locked'
          const label = `${String(length)}s`
          const points = formatScore(pointsForLevel(n), i18n.language)
          const last = index === LENGTHS.length - 1
          return (
            <li
              key={length}
              aria-current={state === 'current' ? 'step' : undefined}
              aria-label={t('ladder.item', { n, label, points, state: t(`ladder.${state}`) })}
              className={cn(
                'absolute top-0 flex flex-col',
                last ? '-translate-x-full items-end' : '-translate-x-1/2 items-center',
              )}
              style={{ left: `${String((length / TOTAL_SECONDS) * 100)}%` }}
            >
              <span
                aria-hidden="true"
                className={cn(
                  'num text-sm leading-5 font-semibold',
                  state === 'current' && 'text-primary',
                  state === 'opened' && 'text-muted-foreground',
                  state === 'locked' && 'text-faint',
                )}
              >
                {label}
              </span>
              <span
                aria-hidden="true"
                className={cn(
                  'num text-xs leading-4',
                  // On a phone 2 s and 5 s sit close together: only the current mark keeps its points.
                  state === 'current' ? 'text-foreground' : 'hidden text-faint sm:block',
                  state === 'opened' && 'line-through decoration-faint',
                )}
              >
                {points}
              </span>
            </li>
          )
        })}
      </ol>
    </div>
  )
}
