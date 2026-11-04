import { X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { ErrorMessage } from '@/shared/realtime/messages'
import { Button } from '@/shared/ui/Button'
import { errorText } from '../model/errorText'

/** The answer to a command the server refused, until the player dismisses it. */
export function LobbyAlert({ error, onDismiss }: { error: ErrorMessage; onDismiss: () => void }) {
  const { t } = useTranslation('room')
  return (
    <div
      role="alert"
      className="flex items-start gap-3 rounded-xl bg-negative-soft px-4 py-3 text-sm text-negative"
    >
      <p className="flex-1">{errorText(error, t)}</p>
      <Button
        variant="ghost"
        size="icon"
        className="-my-2 -mr-2 size-9 text-negative sm:size-9"
        aria-label={t('alert.dismiss')}
        onClick={onDismiss}
      >
        <X aria-hidden="true" className="size-4" strokeWidth={1.75} />
      </Button>
    </div>
  )
}
