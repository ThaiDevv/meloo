import type { ComponentProps } from 'react'
import { cn } from '@/shared/lib/utils'

/**
 * A flat bar at the real size of the value it stands for, so the layout does not shift when data
 * lands. Decorative: the region that loads should carry aria-busy.
 */
export function Skeleton({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div aria-hidden="true" className={cn('rounded-lg bg-foreground/10', className)} {...props} />
  )
}
