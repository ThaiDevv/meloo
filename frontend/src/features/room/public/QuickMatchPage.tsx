import { Swords } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { EMPTY_SELECTION, MusicPicker, type MusicSelection } from '@/shared/music-picker'
import { usePlaylists } from '@/shared/music-picker/api/usePlaylists'
import type { RoomResponse } from '@/shared/realtime/messages'
import { useSessionStore } from '@/shared/session/store'
import { Button } from '@/shared/ui/Button'
import { isIncomplete, musicRequest } from '../lobby/model/music'
import { failureOfError, useQuickMatch } from './api/publicRoomsApi'
import { GuestInvite } from './components/GuestInvite'
import { QueueScreen } from './components/QueueScreen'
import { failureText } from './model/errorText'

/** /quick-match: choose music, enter the matchmaking queue and wait for opponents. */
export function QuickMatchPage() {
  const { t } = useTranslation('room')
  const navigate = useNavigate()
  const role = useSessionStore((state) => state.role)
  const quickMatch = useQuickMatch()
  const playlists = usePlaylists()
  const [music, setMusic] = useState<MusicSelection>(EMPTY_SELECTION)
  const [room, setRoom] = useState<RoomResponse | null>(null)
  const [failure, setFailure] = useState<string | null>(null)

  if (room) {
    return (
      <QueueScreen
        room={room}
        onLeft={() => {
          setRoom(null)
        }}
      />
    )
  }

  const registered = role === 'USER' || role === 'ADMIN'
  const incomplete = isIncomplete(music)

  const search = () => {
    if (incomplete) return
    setFailure(null)
    quickMatch.mutate(musicRequest(music), {
      onSuccess: setRoom,
      onError: (error) => {
        const next = failureOfError(error)
        if (next.reason === 'unauthorized') {
          void navigate('/start', { state: { from: '/quick-match' } })
          return
        }
        if (next.reason === 'playlist-not-found') void playlists.refetch()
        setFailure(failureText(next, t))
      },
    })
  }

  if (!registered) return <GuestInvite />

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-10 lg:py-14">
      <header className="max-w-2xl space-y-4">
        <h1 className="font-display text-[clamp(2.5rem,8vw,4.5rem)] leading-none font-extrabold tracking-[-0.03em] text-balance">
          {t('quick.title')}
        </h1>
        <p className="text-lg">{t('quick.intro')}</p>
      </header>

      <div className="mt-10 space-y-10">
        <section aria-labelledby="quick-music-heading" className="space-y-4">
          <h2
            id="quick-music-heading"
            className="font-display text-2xl font-extrabold tracking-[-0.02em]"
          >
            {t('create.music.heading')}
          </h2>
          <MusicPicker value={music} onChange={setMusic} />
        </section>
        <div className="space-y-3">
          <Button
            className="h-14 w-full text-base sm:w-auto sm:px-10"
            disabled={quickMatch.isPending || incomplete}
            aria-describedby={incomplete ? 'quick-playlist-hint' : undefined}
            onClick={search}
          >
            <Swords aria-hidden="true" className="size-5" strokeWidth={2} />
            {quickMatch.isPending ? t('quick.starting') : t('quick.start')}
          </Button>
          {incomplete ? (
            <p id="quick-playlist-hint" className="text-sm text-muted-foreground">
              {t('create.playlistRequired')}
            </p>
          ) : null}
          {failure ? (
            <p role="alert" className="rounded-xl bg-negative-soft p-4 text-sm text-negative">
              {failure}
            </p>
          ) : null}
        </div>
      </div>
    </div>
  )
}
