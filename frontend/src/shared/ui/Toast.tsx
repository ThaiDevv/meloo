import { CircleAlert, CircleCheck, Info, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import { Button } from './Button'
import { useToastStore, type ToastItem, type ToastTone } from './toast-store'

const AUTO_DISMISS_MS = 5000

const icons = { neutral: Info, positive: CircleCheck, negative: CircleAlert } as const
const iconTone: Record<ToastTone, string> = {
  neutral: 'text-muted-foreground',
  positive: 'text-positive',
  negative: 'text-negative',
}

function ToastView({ item }: { item: ToastItem }) {
  const { t } = useTranslation('common')
  const dismiss = useToastStore((s) => s.dismiss)
  const Icon = icons[item.tone]

  // WCAG 2.2.1: hovering or focusing a toast stops its countdown; leaving it starts a fresh one.
  const [paused, setPaused] = useState(false)

  useEffect(() => {
    if (paused) return
    const timer = setTimeout(() => {
      dismiss(item.id)
    }, AUTO_DISMISS_MS)
    return () => {
      clearTimeout(timer)
    }
  }, [dismiss, item.id, paused])

  return (
    <li
      onMouseEnter={() => {
        setPaused(true)
      }}
      onMouseLeave={() => {
        setPaused(false)
      }}
      onFocus={() => {
        setPaused(true)
      }}
      onBlur={() => {
        setPaused(false)
      }}
      className="motion-rise pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-2xl bg-raised p-4 shadow-xl"
    >
      <Icon
        aria-hidden="true"
        className={cn('mt-0.5 size-[18px] shrink-0', iconTone[item.tone])}
        strokeWidth={1.75}
      />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold">{item.title}</p>
        {item.description ? (
          <p className="mt-0.5 text-sm text-muted-foreground">{item.description}</p>
        ) : null}
      </div>
      <Button
        variant="ghost"
        size="sm"
        className="-my-1 -mr-2 size-9 px-0"
        aria-label={t('ui.dismiss')}
        onClick={() => {
          dismiss(item.id)
        }}
      >
        <X aria-hidden="true" className="size-4" strokeWidth={1.75} />
      </Button>
    </li>
  )
}

/** Live region for toasts. Mount once, in the app layout. */
export function Toaster() {
  const { t } = useTranslation('common')
  const toasts = useToastStore((s) => s.toasts)
  return (
    <section
      aria-label={t('ui.notifications')}
      className="pointer-events-none fixed inset-x-4 bottom-4 z-50 flex justify-end"
    >
      <ul aria-live="polite" className="flex w-full max-w-sm flex-col gap-2">
        {toasts.map((item) => (
          <ToastView key={item.id} item={item} />
        ))}
      </ul>
    </section>
  )
}
