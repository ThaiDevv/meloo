import { cn } from '@/shared/lib/utils'
import { levelLabel } from '../lib/levels'
import '../game-core.css'

/** Inner to outer: level 1 (the shortest clip) is the innermost groove, level 5 the outermost. */
const RADII = [36, 52, 68, 82, 95]

interface LevelDiscProps {
  /** Opened level, 1 to 5. */
  level: number
  /** Length of the opened level in seconds. */
  listenSeconds: number
  playing: boolean
  className?: string
}

/**
 * The round as a record: its five grooves are the five listening levels. Opened grooves are lit in
 * the accent, the current one breathes while the clip plays. Decorative; the ladder beside it
 * carries the same state in words.
 */
export function LevelDisc({ level, listenSeconds, playing, className }: LevelDiscProps) {
  return (
    <div className={cn('gc-disc relative aspect-square', className)} data-playing={playing}>
      <svg viewBox="0 0 200 200" aria-hidden="true" focusable="false" className="size-full">
        <circle cx="100" cy="100" r="99" className="fill-raised" />
        <g className="gc-disc-spin">
          {RADII.map((radius, index) => {
            const n = index + 1
            const state = n < level ? 'opened' : n === level ? 'current' : 'locked'
            return (
              <circle
                key={radius}
                className="gc-ring"
                data-state={state}
                cx="100"
                cy="100"
                r={radius}
                fill="none"
                pathLength={100}
                strokeDasharray="95 5"
                strokeLinecap="butt"
                strokeWidth={state === 'current' ? 7 : 5}
                stroke={state === 'locked' ? 'var(--faint)' : 'var(--primary)'}
                strokeOpacity={state === 'locked' ? 0.35 : state === 'opened' ? 0.55 : 1}
                transform={`rotate(${String(index * 72)} 100 100)`}
              />
            )
          })}
        </g>
      </svg>
      <div className="absolute inset-0 flex items-center justify-center">
        <span className="num font-display text-4xl font-extrabold tracking-tight sm:text-5xl">
          {levelLabel(listenSeconds)}
        </span>
      </div>
    </div>
  )
}
