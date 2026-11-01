import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { Dialog } from '@/shared/ui/Dialog'

interface ExitDialogProps {
  open: boolean
  /** Ranked: leaving keeps the match for 30 minutes. Practice: leaving abandons it (BR-23). */
  ranked: boolean
  busy?: boolean
  onStay: () => void
  onConfirm: () => void
}

/** Asked before a match in progress is left, from the Quit button or from any link in the app. */
export function ExitDialog({ open, ranked, busy = false, onStay, onConfirm }: ExitDialogProps) {
  const { t } = useTranslation('solo')
  const kind = ranked ? 'ranked' : 'practice'
  return (
    <Dialog
      open={open}
      onClose={onStay}
      title={t(`play.exit.${kind}.title`)}
      description={t(`play.exit.${kind}.body`)}
    >
      <div className="flex flex-col gap-3 pt-2 sm:flex-row sm:justify-end">
        <Button disabled={busy} onClick={onStay}>
          {t('play.exit.stay')}
        </Button>
        <Button variant="destructive" disabled={busy} onClick={onConfirm}>
          {t(`play.exit.${kind}.confirm`)}
        </Button>
      </div>
    </Dialog>
  )
}
