import { useState, type SyntheticEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { Input } from '@/shared/ui/Input'
import { toast } from '@/shared/ui/toast-store'
import type { AdminTrack } from '../../catalog/api/catalogApi'
import { adminErrorKey } from '../../shell/adminApi'
import { Field } from '../../shell/fields'
import {
  MIN_TRACKS,
  useSavePlaylist,
  type AdminPlaylist,
  type PlaylistDraft,
} from '../api/playlistsApi'
import { TrackPicker } from './TrackPicker'

const CODE = /^[a-z0-9-]{2,60}$/

/** Create (playlist = null) or edit a playlist: names, enabled flag and its tracks. */
export function PlaylistEditor({
  playlist,
  onDone,
}: {
  playlist: AdminPlaylist | null
  onDone: () => void
}) {
  const { t } = useTranslation('admin')
  const save = useSavePlaylist()
  const [draft, setDraft] = useState<PlaylistDraft>(() => ({
    code: playlist?.code ?? '',
    nameVi: playlist?.nameVi ?? '',
    nameEn: playlist?.nameEn ?? '',
    enabled: playlist?.enabled ?? true,
    trackIds: playlist?.trackIds ?? [],
  }))
  // Titles of tracks seen in search results; the playlist itself only carries ids.
  const [known, setKnown] = useState<Record<string, AdminTrack>>({})
  const [submitted, setSubmitted] = useState(false)

  const codeInvalid = playlist === null && !CODE.test(draft.code)
  const tooFew = (playlist === null || draft.enabled) && draft.trackIds.length < MIN_TRACKS
  const invalid = codeInvalid || !draft.nameVi.trim() || !draft.nameEn.trim() || tooFew

  const submit = (event: SyntheticEvent) => {
    event.preventDefault()
    setSubmitted(true)
    if (invalid) return
    save.mutate(
      {
        original: playlist,
        draft: { ...draft, nameVi: draft.nameVi.trim(), nameEn: draft.nameEn.trim() },
      },
      {
        onSuccess: () => {
          toast({ tone: 'positive', title: t('playlists.saved') })
          onDone()
        },
      },
    )
  }

  const add = (track: AdminTrack) => {
    setKnown((current) => ({ ...current, [track.id]: track }))
    setDraft((current) =>
      current.trackIds.includes(track.id)
        ? current
        : { ...current, trackIds: [...current.trackIds, track.id] },
    )
  }
  const remove = (id: string) => {
    setDraft((current) => ({ ...current, trackIds: current.trackIds.filter((x) => x !== id) }))
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-6">
      <fieldset disabled={save.isPending} className="min-w-0 space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-lg font-semibold">
            {playlist
              ? t('playlists.editTitle', { code: playlist.code })
              : t('playlists.createTitle')}
          </h2>
          <Button type="button" variant="ghost" onClick={onDone}>
            {t('playlists.back')}
          </Button>
        </div>

        <div className="grid gap-4 rounded-2xl border border-border bg-surface p-4 md:grid-cols-3">
          <Field label={t('playlists.form.code')} htmlFor="playlist-code">
            <Input
              id="playlist-code"
              value={draft.code}
              disabled={playlist !== null}
              maxLength={60}
              aria-invalid={submitted && codeInvalid}
              aria-describedby="playlist-code-hint"
              onChange={(e) => {
                setDraft({ ...draft, code: e.target.value })
              }}
            />
            <p id="playlist-code-hint" className="text-xs text-muted-foreground">
              {t('playlists.form.codeHint')}
            </p>
          </Field>
          <Field label={t('playlists.form.nameVi')} htmlFor="playlist-name-vi">
            <Input
              id="playlist-name-vi"
              value={draft.nameVi}
              maxLength={120}
              aria-invalid={submitted && !draft.nameVi.trim()}
              onChange={(e) => {
                setDraft({ ...draft, nameVi: e.target.value })
              }}
            />
          </Field>
          <Field label={t('playlists.form.nameEn')} htmlFor="playlist-name-en">
            <Input
              id="playlist-name-en"
              value={draft.nameEn}
              maxLength={120}
              aria-invalid={submitted && !draft.nameEn.trim()}
              onChange={(e) => {
                setDraft({ ...draft, nameEn: e.target.value })
              }}
            />
          </Field>
          {playlist ? (
            <label className="flex items-center gap-2 text-sm md:col-span-3">
              <input
                type="checkbox"
                className="size-4 accent-[var(--primary)]"
                checked={draft.enabled}
                onChange={(e) => {
                  setDraft({ ...draft, enabled: e.target.checked })
                }}
              />
              {t('playlists.form.enabled')}
            </label>
          ) : null}
        </div>

        <section aria-labelledby="playlist-tracks" className="grid gap-4 lg:grid-cols-2">
          <div className="space-y-3">
            <h3 id="playlist-tracks" className="font-semibold">
              {t('playlists.tracks.selected', { count: draft.trackIds.length })}
            </h3>
            {submitted && tooFew ? (
              <p role="alert" className="text-sm text-negative">
                {t('playlists.tracks.min', { min: MIN_TRACKS })}
              </p>
            ) : null}
            {draft.trackIds.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t('playlists.tracks.none')}</p>
            ) : (
              <ol className="divide-y divide-border rounded-2xl border border-border">
                {draft.trackIds.map((id, index) => {
                  const track = known[id]
                  const label = track
                    ? `${track.title} · ${track.artistName}`
                    : t('playlists.tracks.unknown', { id })
                  return (
                    <li key={id} className="flex items-center justify-between gap-3 px-3 py-2">
                      <span className="min-w-0 truncate text-sm">
                        <span className="mr-2 text-faint tabular-nums">{index + 1}.</span>
                        {label}
                      </span>
                      <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        aria-label={t('playlists.tracks.remove', { track: label })}
                        onClick={() => {
                          remove(id)
                        }}
                      >
                        {t('playlists.tracks.removeShort')}
                      </Button>
                    </li>
                  )
                })}
              </ol>
            )}
          </div>
          <TrackPicker selected={draft.trackIds} onAdd={add} />
        </section>

        {save.isError ? (
          <p role="alert" className="text-sm text-negative">
            {t(adminErrorKey(save.error))} {playlist ? t('playlists.partialSave') : ''}
          </p>
        ) : null}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onDone}>
            {t('cancel')}
          </Button>
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? t('playlists.saving') : t('playlists.save')}
          </Button>
        </div>
      </fieldset>
    </form>
  )
}
