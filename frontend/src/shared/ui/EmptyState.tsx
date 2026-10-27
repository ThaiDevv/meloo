import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/utils'

interface EmptyStateProps {
  title: string
  description?: string
  /** The next step, usually a <Button>. */
  action?: ReactNode
  className?: string
}

/** An empty section keeps its frame: one sentence and, when there is one, the first action. */
export function EmptyState({ title, description, action, className }: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center gap-3 rounded-2xl border border-dashed border-control px-6 py-10 text-center',
        className,
      )}
    >
      <p className="text-base font-semibold">{title}</p>
      {description ? <p className="max-w-sm text-sm text-muted-foreground">{description}</p> : null}
      {action}
    </div>
  )
}
