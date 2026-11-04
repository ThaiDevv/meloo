import { useTranslation } from 'react-i18next'
import type { RoomPlayerSummary } from '@/shared/realtime/messages'
import { Button } from '@/shared/ui/Button'
import { Dialog } from '@/shared/ui/Dialog'

interface KickDialogProps {
  /** The player the host is about to remove; null keeps the dialog closed. */
  player: RoomPlayerSummary | null
  onConfirm: (player: RoomPlayerSummary) => void
  onCancel: () => void
}

/** BR-12: a removed player cannot come back to that room, so the host confirms first. */
export function KickDialog({ player, onConfirm, onCancel }: KickDialogProps) {
  const { t } = useTranslation('room')
  return (
    <Dialog
      open={player !== null}
      onClose={onCancel}
      title={t('kick.title', { name: player?.nickname ?? '' })}
      description={t('kick.description')}
    >
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={onCancel}>
          {t('kick.cancel')}
        </Button>
        <Button
          variant="destructive"
          onClick={() => {
            if (player) onConfirm(player)
          }}
        >
          {t('kick.confirm')}
        </Button>
      </div>
    </Dialog>
  )
}
