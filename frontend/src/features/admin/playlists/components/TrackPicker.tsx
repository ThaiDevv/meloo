import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { Input } from '@/shared/ui/Input'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Pager } from '../../shell/fields'
import { Skeleton } from '@/shared/ui/Skeleton'
import { useAdminTracks, type AdminTrack } from '../../catalog/api/catalogApi'

/** Search enabled catalog tracks and add them to the playlist being edited. */
export function TrackPicker({
  selected,
  onAdd,
}: {
  selected: string[]
  onAdd: (track: AdminTrack) => void
}) {
  const { t } = useTranslation('admin')
  const [text, setText] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const search = () => {
    setQ(text.trim())
    setPage(0)
  }
  const query = useAdminTracks({ q: q || undefined, enabled: true, page }, q !== '')

  return (
    <div className="space-y-3">
      <h3 className="font-semibold">{t('playlists.picker.title')}</h3>
      <div
        role="search"
        className="flex gap-2"
        onKeyDown={(event) => {
          // Enter searches here instead of submitting the playlist form.
          if (event.key === 'Enter') {
            event.preventDefault()
            search()
          }
        }}
      >
        <label htmlFor="picker-q" className="sr-only">
          {t('playlists.picker.search')}
        </label>
        <Input
          id="picker-q"
          type="search"
          maxLength={100}
          placeholder={t('playlists.picker.placeholder')}
          value={text}
          onChange={(e) => {
            setText(e.target.value)
          }}
        />
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            search()
          }}
        >
          {t('playlists.picker.search')}
        </Button>
      </div>
      {q === '' ? (
        <p className="text-sm text-muted-foreground">{t('playlists.picker.hint')}</p>
      ) : query.isPending ? (
        <Skeleton className="h-24" />
      ) : query.isError ? (
        <ErrorState onRetry={() => void query.refetch()} />
      ) : query.data.items.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('playlists.picker.none')}</p>
      ) : (
        <div className="space-y-3" aria-busy={query.isFetching}>
          <ul className="divide-y divide-border rounded-2xl border border-border">
            {query.data.items.map((track) => {
              const added = selected.includes(track.id)
              return (
                <li key={track.id} className="flex items-center justify-between gap-3 px-3 py-2">
                  <span className="min-w-0 text-sm">
                    <span className="block truncate font-medium">{track.title}</span>
                    <span className="block truncate text-muted-foreground">{track.artistName}</span>
                  </span>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    disabled={added}
                    aria-label={t('playlists.picker.add', { title: track.title })}
                    onClick={() => {
                      onAdd(track)
                    }}
                  >
                    {added ? t('playlists.picker.added') : t('playlists.picker.addShort')}
                  </Button>
                </li>
              )
            })}
          </ul>
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
        </div>
      )}
    </div>
  )
}
