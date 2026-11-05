import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { Dialog } from '@/shared/ui/Dialog'

interface ExitDialogProps {
  open: boolean
  busy?: boolean
  onStay: () => void
  onConfirm: () => void
}

/** Leaving always abandons the match (BR-23). Asked before a match in progress is left, from the Quit button or from any link in the app. */
export function ExitDialog({ open, busy = false, onStay, onConfirm }: ExitDialogProps) {
  const { t } = useTranslation('solo')
  return (
    <Dialog
      open={open}
      onClose={onStay}
      title={t(`play.exit.title`)}
      description={t(`play.exit.body`)}
    >
      <div className="flex flex-col gap-3 pt-2 sm:flex-row sm:justify-end">
        <Button disabled={busy} onClick={onStay}>
          {t('play.exit.stay')}
        </Button>
        <Button variant="destructive" disabled={busy} onClick={onConfirm}>
          {t(`play.exit.confirm`)}
        </Button>
      </div>
    </Dialog>
  )
}
