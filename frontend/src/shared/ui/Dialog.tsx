import { X } from 'lucide-react'
import { useEffect, useId, useRef, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from './Button'

interface DialogProps {
  open: boolean
  /** Called on Esc, on the close button and on a click outside; the owner sets `open` to false. */
  onClose: () => void
  title: string
  description?: string
  children?: ReactNode
}

/**
 * Modal built on the native <dialog>: the browser traps focus, closes on Esc, marks the rest of the
 * page inert and returns focus to the opener.
 */
export function Dialog({ open, onClose, title, description, children }: DialogProps) {
  const { t } = useTranslation('common')
  const ref = useRef<HTMLDialogElement>(null)
  const id = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      aria-labelledby={`${id}-title`}
      aria-describedby={description ? `${id}-description` : undefined}
      onClose={onClose}
      onClick={(event) => {
        // The backdrop belongs to the <dialog> itself; the panel inside fills the rest.
        if (event.target === event.currentTarget) onClose()
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-md rounded-2xl bg-surface p-0 text-foreground shadow-2xl backdrop:bg-black/60"
    >
      <div className="motion-rise space-y-4 p-6">
        <div className="flex items-start justify-between gap-4">
          <h2 id={`${id}-title`} className="text-lg font-semibold">
            {title}
          </h2>
          <Button
            variant="ghost"
            size="icon"
            className="-mt-2 -mr-2"
            aria-label={t('ui.close')}
            onClick={onClose}
          >
            <X aria-hidden="true" className="size-[18px]" strokeWidth={1.75} />
          </Button>
        </div>
        {description ? (
          <p id={`${id}-description`} className="text-sm text-muted-foreground">
            {description}
          </p>
        ) : null}
        {children}
      </div>
    </dialog>
  )
}
