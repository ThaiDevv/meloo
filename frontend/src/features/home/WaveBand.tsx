import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import './home.css'

/** The five listening levels (BR-01) as a share of the longest clip. */
const LEVELS = [2, 5, 10, 20, 30] as const
const LONGEST = 30
const BAR_COUNT = 72
/** Bars that fall inside the first level, drawn in the accent colour. */
const FIRST_LEVEL_BARS = Math.round((LEVELS[0] / LONGEST) * BAR_COUNT)

/** A made-up waveform, the same on every render: two sine waves under a soft envelope. */
const HEIGHTS = Array.from({ length: BAR_COUNT }, (_, i) => {
  const wave = Math.abs(Math.sin(i * 0.9) * Math.sin(i * 0.23 + 1.2))
  const envelope = 0.55 + 0.45 * Math.sin((i / BAR_COUNT) * Math.PI)
  return Math.round((0.18 + 0.82 * wave) * envelope * 100)
})

/**
 * One copy of the wave with its time marks. The dim copy is the whole clip; the lit copy is shown
 * through a window that opens one level at a time.
 */
function WaveLayer({ lit }: { lit: boolean }) {
  const { t } = useTranslation('home')
  return (
    <div
      className={cn(
        'absolute inset-0 flex flex-col',
        lit ? 'text-foreground' : 'text-foreground/20',
      )}
    >
      <div className="flex flex-1 items-end gap-[3px]">
        {HEIGHTS.map((height, index) => (
          <span
            key={index}
            className={cn(
              'mh-bar flex-1 rounded-full bg-current',
              lit && index < FIRST_LEVEL_BARS && 'text-primary',
              lit && 'mh-bar-live',
            )}
            style={{ height: `${String(height)}%`, animationDelay: `${String(index * -90)}ms` }}
          />
        ))}
      </div>
      <div className="relative h-9">
        {LEVELS.map((seconds) => (
          <span
            key={seconds}
            className="num absolute top-2.5 text-sm font-semibold"
            style={{
              left: `${String((seconds / LONGEST) * 100)}%`,
              translate: '-100% 0',
              paddingRight: '0.5rem',
            }}
          >
            {t('wave.marker', { seconds })}
          </span>
        ))}
      </div>
    </div>
  )
}

/**
 * The page's one loud moment, between the hero and the modes: the clip being unlocked level by
 * level. Decorative (the ladder above already says it in words), so it is hidden from assistive tech.
 */
export function WaveBand() {
  return (
    <div
      aria-hidden="true"
      className="mh-wave relative h-44 overflow-hidden bg-surface select-none sm:h-52"
    >
      <WaveLayer lit={false} />
      <div className="mh-reveal absolute inset-0 overflow-hidden">
        <div className="mh-reveal-inner absolute inset-0">
          <WaveLayer lit />
        </div>
      </div>
      <div className="mh-head pointer-events-none absolute inset-0">
        <span className="absolute inset-y-0 right-0 w-[3px] bg-primary" />
        <span className="mh-ripple absolute top-[38%] right-0 size-72 -translate-y-1/2 translate-x-1/2 rounded-full border-2 border-primary" />
      </div>
    </div>
  )
}
