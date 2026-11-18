import { useState, type SyntheticEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Badge } from '@/shared/ui/Badge'
import { Button } from '@/shared/ui/Button'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Input } from '@/shared/ui/Input'
import { Skeleton } from '@/shared/ui/Skeleton'
import { toast } from '@/shared/ui/toast-store'
import { adminErrorKey } from '../shell/adminApi'
import { Field, Pager, Select } from '../shell/fields'
import { useAdminTracks, useSetTrackStatus, type AdminTrack } from './api/catalogApi'
import { DECADES, EMPTY, toFilters, type Draft } from './filters'
import { DisableTrackDialog } from './components/DisableTrackDialog'
import { ImportTracksDialog } from './components/ImportTracksDialog'

export function CatalogPage() {
  const { t } = useTranslation('admin')
  const [draft, setDraft] = useState<Draft>(EMPTY)
  const [applied, setApplied] = useState<Draft>(EMPTY)
  const [page, setPage] = useState(0)
  const [importing, setImporting] = useState(false)
  const query = useAdminTracks(toFilters(applied, page))

  const submit = (event: SyntheticEvent) => {
    event.preventDefault()
    setApplied(draft)
    setPage(0)
  }
  const update = (patch: Partial<Draft>) => {
    setDraft((current) => ({ ...current, ...patch }))
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">{t('catalog.subtitle')}</p>
        <Button
          onClick={() => {
            setImporting(true)
          }}
        >
          {t('catalog.import.open')}
        </Button>
      </div>

      <form
        role="search"
        aria-label={t('catalog.filters.label')}
        onSubmit={submit}
        className="grid gap-3 rounded-2xl border border-border bg-surface p-4 sm:grid-cols-2 lg:grid-cols-6"
      >
        <Field label={t('catalog.filters.q')} htmlFor="catalog-q" className="lg:col-span-2">
          <Input
            id="catalog-q"
            type="search"
            maxLength={100}
            value={draft.q}
            onChange={(e) => {
              update({ q: e.target.value })
            }}
          />
        </Field>
        <Field label={t('catalog.filters.status')} htmlFor="catalog-status">
          <Select
            id="catalog-status"
            value={draft.status}
            onChange={(e) => {
              const value = e.target.value
              update({ status: value === 'enabled' || value === 'disabled' ? value : 'all' })
            }}
          >
            <option value="all">{t('catalog.filters.all')}</option>
            <option value="enabled">{t('catalog.status.enabled')}</option>
            <option value="disabled">{t('catalog.status.disabled')}</option>
          </Select>
        </Field>
        <Field label={t('catalog.filters.genre')} htmlFor="catalog-genre">
          <Input
            id="catalog-genre"
            maxLength={40}
            value={draft.genreCode}
            onChange={(e) => {
              update({ genreCode: e.target.value })
            }}
          />
        </Field>
        <Field label={t('catalog.filters.country')} htmlFor="catalog-country">
          <Input
            id="catalog-country"
            maxLength={2}
            autoCapitalize="characters"
            value={draft.countryCode}
            onChange={(e) => {
              update({ countryCode: e.target.value })
            }}
          />
        </Field>
        <Field label={t('catalog.filters.decade')} htmlFor="catalog-decade">
          <Select
            id="catalog-decade"
            value={draft.decade}
            onChange={(e) => {
              update({ decade: e.target.value })
            }}
          >
            <option value="">{t('catalog.filters.all')}</option>
            {DECADES.map((decade) => (
              <option key={decade} value={decade}>
                {t('catalog.filters.decadeValue', { decade })}
              </option>
            ))}
          </Select>
        </Field>
        <div className="flex gap-2 sm:col-span-2 lg:col-span-6">
          <Button type="submit">{t('catalog.filters.apply')}</Button>
          <Button
            type="button"
            variant="ghost"
            onClick={() => {
              setDraft(EMPTY)
              setApplied(EMPTY)
              setPage(0)
            }}
          >
            {t('catalog.filters.reset')}
          </Button>
        </div>
      </form>

      {query.isPending ? (
        <div role="status" aria-busy="true" className="space-y-2">
          <span className="sr-only">{t('loading')}</span>
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} className="h-12" />
          ))}
        </div>
      ) : query.isError ? (
        <ErrorState onRetry={() => void query.refetch()} />
      ) : query.data.items.length === 0 ? (
        <EmptyState title={t('catalog.empty.title')} description={t('catalog.empty.description')} />
      ) : (
        <>
          <TrackTable tracks={query.data.items} />
          <Pager
            page={page}
            totalPages={query.data.totalPages}
            onChange={setPage}
            labels={{
              previous: t('pager.previous'),
              next: t('pager.next'),
              status: t('pager.status', { page: page + 1, total: query.data.totalPages }),
            }}
          />
        </>
      )}

      <ImportTracksDialog
        open={importing}
        onClose={() => {
          setImporting(false)
        }}
      />
    </div>
  )
}

function TrackTable({ tracks }: { tracks: AdminTrack[] }) {
  const { t } = useTranslation('admin')
  const status = useSetTrackStatus()
  const [disabling, setDisabling] = useState<AdminTrack | null>(null)

  const change = (track: AdminTrack, enabled: boolean, disabledReason?: string) => {
    status.mutate(
      { id: track.id, enabled, disabledReason },
      {
        onSuccess: (updated) => {
          setDisabling(null)
          toast({
            tone: 'positive',
            title: t(updated.enabled ? 'catalog.toast.enabled' : 'catalog.toast.disabled', {
              title: updated.title,
            }),
          })
        },
        onError: (error) => {
          toast({ tone: 'negative', title: t(adminErrorKey(error)) })
        },
      },
    )
  }

  return (
    <div className="relative overflow-x-auto rounded-2xl border border-border">
      <table className="w-full min-w-[720px] text-left text-sm">
        <caption className="sr-only">{t('catalog.table.caption')}</caption>
        <thead className="bg-raised text-muted-foreground">
          <tr>
            <th scope="col" className="px-4 py-3 font-medium">
              {t('catalog.table.track')}
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              {t('catalog.table.genre')}
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              {t('catalog.table.country')}
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              {t('catalog.table.year')}
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              {t('catalog.table.status')}
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              <span className="sr-only">{t('catalog.table.actions')}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {tracks.map((track) => (
            <tr key={track.id} className="border-t border-border">
              <td className="px-4 py-3">
                <p className="font-medium">{track.title}</p>
                <p className="text-muted-foreground">{track.artistName}</p>
              </td>
              <td className="px-4 py-3">{track.genreCode ?? '–'}</td>
              <td className="px-4 py-3">{track.countryCode ?? '–'}</td>
              <td className="px-4 py-3 tabular-nums">{track.releaseYear ?? '–'}</td>
              <td className="px-4 py-3">
                <Badge tone={track.enabled ? 'positive' : 'negative'}>
                  {t(track.enabled ? 'catalog.status.enabled' : 'catalog.status.disabled')}
                </Badge>
                {!track.enabled && track.disabledReason ? (
                  <p className="mt-1 text-xs text-muted-foreground">{track.disabledReason}</p>
                ) : null}
              </td>
              <td className="px-4 py-3 text-right">
                <Button
                  size="sm"
                  variant={track.enabled ? 'destructive' : 'outline'}
                  disabled={status.isPending}
                  aria-label={t(
                    track.enabled ? 'catalog.action.disable' : 'catalog.action.enable',
                    {
                      title: track.title,
                    },
                  )}
                  onClick={() => {
                    if (track.enabled) setDisabling(track)
                    else change(track, true)
                  }}
                >
                  {t(track.enabled ? 'catalog.action.disableShort' : 'catalog.action.enableShort')}
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {disabling ? (
        <DisableTrackDialog
          track={disabling}
          pending={status.isPending}
          onClose={() => {
            setDisabling(null)
          }}
          onConfirm={(reason) => {
            change(disabling, false, reason)
          }}
        />
      ) : null}
    </div>
  )
}
