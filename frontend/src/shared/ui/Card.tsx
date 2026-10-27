import type { ComponentProps } from 'react'
import { cn } from '@/shared/lib/utils'

// A surface one tone step off the ground with a 1px edge: border or shadow, never both.
export function Card({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('rounded-2xl border border-border bg-surface', className)} {...props} />
}

export function CardHeader({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('space-y-1 p-5 pb-0', className)} {...props} />
}

/** Renders an <h3>; pass `as` when the card sits under a different heading level. */
export function CardTitle({
  as: Tag = 'h3',
  className,
  ...props
}: ComponentProps<'h3'> & { as?: 'h2' | 'h3' | 'h4' }) {
  return <Tag className={cn('text-base font-semibold', className)} {...props} />
}

export function CardDescription({ className, ...props }: ComponentProps<'p'>) {
  return <p className={cn('text-sm text-muted-foreground', className)} {...props} />
}

export function CardContent({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('p-5', className)} {...props} />
}
