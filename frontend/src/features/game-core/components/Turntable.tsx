import { useEffect, useRef } from 'react'
import { cn } from '@/shared/lib/utils'
import type { SnippetPlayer } from '../audio/snippetPlayer'
import { levelLabel } from '../lib/levels'
import { motionAllowed, useFrameLoop } from '../lib/motion'
import '../game-core.css'

// Drawing units of the 340 x 300 view box.
const CX = 150
const CY = 150
const DISC_R = 112
const LABEL_R = 34
/** Level 1 (the shortest clip) is the innermost groove, level 5 the outermost. */
const LEVEL_RADII = [52, 65, 78, 91, 104]
const TEXTURE_RADII = Array.from({ length: 20 }, (_, i) => 40 + i * 3.6)
/** The spectrum: bars standing on a ring just outside the record. */
const BARS = 72
const BAR_FROM = 119
const BAR_IDLE = 2.5
const BAR_MAX = 19
const ARM_PIVOT = '306px 44px'
const ARM_PARKED = 3
const ARM_PLAYING = 24

interface TurntableProps {
  /** Opened level, 1 to 5. */
  level: number
  /** Length of the opened level in seconds, printed on the label. */
  listenSeconds: number
  playing: boolean
  /** Read every frame while playing, to draw the spectrum of what is sounding. */
  player: SnippetPlayer
  /** Changes on each wrong guess: the record skips. */
  joltKey?: number
  className?: string
}

/**
 * The round as a record on a turntable. The five grooves are the five listening levels; the arm
 * drops when the clip plays and the ring of bars around the record moves with its real spectrum.
 * Decorative: the strip and the status line carry the same facts in words.
 */
export function Turntable({
  level,
  listenSeconds,
  playing,
  player,
  joltKey,
  className,
}: TurntableProps) {
  const rootRef = useRef<HTMLDivElement>(null)
  const barRefs = useRef<(SVGLineElement | null)[]>([])
  const bins = useRef<Uint8Array<ArrayBuffer> | null>(null)
  const animate = playing && motionAllowed()

  useFrameLoop(animate, () => {
    const analyser = player.analyserNode
    if (!analyser) return
    if (bins.current?.length !== analyser.frequencyBinCount) {
      bins.current = new Uint8Array(analyser.frequencyBinCount)
    }
    const data = bins.current
    analyser.getByteFrequencyData(data)
    drawBars(barRefs.current, (band) => {
      // Low bands are where most music lives, so the ring gives them more bars.
      const bin = Math.min(
        data.length - 1,
        Math.round(1 + (band / (BARS / 2)) ** 1.6 * data.length * 0.7),
      )
      const value = (data[bin] ?? 0) / 255
      return BAR_IDLE + value ** 1.3 * BAR_MAX
    })
  })

  // When the clip stops, the bars settle back into a dotted ring.
  useEffect(() => {
    if (!animate) drawBars(barRefs.current, () => BAR_IDLE)
  }, [animate])

  useEffect(() => {
    const root = rootRef.current
    if (joltKey === undefined || !root || !motionAllowed() || !('animate' in root)) return
    root.animate(
      [
        { transform: 'translateX(0)' },
        { transform: 'translateX(-7px) rotate(-1.5deg)' },
        { transform: 'translateX(6px) rotate(1deg)' },
        { transform: 'translateX(-3px)' },
        { transform: 'translateX(0)' },
      ],
      { duration: 360, easing: 'cubic-bezier(0.36, 0.07, 0.19, 0.97)' },
    )
  }, [joltKey])

  return (
    <div
      ref={rootRef}
      className={cn('gc-turntable aspect-[34/30]', className)}
      data-playing={animate}
    >
      <svg viewBox="0 0 340 300" aria-hidden="true" focusable="false" className="size-full">
        <g className="gc-spectrum" data-playing={playing}>
          {Array.from({ length: BARS }, (_, i) => {
            const [x1, y1] = polar(i, BAR_FROM)
            const [x2, y2] = polar(i, BAR_FROM + BAR_IDLE)
            return (
              <line
                key={i}
                ref={(el) => {
                  barRefs.current[i] = el
                }}
                x1={x1}
                y1={y1}
                x2={x2}
                y2={y2}
                strokeWidth={3.2}
                strokeLinecap="round"
              />
            )
          })}
        </g>

        <circle cx={CX} cy={CY} r={DISC_R} className="fill-background" />
        <g className="gc-platter">
          {TEXTURE_RADII.map((radius) => (
            <circle
              key={radius}
              cx={CX}
              cy={CY}
              r={radius}
              fill="none"
              stroke="var(--foreground)"
              strokeOpacity={0.05}
              strokeWidth={1}
            />
          ))}
          {LEVEL_RADII.map((radius, index) => {
            const n = index + 1
            const state = n < level ? 'opened' : n === level ? 'current' : 'locked'
            return (
              <circle
                key={radius}
                className="gc-ring"
                data-state={state}
                cx={CX}
                cy={CY}
                r={radius}
                fill="none"
                pathLength={100}
                strokeDasharray={state === 'locked' ? '1 3' : '96 4'}
                strokeWidth={state === 'current' ? 6 : state === 'opened' ? 4 : 3}
                stroke={state === 'locked' ? 'var(--foreground)' : 'var(--primary)'}
                strokeOpacity={state === 'locked' ? 0.18 : state === 'opened' ? 0.5 : 1}
                transform={`rotate(${String(index * 67)} ${String(CX)} ${String(CY)})`}
              />
            )
          })}
          <circle cx={CX} cy={CY} r={LABEL_R} className="fill-primary" />
          <circle cx={CX} cy={CY - 26} r={2.5} className="fill-primary-foreground" />
        </g>
        {/* Light on vinyl stays put while the record turns. */}
        <path d={sheen(-62, -28)} className="fill-foreground" fillOpacity={0.05} />
        <path d={sheen(118, 152)} className="fill-foreground" fillOpacity={0.035} />
        <text
          x={CX}
          y={CY + 1}
          textAnchor="middle"
          dominantBaseline="central"
          className="num fill-primary-foreground font-display text-[22px] font-extrabold"
        >
          {levelLabel(listenSeconds)}
        </text>

        <g
          className="gc-arm"
          style={{
            transform: `rotate(${String(playing ? ARM_PLAYING : ARM_PARKED)}deg)`,
            transformOrigin: ARM_PIVOT,
          }}
        >
          <rect x={297} y={10} width={18} height={20} rx={4} className="fill-faint" />
          <line
            x1={306}
            y1={30}
            x2={306}
            y2={204}
            stroke="var(--muted-foreground)"
            strokeWidth={5}
            strokeLinecap="round"
          />
          <path d="M301 200 L311 200 L304 228 L294 228 Z" className="fill-muted-foreground" />
          <circle cx={306} cy={44} r={13} className="fill-raised" />
          <circle cx={306} cy={44} r={5} className="fill-faint" />
        </g>
      </svg>
    </div>
  )
}

/** Point at `radius` on the angle of spectrum bar `index`, starting at the top, clockwise. */
function polar(index: number, radius: number): [number, number] {
  const angle = (index / BARS) * Math.PI * 2 - Math.PI / 2
  return [CX + Math.cos(angle) * radius, CY + Math.sin(angle) * radius]
}

/** Sets each bar's length; the ring is mirrored left to right, so band 0 sits at the top. */
function drawBars(bars: readonly (SVGLineElement | null)[], length: (band: number) => number) {
  bars.forEach((bar, index) => {
    if (!bar) return
    const band = index < BARS / 2 ? index : BARS - 1 - index
    const [x2, y2] = polar(index, BAR_FROM + length(band))
    bar.setAttribute('x2', x2.toFixed(1))
    bar.setAttribute('y2', y2.toFixed(1))
  })
}

/** A flat wedge of reflected light between two angles (degrees), across the grooves. */
function sheen(fromDeg: number, toDeg: number): string {
  const point = (deg: number, radius: number) => {
    const angle = (deg * Math.PI) / 180
    return `${(CX + Math.cos(angle) * radius).toFixed(1)} ${(CY + Math.sin(angle) * radius).toFixed(1)}`
  }
  const inner = LABEL_R + 6
  const outer = DISC_R - 4
  return `M${point(fromDeg, inner)} L${point(fromDeg, outer)} A${String(outer)} ${String(outer)} 0 0 1 ${point(toDeg, outer)} L${point(toDeg, inner)} A${String(inner)} ${String(inner)} 0 0 0 ${point(fromDeg, inner)} Z`
}
