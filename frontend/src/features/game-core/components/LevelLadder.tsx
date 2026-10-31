import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import { pointsForLevel } from '../lib/levels'

/** The listening lengths, in order. The server confirms the opened one; the rest are the fixed BR-01 steps. */
const LENGTHS = [2, 5, 10, 20, 30]

interface LevelLadderProps {
  level: number
  className?: string
}

/** Five steps of the round: each shows its length and what a right guess is worth there (BR-01, BR-02). */
export function LevelLadder({ level, className }: LevelLadderProps) {
  const { t } = useTranslation('game')
  return (
    <ol aria-label={t('ladder.label')} className={cn('grid grid-cols-5 gap-1.5', className)}>
      {LENGTHS.map((length, index) => {
        const n = index + 1
        const state = n < level ? 'opened' : n === level ? 'current' : 'locked'
        const label = `${String(length)}s`
        const points = pointsForLevel(n)
        return (
          <li
            key={length}
            aria-current={state === 'current' ? 'step' : undefined}
            aria-label={t('ladder.item', {
              n,
              label,
              points,
              state: t(`ladder.${state}`),
            })}
            className="flex flex-col gap-2"
          >
            <span
              aria-hidden="true"
              className={cn(
                'h-1.5 rounded-full',
                state === 'locked' && 'bg-foreground/15',
                state === 'opened' && 'bg-primary/55',
                state === 'current' && 'bg-primary',
              )}
            />
            <span
              aria-hidden="true"
              className={cn(
                'num text-sm font-semibold',
                state === 'current' ? 'text-foreground' : 'text-faint',
              )}
            >
              {label}
            </span>
            <span
              aria-hidden="true"
              className={cn(
                'num -mt-1.5 text-xs',
                state === 'current' ? 'text-muted-foreground' : 'text-faint',
              )}
            >
              {points}
            </span>
          </li>
        )
      })}
    </ol>
  )
}
