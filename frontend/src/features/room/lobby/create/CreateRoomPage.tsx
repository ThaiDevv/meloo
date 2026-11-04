import { Plus } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import {
  EMPTY_SELECTION,
  MusicPicker,
  countFilterValues,
  type MusicSelection,
} from '@/shared/music-picker'
import { usePlaylists } from '@/shared/music-picker/api/usePlaylists'
import { Button } from '@/shared/ui/Button'
import { RoomRequestError, useCreateRoom, type RoomFailure } from '../api/roomApi'
import { ChoiceGroup } from '../components/ChoiceGroup'
import { isIncomplete, musicRequest } from '../model/music'
import { JoinByCodeForm } from './JoinByCodeForm'

type Visibility = 'PRIVATE_ROOM' | 'PUBLIC_ROOM'
type RoundCount = 5 | 10 | 15

const ROUND_COUNTS: RoundCount[] = [5, 10, 15]
const PLAYER_COUNTS = [2, 3, 4, 5]

function SummaryRow({ term, detail }: { term: string; detail: string }) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-sm text-muted-foreground">{term}</dt>
      <dd className="text-right font-semibold">{detail}</dd>
    </div>
  )
}

function FailureNotice({ failure, roundCount }: { failure: RoomFailure; roundCount: RoundCount }) {
  const { t } = useTranslation('room')
  const text =
    failure.reason === 'not-enough-tracks'
      ? t('create.errors.notEnoughTracks', { n: roundCount, missing: failure.missingTracks })
      : failure.reason === 'playlist-not-found'
        ? t('create.errors.playlistNotFound')
        : failure.reason === 'rate-limited'
          ? t('create.errors.rateLimited')
          : failure.reason === 'locked'
            ? t('create.errors.locked')
            : failure.reason === 'rejected'
              ? t('create.errors.rejected')
              : t('create.errors.unknown')
  return (
    <p role="alert" className="rounded-xl bg-negative-soft p-4 text-sm text-negative">
      {text}
    </p>
  )
}

/** /rooms/new: pick who may join, how long and which music, then open the lobby as its host. */
export function CreateRoomPage() {
  const { t, i18n } = useTranslation('room')
  const navigate = useNavigate()
  const create = useCreateRoom()
  const playlists = usePlaylists()
  const [visibility, setVisibility] = useState<Visibility>('PRIVATE_ROOM')
  const [roundCount, setRoundCount] = useState<RoundCount>(10)
  const [maxPlayers, setMaxPlayers] = useState(5)
  const [music, setMusic] = useState<MusicSelection>(EMPTY_SELECTION)
  const [failure, setFailure] = useState<RoomFailure | null>(null)

  const incomplete = isIncomplete(music)
  const filterCount = countFilterValues(music.filter)
  const playlist =
    music.source === 'playlist' && music.playlistId
      ? playlists.data?.find((p) => p.id === music.playlistId)
      : null
  const musicSummary =
    music.source === 'playlist'
      ? playlist
        ? i18n.resolvedLanguage === 'vi'
          ? playlist.nameVi
          : playlist.nameEn
        : music.playlistId
          ? t('settings.music.playlist')
          : t('create.summary.noPlaylist')
      : filterCount === 0
        ? t('settings.music.wholeCatalog')
        : t('settings.music.filters', { n: filterCount })

  const submit = () => {
    if (incomplete) return
    setFailure(null)
    create.mutate(
      { roomType: visibility, roundCount, maxPlayers, ...musicRequest(music) },
      {
        onSuccess: (room) => {
          // The snapshot travels with the navigation: the creator is already inside, no need to join.
          void navigate(`/r/${room.code ?? room.roomId}`, { state: { room } })
        },
        onError: (error) => {
          const next =
            error instanceof RoomRequestError ? error.failure : { reason: 'unknown' as const }
          if (next.reason === 'unauthorized') {
            void navigate('/start', { state: { from: '/rooms/new' } })
            return
          }
          if (next.reason === 'playlist-not-found') void playlists.refetch()
          setFailure(next)
        },
      },
    )
  }

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-10 lg:py-14">
      <header className="max-w-2xl space-y-3">
        <h1 className="font-display text-[clamp(2rem,6vw,3.25rem)] leading-none font-extrabold tracking-[-0.03em] text-balance">
          {t('create.title')}
        </h1>
        <p className="text-lg">{t('create.intro')}</p>
      </header>

      <div className="mt-10 grid gap-10 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start lg:gap-14">
        <div className="space-y-10">
          <ChoiceGroup
            legend={t('create.visibility.legend')}
            name="visibility"
            value={visibility}
            onChange={setVisibility}
            options={[
              {
                value: 'PRIVATE_ROOM',
                title: t('create.visibility.private.title'),
                description: t('create.visibility.private.description'),
              },
              {
                value: 'PUBLIC_ROOM',
                title: t('create.visibility.public.title'),
                description: t('create.visibility.public.description'),
              },
            ]}
          />
          <ChoiceGroup
            legend={t('create.rounds.legend')}
            name="rounds"
            value={roundCount}
            onChange={setRoundCount}
            compact
            options={ROUND_COUNTS.map((n) => ({ value: n, title: String(n) }))}
          />
          <ChoiceGroup
            legend={t('create.players.legend')}
            name="players"
            value={maxPlayers}
            onChange={setMaxPlayers}
            compact
            options={PLAYER_COUNTS.map((n) => ({ value: n, title: String(n) }))}
          />
          <section aria-labelledby="music-heading" className="space-y-4">
            <h2 id="music-heading" className="text-base font-semibold">
              {t('create.music.heading')}
            </h2>
            <MusicPicker value={music} onChange={setMusic} />
          </section>
        </div>

        <div className="space-y-8 lg:sticky lg:top-20">
          <aside aria-labelledby="summary-heading" className="space-y-5 rounded-2xl bg-surface p-5">
            <h2 id="summary-heading" className="text-base font-semibold">
              {t('create.summary.heading')}
            </h2>
            <dl className="space-y-3">
              <SummaryRow
                term={t('create.visibility.legend')}
                detail={t(
                  visibility === 'PRIVATE_ROOM'
                    ? 'create.visibility.private.title'
                    : 'create.visibility.public.title',
                )}
              />
              <SummaryRow
                term={t('create.rounds.legend')}
                detail={t('create.rounds.option', { n: roundCount })}
              />
              <SummaryRow
                term={t('create.players.legend')}
                detail={t('create.players.option', { n: maxPlayers })}
              />
              <SummaryRow term={t('create.music.heading')} detail={musicSummary} />
            </dl>
            <div className="space-y-2">
              <Button
                className="w-full"
                disabled={create.isPending || incomplete}
                aria-describedby={incomplete ? 'playlist-required-hint' : undefined}
                onClick={submit}
              >
                <Plus aria-hidden="true" className="size-4" strokeWidth={2} />
                {create.isPending ? t('create.submitting') : t('create.submit')}
              </Button>
              {incomplete ? (
                <p
                  id="playlist-required-hint"
                  className="text-center text-xs text-muted-foreground"
                >
                  {t('create.playlistRequired')}
                </p>
              ) : null}
            </div>
            {failure ? <FailureNotice failure={failure} roundCount={roundCount} /> : null}
          </aside>
          <JoinByCodeForm />
        </div>
      </div>
    </div>
  )
}
