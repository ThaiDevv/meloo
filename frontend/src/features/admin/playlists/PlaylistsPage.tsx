import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Badge } from '@/shared/ui/Badge'
import { Button } from '@/shared/ui/Button'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Skeleton } from '@/shared/ui/Skeleton'
import { useAdminPlaylists, type AdminPlaylist } from './api/playlistsApi'
import { PlaylistEditor } from './components/PlaylistEditor'

type Editing = { kind: 'new' } | { kind: 'edit'; playlist: AdminPlaylist } | null

export function PlaylistsPage() {
  const { t, i18n } = useTranslation('admin')
  const query = useAdminPlaylists()
  const [editing, setEditing] = useState<Editing>(null)
  const name = (p: AdminPlaylist) => (i18n.language.startsWith('en') ? p.nameEn : p.nameVi)

  if (editing) {
    return (
      <PlaylistEditor
        key={editing.kind === 'edit' ? editing.playlist.id : 'new'}
        playlist={editing.kind === 'edit' ? editing.playlist : null}
        onDone={() => {
          setEditing(null)
        }}
      />
    )
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">{t('playlists.subtitle')}</p>
        <Button
          onClick={() => {
            setEditing({ kind: 'new' })
          }}
        >
          {t('playlists.create')}
        </Button>
      </div>

      {query.isPending ? (
        <div role="status" aria-busy="true" className="space-y-2">
          <span className="sr-only">{t('loading')}</span>
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-20" />
          ))}
        </div>
      ) : query.isError ? (
        <ErrorState onRetry={() => void query.refetch()} />
      ) : query.data.length === 0 ? (
        <EmptyState
          title={t('playlists.empty.title')}
          description={t('playlists.empty.description')}
        />
      ) : (
        <ul className="grid gap-3 md:grid-cols-2">
          {query.data.map((playlist) => (
            <li
              key={playlist.id}
              className="flex items-start justify-between gap-3 rounded-2xl border border-border bg-surface p-4"
            >
              <div className="min-w-0 space-y-1.5">
                <p className="truncate font-semibold">{name(playlist)}</p>
                <p className="text-xs text-faint">{playlist.code}</p>
                <div className="flex flex-wrap gap-1.5">
                  <Badge tone={playlist.enabled ? 'positive' : 'neutral'}>
                    {t(playlist.enabled ? 'playlists.enabled' : 'playlists.disabled')}
                  </Badge>
                  <Badge>{t(playlist.curated ? 'playlists.curated' : 'playlists.standard')}</Badge>
                  <Badge tone={playlist.enabledTrackCount < 5 ? 'warning' : 'neutral'}>
                    {t('playlists.trackCount', {
                      enabled: playlist.enabledTrackCount,
                      total: playlist.trackIds.length,
                    })}
                  </Badge>
                </div>
              </div>
              <Button
                size="sm"
                variant="outline"
                disabled={!playlist.curated}
                title={!playlist.curated ? t('playlists.readOnly') : undefined}
                aria-label={t('playlists.editNamed', { name: name(playlist) })}
                onClick={() => {
                  setEditing({ kind: 'edit', playlist })
                }}
              >
                {t('playlists.edit')}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
