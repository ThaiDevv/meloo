import type { ComponentProps, ReactNode } from 'react'
import { cn } from '@/shared/lib/utils'

/** Native select styled like the shared Input. */
export function Select({ className, ...props }: ComponentProps<'select'>) {
  return (
    <select
      className={cn(
        'h-11 w-full rounded-xl border border-control bg-surface px-3 text-sm text-foreground disabled:opacity-50 sm:h-10',
        className,
      )}
      {...props}
    />
  )
}

/** Label above a control; `htmlFor` ties them together. */
export function Field({
  label,
  htmlFor,
  children,
  className,
}: {
  label: string
  htmlFor: string
  children: ReactNode
  className?: string
}) {
  return (
    <div className={cn('space-y-1.5', className)}>
      <label htmlFor={htmlFor} className="block text-sm font-medium">
        {label}
      </label>
      {children}
    </div>
  )
}

/** Previous / next pager for zero-based pages. */
export function Pager({
  page,
  totalPages,
  onChange,
  labels,
}: {
  page: number
  totalPages: number
  onChange: (page: number) => void
  labels: { previous: string; next: string; status: string }
}) {
  if (totalPages <= 1) return null
  const button =
    'h-9 rounded-xl border border-control px-3.5 text-sm hover:bg-raised disabled:opacity-50'
  return (
    <div className="flex items-center justify-end gap-3 text-sm">
      <button
        type="button"
        className={button}
        disabled={page === 0}
        onClick={() => {
          onChange(page - 1)
        }}
      >
        {labels.previous}
      </button>
      <span className="text-muted-foreground tabular-nums">{labels.status}</span>
      <button
        type="button"
        className={button}
        disabled={page + 1 >= totalPages}
        onClick={() => {
          onChange(page + 1)
        }}
      >
        {labels.next}
      </button>
    </div>
  )
}
