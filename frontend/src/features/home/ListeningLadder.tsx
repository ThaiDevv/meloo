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

/** Share of the clip that each level adds: 2, then 3 more, 5 more, 10 more and 10 more seconds. */
function shareOf(index: number): number {
  const level = LEVELS[index]
  if (!level) return 0
  const previous = LEVELS[index - 1]?.seconds ?? 0
  return ((level.seconds - previous) / LONGEST) * 100
}

/**
 * One music bar cut into five parts, one per listening level, each as wide as the stretch of clip
 * it opens, with the level above it. The parts light up one after another, in step with the
 * grooves of the disc next to them; the first part is the only one in the accent colour, because
 * guessing there scores the most.
 */
export function ListeningLadder({ className }: { className?: string }) {
  const { t, i18n } = useTranslation('home')
  const number = new Intl.NumberFormat(i18n.resolvedLanguage)
  return (
    <div className={className}>
      <ol aria-label={t('ladder.label')} className="flex gap-[3px]">
        {LEVELS.map(({ seconds, points }, index) => (
          <li
            key={seconds}
            style={{ flexBasis: `${String(shareOf(index))}%` }}
            className="min-w-0 flex-none"
          >
            <span
              aria-hidden="true"
              className="num mb-2 block text-center text-sm font-semibold text-muted-foreground"
            >
              {t('ladder.marker', { seconds })}
            </span>
            <span className="sr-only">{t('ladder.step', { seconds })}</span>
            <span
              aria-hidden="true"
              className={cn(
                'animate-in fade-in slide-in-from-left-4 relative block h-12 overflow-hidden bg-foreground/15 duration-700 fill-mode-both motion-reduce:animate-none',
                index === 0 && 'rounded-l-full',
                index === LEVELS.length - 1 && 'rounded-r-full',
              )}
              style={{ animationDelay: `${String(index * 90)}ms` }}
            >
              <span
                className={cn(
                  'mv-lit absolute inset-0',
                  index === 0 ? 'bg-primary' : 'bg-foreground',
                )}
                style={{ animationDelay: levelDelay(index) }}
              />
            </span>
            <span className="sr-only">
              {number.format(points)} {t('ladder.pointsUnit')}
            </span>
          </li>
        ))}
      </ol>
      <p className="mt-4 text-sm text-muted-foreground">{t('ladder.caption')}</p>
    </div>
  )
}
