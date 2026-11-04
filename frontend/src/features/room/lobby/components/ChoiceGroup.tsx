import { cn } from '@/shared/lib/utils'

interface ChoiceGroupProps<T extends string | number> {
  legend: string
  name: string
  value: T
  onChange: (value: T) => void
  options: { value: T; title: string; description?: string; disabled?: boolean }[]
  /** Short options (numbers) sit side by side even on a 360 px screen. */
  compact?: boolean
  disabled?: boolean
  /** Hides the legend visually but keeps it for screen readers (when a heading already says it). */
  hideLegend?: boolean
}

/** Native radios dressed as flat tiles: arrow keys, labels and form semantics come for free. */
export function ChoiceGroup<T extends string | number>({
  legend,
  name,
  value,
  onChange,
  options,
  compact = false,
  disabled = false,
  hideLegend = false,
}: ChoiceGroupProps<T>) {
  return (
    <fieldset disabled={disabled} className="min-w-0 space-y-3">
      <legend className={cn('text-base font-semibold', hideLegend && 'sr-only')}>{legend}</legend>
      <div
        className={cn(
          'grid gap-2',
          compact ? 'grid-cols-[repeat(auto-fit,minmax(4.5rem,1fr))]' : 'sm:grid-cols-2',
        )}
      >
        {options.map((option) => (
          <label
            key={option.value}
            className="mv-tile relative flex cursor-pointer flex-col justify-center gap-1 rounded-2xl bg-raised px-5 py-3.5 text-foreground transition-[background-color,transform] duration-150 hover:-translate-y-0.5 has-checked:bg-primary has-checked:text-primary-foreground has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-ring has-disabled:cursor-not-allowed has-disabled:opacity-50 has-disabled:hover:translate-y-0 motion-reduce:transition-none motion-reduce:hover:translate-y-0"
          >
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={option.value === value}
              disabled={option.disabled}
              onChange={() => {
                onChange(option.value)
              }}
              className="sr-only"
            />
            <span className={cn('font-semibold', compact ? 'num text-center text-xl' : 'text-lg')}>
              {option.title}
            </span>
            {option.description ? <span className="text-sm">{option.description}</span> : null}
          </label>
        ))}
      </div>
    </fieldset>
  )
}
