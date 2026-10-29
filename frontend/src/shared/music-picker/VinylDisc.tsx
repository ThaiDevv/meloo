import { cn } from '@/shared/lib/utils'
import { levelDelay } from './levels'
import './visuals.css'

/** Inner to outer: the shortest clip (2 s) is the innermost ring, the longest (30 s) the outermost. */
const RINGS = [40, 54, 68, 82, 94]

interface VinylDiscProps {
  /** Fills the label in the accent colour instead of the text colour. */
  accent?: boolean
  spinning?: boolean
  /** Draws the tonearm; off when the disc stands in for a letter. */
  arm?: boolean
  /** Spins quickly, for "working on it". */
  fast?: boolean
  className?: string
}

/**
 * A flat record that turns slowly. Its five grooves light up one after another, matching the five
 * listening levels of a round. Decorative; drawn in the current text colour. Size it with a width.
 */
export function VinylDisc({
  accent = false,
  spinning = true,
  arm = true,
  fast = false,
  className,
}: VinylDiscProps) {
  return (
    <svg
      viewBox="0 0 200 200"
      aria-hidden="true"
      focusable="false"
      data-spinning={spinning}
      data-fast={fast}
      className={cn('mv-disc aspect-square', className)}
    >
      <g className="mv-disc-body">
        <circle cx="100" cy="100" r="98" fill="currentColor" fillOpacity="0.1" />
        {RINGS.map((radius, index) => (
          <circle
            key={radius}
            className="mv-ring"
            style={{ animationDelay: levelDelay(index) }}
            cx="100"
            cy="100"
            r={radius}
            fill="none"
            stroke="currentColor"
            strokeWidth="5"
            strokeOpacity="0.55"
          />
        ))}
        <circle cx="100" cy="100" r="28" fill={accent ? 'var(--primary)' : 'currentColor'} />
        <circle cx="100" cy="100" r="4.5" fill="var(--background)" />
      </g>
      {arm ? (
        <g className="mv-arm">
          <path
            d="M176 24 L134 116"
            stroke="currentColor"
            strokeOpacity="0.7"
            strokeWidth="5"
            strokeLinecap="round"
          />
          <circle cx="176" cy="24" r="9" fill="currentColor" fillOpacity="0.55" />
          <rect
            x="124"
            y="112"
            width="16"
            height="9"
            rx="2"
            transform="rotate(24 132 116)"
            fill="currentColor"
          />
        </g>
      ) : null}
    </svg>
  )
}
