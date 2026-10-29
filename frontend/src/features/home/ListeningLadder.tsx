import { useTranslation } from 'react-i18next'
import { levelDelay } from '@/shared/music-picker'
import '@/shared/music-picker/visuals.css'
import { cn } from '@/shared/lib/utils'

/**
 * The five listening levels of a round (BR-01) and the base score for guessing right at each one
 * (BR-02). Shown for information only: the server alone decides every score.
 */
const LEVELS = [
  { seconds: 2, points: 1000 },
  { seconds: 5, points: 800 },
  { seconds: 10, points: 600 },
  { seconds: 20, points: 400 },
  { seconds: 30, points: 200 },
] as const
const LONGEST = 30

/**
 * Five flat bars that grow from 2 to 30 seconds and light up one after another, in step with the
 * grooves of the disc next to them. The shortest bar is the only one in the accent colour, because
 * guessing there scores the most.
 */
export function ListeningLadder({ className }: { className?: string }) {
  const { t, i18n } = useTranslation('home')
  const number = new Intl.NumberFormat(i18n.resolvedLanguage)
  return (
    <div className={className}>
      <ol aria-label={t('ladder.label')} className="space-y-3">
        {LEVELS.map(({ seconds, points }, index) => (
          <li
            key={seconds}
            className="grid grid-cols-[4.5rem_minmax(0,1fr)_3.5rem] items-center gap-3"
          >
            <span className="num text-sm text-muted-foreground">
              {t('ladder.step', { seconds })}
            </span>
            <span aria-hidden="true" className="h-12 overflow-hidden rounded-full bg-raised">
              <span
                className="animate-in fade-in slide-in-from-left-full block h-full duration-700 fill-mode-both motion-reduce:animate-none"
                style={{
                  width: `${String((seconds / LONGEST) * 100)}%`,
                  animationDelay: `${String(index * 90)}ms`,
                }}
              >
                <span
                  className={cn(
                    'mv-lit block h-full rounded-full',
                    index === 0 ? 'bg-primary' : 'bg-foreground',
                  )}
                  style={{ animationDelay: levelDelay(index) }}
                />
              </span>
            </span>
            <span className="num text-right text-sm font-semibold">
              {number.format(points)}
              <span className="sr-only"> {t('ladder.pointsUnit')}</span>
            </span>
          </li>
        ))}
      </ol>
      <p className="mt-4 text-sm text-muted-foreground">{t('ladder.caption')}</p>
    </div>
  )
}
