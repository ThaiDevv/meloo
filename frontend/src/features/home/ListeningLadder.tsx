import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'

/** The listening levels of a round (BR-01); the bar length is the clip length. */
const LEVELS = [2, 5, 10, 20, 30] as const
const LONGEST = 30

/**
 * The page's picture: five flat bars that grow from 2 to 30 seconds. The shortest one is the only
 * bar in the accent colour, because guessing there scores the most.
 */
export function ListeningLadder({ className }: { className?: string }) {
  const { t } = useTranslation('home')
  return (
    <ol aria-label={t('ladder.label')} className={cn('space-y-3', className)}>
      {LEVELS.map((seconds, index) => (
        <li key={seconds} className="flex items-center gap-4">
          <span className="num w-20 shrink-0 text-sm text-muted-foreground">
            {t('ladder.step', { seconds })}
          </span>
          <span aria-hidden="true" className="h-12 flex-1 overflow-hidden rounded-full bg-raised">
            <span
              className={cn(
                'animate-in fade-in slide-in-from-left-full block h-full rounded-full duration-500 fill-mode-both motion-reduce:animate-none',
                index === 0 ? 'bg-primary' : 'bg-foreground',
              )}
              style={{
                width: `${String((seconds / LONGEST) * 100)}%`,
                animationDelay: `${String(index * 90)}ms`,
              }}
            />
          </span>
        </li>
      ))}
    </ol>
  )
}
