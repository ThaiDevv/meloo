import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { Dialog } from '@/shared/ui/Dialog'
import { Input } from '@/shared/ui/Input'
import { Field } from '../../shell/fields'
import type { AdminTrack } from '../api/catalogApi'

interface Props {
  track: AdminTrack | null
  pending: boolean
  onClose: () => void
  onConfirm: (reason: string | undefined) => void
}

/** Asks for an optional reason before a track leaves new rounds (BR-27). */
export function DisableTrackDialog({ track, pending, onClose, onConfirm }: Props) {
  const { t } = useTranslation('admin')
  const [reason, setReason] = useState('')
  return (
    <Dialog
      open={track !== null}
      onClose={() => {
        setReason('')
        onClose()
      }}
      title={t('catalog.disable.title')}
      description={track ? t('catalog.disable.description', { title: track.title }) : undefined}
    >
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault()
          onConfirm(reason.trim() || undefined)
          setReason('')
        }}
      >
        <Field label={t('catalog.disable.reason')} htmlFor="disable-reason">
          <Input
            id="disable-reason"
            maxLength={200}
            value={reason}
            onChange={(e) => {
              setReason(e.target.value)
            }}
          />
        </Field>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            {t('cancel')}
          </Button>
          <Button type="submit" variant="destructive" disabled={pending}>
            {t('catalog.disable.confirm')}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
