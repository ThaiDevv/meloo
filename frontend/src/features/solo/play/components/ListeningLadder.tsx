import { useTranslation } from 'react-i18next'
import { pointsForLevel } from '@/features/game-core'
import { levelDelay } from '@/shared/music-picker'
import { cn } from '@/shared/lib/utils'
import '../solo-play.css'

const LENGTHS = [2, 5, 10, 20, 30] as const

/**
 * The rule of the game before the first round: each level is a bar as long as the clip it opens,
 * next to what a right guess is still worth (BR-01, BR-02). Only 2 seconds, the best, is lit.
 */
export function ListeningLadder() {
  const { t, i18n } = useTranslation('solo')
  const number = new Intl.NumberFormat(i18n.language)

  return (
    <section aria-labelledby="ladder-heading" className="rounded-3xl bg-surface p-6 sm:p-8">
      <h2
        id="ladder-heading"
        className="font-display text-2xl leading-tight font-extrabold tracking-tight"
      >
        {t('play.intro.ladder.heading')}
      </h2>
      <ol className="mt-6 space-y-4">
        {LENGTHS.map((seconds, index) => {
          const level = index + 1
          const label = `${String(seconds)}s`
          const points = number.format(pointsForLevel(level))
          return (
            <li
              key={seconds}
              className="grid grid-cols-[2.75rem_minmax(0,1fr)_4.5rem] items-center gap-4"
            >
              <span className="sr-only">{t('play.intro.ladder.item', { label, points })}</span>
              <span aria-hidden="true" className="num font-display text-lg font-extrabold">
                {label}
              </span>
              <span aria-hidden="true" className="h-3 rounded-full bg-foreground/8">
                <span
                  className={cn(
                    'solo-grow block h-full rounded-full',
                    level === 1 ? 'bg-primary' : 'bg-foreground/35',
                  )}
                  style={{
                    width: `${String(Math.max(8, (seconds / 30) * 100))}%`,
                    animationDelay: levelDelay(index),
                  }}
                />
              </span>
              <span
                aria-hidden="true"
                className={cn(
                  'num text-right text-lg font-semibold',
                  level === 1 && 'text-primary',
                )}
              >
                {points}
              </span>
            </li>
          )
        })}
      </ol>
      <div className="mt-6 space-y-2 text-sm text-muted-foreground">
        <p>{t('play.intro.ladder.note')}</p>
        <p>{t('play.intro.ladder.combo')}</p>
      </div>
    </section>
  )
}
