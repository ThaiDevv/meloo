import {
  createContext,
  useContext,
  useId,
  useState,
  type ComponentProps,
  type KeyboardEvent,
} from 'react'
import { cn } from '@/shared/lib/utils'

interface TabsContext {
  value: string
  select: (value: string) => void
  idBase: string
}

const TabsCtx = createContext<TabsContext | null>(null)

function useTabs(): TabsContext {
  const ctx = useContext(TabsCtx)
  if (!ctx) throw new Error('Tabs parts must be rendered inside <Tabs>')
  return ctx
}

interface TabsProps extends Omit<ComponentProps<'div'>, 'defaultValue' | 'onChange'> {
  defaultValue: string
  onValueChange?: (value: string) => void
}

/** WAI-ARIA tabs with automatic activation: arrows, Home and End move focus and select. */
export function Tabs({ defaultValue, onValueChange, className, ...props }: TabsProps) {
  const [value, setValue] = useState(defaultValue)
  const idBase = useId()
  const select = (next: string) => {
    setValue(next)
    onValueChange?.(next)
  }
  return (
    <TabsCtx.Provider value={{ value, select, idBase }}>
      <div className={className} {...props} />
    </TabsCtx.Provider>
  )
}

export function TabsList({
  className,
  onKeyDown,
  ...props
}: ComponentProps<'div'> & { 'aria-label': string }) {
  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    onKeyDown?.(event)
    const tabs = [
      ...event.currentTarget.querySelectorAll<HTMLElement>('[role="tab"]:not(:disabled)'),
    ]
    const index = tabs.indexOf(document.activeElement as HTMLElement)
    if (index < 0) return
    const target = {
      ArrowRight: tabs[(index + 1) % tabs.length],
      ArrowLeft: tabs[(index - 1 + tabs.length) % tabs.length],
      Home: tabs[0],
      End: tabs[tabs.length - 1],
    }[event.key]
    if (!target) return
    event.preventDefault()
    target.focus()
    target.click()
  }
  return (
    <div
      role="tablist"
      onKeyDown={handleKeyDown}
      className={cn('flex gap-1 overflow-x-auto rounded-full bg-raised p-1', className)}
      {...props}
    />
  )
}

export function TabsTrigger({
  value,
  className,
  ...props
}: Omit<ComponentProps<'button'>, 'value'> & { value: string }) {
  const ctx = useTabs()
  const selected = ctx.value === value
  return (
    <button
      type="button"
      role="tab"
      id={`${ctx.idBase}-tab-${value}`}
      aria-selected={selected}
      aria-controls={`${ctx.idBase}-panel-${value}`}
      tabIndex={selected ? 0 : -1}
      onClick={() => {
        ctx.select(value)
      }}
      className={cn(
        'h-9 shrink-0 rounded-full px-4 text-sm font-medium transition-colors motion-reduce:transition-none',
        selected ? 'bg-surface text-foreground' : 'text-muted-foreground hover:text-foreground',
        className,
      )}
      {...props}
    />
  )
}

export function TabsContent({
  value,
  className,
  ...props
}: Omit<ComponentProps<'div'>, 'value'> & { value: string }) {
  const ctx = useTabs()
  if (ctx.value !== value) return null
  return (
    <div
      role="tabpanel"
      id={`${ctx.idBase}-panel-${value}`}
      aria-labelledby={`${ctx.idBase}-tab-${value}`}
      tabIndex={0}
      className={cn('mt-4', className)}
      {...props}
    />
  )
}
