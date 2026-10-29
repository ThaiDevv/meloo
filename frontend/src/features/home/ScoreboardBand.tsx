import { Coins, Timer } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import './home.css'

/** The five listening levels (BR-01) and the base score of each (BR-02). Shown only; the server scores. */
const ROWS = [
  { seconds: 2, points: 1000 },
  { seconds: 5, points: 800 },
  { seconds: 10, points: 600 },
  { seconds: 20, points: 400 },
  { seconds: 30, points: 200 },
] as const

/** Five pips under a counter: `fill` lights one more at every level, `drain` puts one out. */
function Pips({ mode }: { mode: 'fill' | 'drain' }) {
  return (
    <div className="mt-6 flex gap-2">
      {[1, 2, 3, 4, 5].map((pip) => (
        <span key={pip} className="relative size-3 rounded-full bg-foreground/10">
          <span
            className={cn(
              'mh-pip absolute inset-0 rounded-full bg-primary',
              pip > 1 && `mh-pip-${mode}-${String(pip)}`,
            )}
          />
        </span>
      ))}
    </div>
  )
}

/** One counter: a window one row tall over a strip of values that rolls up as the levels open. */
function Counter({
  label,
  icon,
  values,
  pips,
}: {
  label: string
  icon: ReactNode
  values: ReactNode[]
  pips: 'fill' | 'drain'
}) {
  return (
    <div className="relative overflow-hidden rounded-2xl bg-background p-6 sm:p-8">
      <span className="absolute top-6 right-6 text-muted-foreground sm:top-8 sm:right-8">
        {icon}
      </span>
      <p className="text-base text-muted-foreground">{label}</p>
      <div className="mt-3 h-[1em] overflow-hidden font-display text-[clamp(3.5rem,11vw,8rem)] leading-none font-extrabold tracking-[-0.04em]">
        <div className="mh-roll num">
          {values.map((value, index) => (
            <div key={index} className="h-[1em] leading-none">
              {value}
            </div>
          ))}
        </div>
      </div>
      <Pips mode={pips} />
    </div>
  )
}

/**
 * The page's one loud moment, between the hero and the modes: a scoreboard whose time goes up while
 * the score goes down. The sentence above is real text; the counters are decoration (the ladder
 * above already gives every number in words), so they are hidden from assistive technology.
 */
export function ScoreboardBand() {
  const { t, i18n } = useTranslation('home')
  const number = new Intl.NumberFormat(i18n.resolvedLanguage)
  return (
    <div className="bg-surface">
      <div className="mx-auto w-full max-w-6xl px-4 py-12 lg:py-16">
        <p className="max-w-xl text-lg">{t('score.prompt')}</p>
        <div aria-hidden="true" className="mt-8 grid gap-3 select-none sm:grid-cols-2">
          <Counter
            label={t('score.heard')}
            icon={<Timer className="size-6" strokeWidth={1.75} />}
            pips="fill"
            values={ROWS.map(({ seconds }) => `0:${String(seconds).padStart(2, '0')}`)}
          />
          <Counter
            label={t('score.points')}
            icon={<Coins className="size-6" strokeWidth={1.75} />}
            pips="drain"
            values={ROWS.map(({ points }) => number.format(points))}
          />
        </div>
      </div>
    </div>
  )
}
