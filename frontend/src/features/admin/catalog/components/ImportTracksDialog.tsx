import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { Dialog } from '@/shared/ui/Dialog'
import { Input } from '@/shared/ui/Input'
import { adminErrorKey } from '../../shell/adminApi'
import { Field, Select } from '../../shell/fields'
import { useImportTracks, type AdminTrackImportRequest } from '../api/catalogApi'

type Mode = 'query' | 'providerTrackId'

/** Imports from the music provider; tracks without a preview are skipped by the server (BR-27). */
export function ImportTracksDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation('admin')
  const importer = useImportTracks()
  const [mode, setMode] = useState<Mode>('query')
  const [value, setValue] = useState('')
  const [limit, setLimit] = useState('25')
  const [country, setCountry] = useState('')

  const close = () => {
    importer.reset()
    setValue('')
    onClose()
  }

  const submit = () => {
    const text = value.trim()
    if (!text) return
    const code = country.trim().toUpperCase()
    const body: AdminTrackImportRequest =
      mode === 'query'
        ? { query: text, limit: Math.min(50, Math.max(1, Number(limit) || 25)) }
        : { providerTrackId: text, limit: 1 }
    if (/^[A-Z]{2}$/.test(code)) body.defaultCountryCode = code
    importer.mutate(body)
  }

  const result = importer.data
  return (
    <Dialog
      open={open}
      onClose={close}
      title={t('catalog.import.title')}
      description={t('catalog.import.description')}
    >
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault()
          submit()
        }}
      >
        <Field label={t('catalog.import.mode')} htmlFor="import-mode">
          <Select
            id="import-mode"
            value={mode}
            onChange={(e) => {
              setMode(e.target.value === 'providerTrackId' ? 'providerTrackId' : 'query')
            }}
          >
            <option value="query">{t('catalog.import.byQuery')}</option>
            <option value="providerTrackId">{t('catalog.import.byId')}</option>
          </Select>
        </Field>
        <Field
          label={t(mode === 'query' ? 'catalog.import.query' : 'catalog.import.providerTrackId')}
          htmlFor="import-value"
        >
          <Input
            id="import-value"
            required
            maxLength={mode === 'query' ? 120 : 64}
            value={value}
            onChange={(e) => {
              setValue(e.target.value)
            }}
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          {mode === 'query' ? (
            <Field label={t('catalog.import.limit')} htmlFor="import-limit">
              <Input
                id="import-limit"
                type="number"
                min={1}
                max={50}
                value={limit}
                onChange={(e) => {
                  setLimit(e.target.value)
                }}
              />
            </Field>
          ) : null}
          <Field label={t('catalog.import.country')} htmlFor="import-country">
            <Input
              id="import-country"
              maxLength={2}
              value={country}
              onChange={(e) => {
                setCountry(e.target.value)
              }}
            />
          </Field>
        </div>

        {importer.isError ? (
          <p role="alert" className="text-sm text-negative">
            {t(adminErrorKey(importer.error))}
          </p>
        ) : null}
        {result ? (
          <div role="status" className="space-y-1 rounded-xl bg-raised p-3 text-sm">
            <p className="font-medium">
              {t('catalog.import.result', {
                imported: result.importedCount,
                total: result.totalProcessed,
              })}
            </p>
            <p className="text-muted-foreground">
              {t('catalog.import.skipped', {
                noPreview: result.skippedNoPreviewCount,
                duplicate: result.duplicateCount,
              })}
            </p>
          </div>
        ) : null}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={close}>
            {t('close')}
          </Button>
          <Button type="submit" disabled={importer.isPending || value.trim() === ''}>
            {importer.isPending ? t('catalog.import.running') : t('catalog.import.submit')}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
