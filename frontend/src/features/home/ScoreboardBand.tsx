import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import './home.css'

/** The five listening levels (BR-01) and the base score of each (BR-02). Shown only; the server scores. */
const ROWS = [
  { seconds: 2, points: 1000 },
  { seconds: 5, points: 800 },
  { seconds: 10, points: 600 },
  { seconds: 20, points: 400 },
  { seconds: 30, points: 200 },
] as const

/** One counter: a window one row tall over a strip of values that rolls up as the levels open. */
function Counter({ label, values }: { label: string; values: ReactNode[] }) {
  return (
    <div className="rounded-2xl bg-background p-6 sm:p-8">
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
            values={ROWS.map(({ seconds }) => `0:${String(seconds).padStart(2, '0')}`)}
          />
          <Counter
            label={t('score.points')}
            values={ROWS.map(({ points }) => number.format(points))}
          />
        </div>
      </div>
    </div>
  )
}
