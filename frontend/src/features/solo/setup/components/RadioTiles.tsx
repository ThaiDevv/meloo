import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/utils'

interface RadioTilesProps<T extends string | number> {
  legend: string
  name: string
  value: T
  onChange: (value: T) => void
  options: {
    value: T
    title: string
    description?: string
    /** Decorative, aria-hidden, drawn in the tile's text colour (top right). */
    icon?: ReactNode
    badge?: ReactNode
  }[]
  /** Tiles share a row on wide screens; `compact` keeps short options on one line even at 360px. */
  compact?: boolean
}

/** Native radios dressed as flat tiles: arrow keys, labels and form semantics come for free. */
export function RadioTiles<T extends string | number>({
  legend,
  name,
  value,
  onChange,
  options,
  compact = false,
}: RadioTilesProps<T>) {
  return (
    <fieldset className="min-w-0 space-y-3">
      <legend className="text-base font-semibold">{legend}</legend>
      <div className={cn('grid gap-2', compact ? 'grid-cols-3' : 'sm:grid-cols-2')}>
        {options.map((option) => (
          <label
            key={option.value}
            className="mv-tile relative flex cursor-pointer flex-col justify-center gap-1 rounded-2xl bg-raised px-5 py-4 text-foreground transition-[background-color,transform] duration-150 hover:-translate-y-0.5 has-checked:bg-primary has-checked:text-primary-foreground has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-ring has-disabled:cursor-not-allowed has-disabled:opacity-50 motion-reduce:transition-none motion-reduce:hover:translate-y-0"
          >
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={option.value === value}
              onChange={() => {
                onChange(option.value)
              }}
              className="sr-only"
            />
            {option.icon ? (
              <span
                aria-hidden="true"
                className="absolute top-4 right-4 opacity-60 transition-transform duration-200 [label:has(:checked)_&]:opacity-100"
              >
                {option.icon}
              </span>
            ) : null}
            <span className={cn('font-semibold', compact ? 'num text-center text-2xl' : 'text-lg')}>
              {option.title}
            </span>
            {option.description ? <span className="text-sm">{option.description}</span> : null}
            {option.badge}
          </label>
        ))}
      </div>
    </fieldset>
  )
}
