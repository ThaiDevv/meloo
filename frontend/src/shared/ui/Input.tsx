import type { ComponentProps } from 'react'
import { cn } from '@/shared/lib/utils'

/** Text field. Pair it with a <label>; set aria-invalid for the error state. */
export function Input({ className, type = 'text', ...props }: ComponentProps<'input'>) {
  return (
    <input
      type={type}
      className={cn(
        'h-11 w-full rounded-xl border border-control bg-surface px-3.5 text-sm text-foreground transition-colors placeholder:text-faint disabled:opacity-50 aria-[invalid=true]:border-negative sm:h-10 motion-reduce:transition-none',
        className,
      )}
      {...props}
    />
  )
}
