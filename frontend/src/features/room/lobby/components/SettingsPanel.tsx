import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { MusicPicker, countFilterValues, type MusicSelection } from '@/shared/music-picker'
import { usePlaylists } from '@/shared/music-picker/api/usePlaylists'
import type { RoomResponse, SettingsCommand } from '@/shared/realtime/messages'
import { Button } from '@/shared/ui/Button'
import { isIncomplete, musicSettings, selectionOf } from '../model/music'
import { MAX_PLAYERS_LIMIT, minimumMaxPlayers } from '../model/rules'
import { ChoiceGroup } from './ChoiceGroup'

const ROUND_COUNTS = [5, 10, 15] as const

interface SettingsPanelProps {
  room: RoomResponse
  /** Only the host of a room with a host edits (BR-12, BR-13). */
  editable: boolean
  /** False while the socket is down: a command would be lost. */
  canSend: boolean
  onChange: (settings: SettingsCommand) => void
}

function SummaryRow({ term, detail }: { term: string; detail: string }) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-sm text-muted-foreground">{term}</dt>
      <dd className="text-right text-lg font-bold">{detail}</dd>
    </div>
  )
}

export function SettingsPanel({ room, editable, canSend, onChange }: SettingsPanelProps) {
  const { t, i18n } = useTranslation('room')
  const playlists = usePlaylists()
  const [editingMusic, setEditingMusic] = useState(false)
  const [draft, setDraft] = useState<MusicSelection>(() => selectionOf(room))

  const playlist = room.playlistId ? playlists.data?.find((p) => p.id === room.playlistId) : null
  const playlistName = playlist
    ? i18n.resolvedLanguage === 'vi'
      ? playlist.nameVi
      : playlist.nameEn
    : null
  const filterCount = countFilterValues(selectionOf(room).filter)
  const music = room.playlistId
    ? (playlistName ?? t('settings.music.playlist'))
    : filterCount === 0
      ? t('settings.music.wholeCatalog')
      : t('settings.music.filters', { n: filterCount })

  if (!editable) {
    return (
      <section aria-labelledby="settings-heading" className="space-y-3">
        <h2 id="settings-heading" className="text-base font-semibold">
          {t('settings.heading')}
        </h2>
        <dl className="space-y-2">
          <SummaryRow
            term={t('settings.rounds')}
            detail={t('settings.roundsValue', { n: room.roundCount })}
          />
          <SummaryRow
            term={t('settings.players')}
            detail={t('settings.playersValue', { n: room.maxPlayers })}
          />
          <SummaryRow term={t('settings.music.heading')} detail={music} />
        </dl>
      </section>
    )
  }

  const minPlayers = minimumMaxPlayers(room)
  const maxOptions = Array.from({ length: MAX_PLAYERS_LIMIT - 1 }, (_, i) => i + 2)

  return (
    <section aria-labelledby="settings-heading" className="space-y-5">
      <h2 id="settings-heading" className="text-base font-semibold">
        {t('settings.heading')}
      </h2>
      <ChoiceGroup
        legend={t('settings.rounds')}
        name="settings-rounds"
        value={room.roundCount}
        disabled={!canSend}
        compact
        onChange={(roundCount) => {
          onChange({ roundCount })
        }}
        options={ROUND_COUNTS.map((n) => ({ value: n, title: String(n) }))}
      />
      <ChoiceGroup
        legend={t('settings.players')}
        name="settings-players"
        value={room.maxPlayers}
        disabled={!canSend}
        compact
        onChange={(maxPlayers) => {
          onChange({ maxPlayers })
        }}
        options={maxOptions.map((n) => ({ value: n, title: String(n), disabled: n < minPlayers }))}
      />
      <div className="space-y-3">
        <div className="flex items-baseline justify-between gap-4">
          <h3 className="text-base font-semibold">{t('settings.music.heading')}</h3>
          <span className="text-right text-sm font-semibold">{music}</span>
        </div>
        {editingMusic ? (
          <div className="space-y-4">
            <MusicPicker value={draft} onChange={setDraft} />
            <div className="flex gap-2">
              <Button
                size="sm"
                disabled={!canSend || isIncomplete(draft)}
                onClick={() => {
                  onChange(musicSettings(draft))
                  setEditingMusic(false)
                }}
              >
                {t('settings.music.apply')}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  setEditingMusic(false)
                }}
              >
                {t('settings.music.cancel')}
              </Button>
            </div>
          </div>
        ) : (
          <Button
            variant="outline"
            size="sm"
            disabled={!canSend}
            onClick={() => {
              setDraft(selectionOf(room))
              setEditingMusic(true)
            }}
          >
            {t('settings.music.change')}
          </Button>
        )}
      </div>
    </section>
  )
}
