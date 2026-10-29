import type { CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import './home.css'

/** The five listening levels (BR-01); how many blocks fall in each lane is only for looks. */
const LANES = [
  { seconds: 2, blocks: 4 },
  { seconds: 5, blocks: 3 },
  { seconds: 10, blocks: 3 },
  { seconds: 20, blocks: 2 },
  { seconds: 30, blocks: 1 },
] as const
const LONGEST = 30
/** Distance covered per second, in percent of the lane height, so long and short blocks fall alike. */
const SPEED = 30

interface BlockStyle extends CSSProperties {
  '--mh-dur': string
  '--mh-delay': string
}

/**
 * The page's one loud moment, between the hero and the modes: blocks fall down five lanes onto
 * their pads, each as long as the clip of its listening level. The sentence above is real text; the
 * lanes are decoration, so they are hidden from assistive technology.
 */
export function LanesBand() {
  const { t } = useTranslation('home')
  return (
    <div className="bg-surface">
      <div className="mx-auto w-full max-w-6xl px-4 py-12 lg:py-16">
        <p className="max-w-xl text-lg">{t('lanes.prompt')}</p>
        <div aria-hidden="true" className="mt-8 select-none">
          <div className="flex h-60 gap-2 sm:h-72 sm:gap-3">
            {LANES.map(({ seconds, blocks }, lane) => {
              const length = 14 + (seconds / LONGEST) * 36
              const duration = (100 + length) / SPEED
              return (
                <div
                  key={seconds}
                  className="mh-lanes relative flex-1 overflow-hidden rounded-2xl bg-foreground/[0.05]"
                >
                  <span className="absolute inset-x-1.5 bottom-1 h-3 rounded-full bg-foreground/15" />
                  {Array.from({ length: blocks }, (_, block) => {
                    const delay = -(block * (duration / blocks) + lane * 0.45)
                    const style: BlockStyle = {
                      '--mh-dur': `${duration.toFixed(2)}s`,
                      '--mh-delay': `${delay.toFixed(2)}s`,
                    }
                    return (
                      <div key={block} className="absolute inset-0" style={style}>
                        <span className="mh-flash absolute inset-x-1.5 bottom-1 h-3 rounded-full bg-primary" />
                        <span
                          className={cn(
                            'mh-block absolute inset-x-1.5 top-0 rounded-xl',
                            lane === 0 ? 'bg-primary' : 'bg-foreground',
                          )}
                          style={{ height: `${length.toFixed(1)}cqh` }}
                        />
                      </div>
                    )
                  })}
                </div>
              )
            })}
          </div>
          <div className="num mt-3 flex gap-2 text-sm font-semibold sm:gap-3">
            {LANES.map(({ seconds }) => (
              <span key={seconds} className="flex-1 text-center">
                {t('lanes.marker', { seconds })}
              </span>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
