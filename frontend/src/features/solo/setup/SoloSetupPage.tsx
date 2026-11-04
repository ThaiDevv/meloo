import { useQueryClient } from '@tanstack/react-query'
import { Headphones, Play } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import {
  EMPTY_SELECTION,
  Equalizer,
  MusicPicker,
  SoundBackdrop,
  VinylDisc,
  countFilterValues,
  toSoloFilter,
  type MusicSelection,
} from '@/shared/music-picker'
import { usePlaylists } from '@/shared/music-picker/api/usePlaylists'
import { Badge } from '@/shared/ui/Badge'
import { Button } from '@/shared/ui/Button'
import {
  CreateSoloMatchError,
  useCreateSoloMatch,
  type CreateSoloMatchFailure,
} from './api/useCreateSoloMatch'
import { RadioTiles } from './components/RadioTiles'

type Mode = 'PRACTICE'
type RoundCount = 5 | 10 | 15

const ROUND_COUNTS: RoundCount[] = [5, 10, 15]

export function SoloSetupPage() {
  const { t, i18n } = useTranslation('solo')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const create = useCreateSoloMatch()
  const playlists = usePlaylists()
  const [mode, setMode] = useState<Mode>('PRACTICE')
  const [roundCount, setRoundCount] = useState<RoundCount>(10)
  const [music, setMusic] = useState<MusicSelection>(EMPTY_SELECTION)
  const [failure, setFailure] = useState<CreateSoloMatchFailure | null>(null)

  const isPlaylistUnselected =
    music.source === 'playlist' && !music.playlistId
  const canStart = !create.isPending && !isPlaylistUnselected

  const filterCount = countFilterValues(music.filter)
  const selectedPlaylist =
    music.source === 'playlist' && music.playlistId
      ? playlists.data?.find((p) => p.id === music.playlistId)
      : null

  const musicSummary =
    music.source === 'playlist'
      ? selectedPlaylist
        ? i18n.resolvedLanguage === 'vi'
          ? selectedPlaylist.nameVi
          : selectedPlaylist.nameEn
        : music.playlistId
          ? music.playlistId
          : t('setup.summary.noPlaylist')
      : filterCount === 0
        ? t('setup.summary.wholeCatalog')
        : t('setup.summary.filters', { n: filterCount })

  const start = () => {
    if (isPlaylistUnselected) return
    setFailure(null)
    const body =
      music.source === 'playlist'
        ? { mode: 'PRACTICE' as const, roundCount, ...(music.playlistId && { playlistId: music.playlistId }) }
        : {
            mode: 'PRACTICE' as const,
            roundCount,
            ...(filterCount > 0 && { filter: toSoloFilter(music.filter) }),
          }
    create.mutate(body, {
      onSuccess: (match) => {
        void navigate(`/solo/${match.matchId}`)
      },
      onError: (error) => {
        const next =
          error instanceof CreateSoloMatchError ? error.failure : { reason: 'unknown' as const }
        if (next.reason === 'unauthorized') {
          void navigate('/start', { state: { from: '/solo' } })
          return
        }
        if (next.reason === 'playlist-not-found') {
          void playlists.refetch()
          void queryClient.invalidateQueries({ queryKey: ['catalog', 'playlists'] })
        }
        setFailure(next)
      },
    })
  }

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-10 lg:py-16">
      <SoundBackdrop />
      <header className="motion-rise flex items-center justify-between gap-6">
        <div className="max-w-2xl space-y-4">
          <div className="flex items-end gap-4">
            <h1 className="font-display text-[clamp(2.5rem,7vw,4.5rem)] leading-none font-extrabold tracking-[-0.03em] text-balance">
              {t('title')}
            </h1>
            <Equalizer className="mb-2 text-[clamp(1.5rem,3.5vw,2.5rem)] text-primary" />
          </div>
          <p className="text-lg">{t('setup.intro')}</p>
        </div>
        <VinylDisc accent className="hidden w-32 shrink-0 text-foreground sm:block lg:w-44" />
      </header>

      <div className="mt-10 grid gap-10 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start lg:gap-14">
        <div className="space-y-10">
          <RadioTiles
            legend={t('setup.mode.legend')}
            name="mode"
            value={mode}
            onChange={setMode}
            options={[
              {
                value: 'PRACTICE',
                title: t('setup.mode.practice.title'),
                description: t('setup.mode.practice.description'),
                icon: <Headphones className="size-6" strokeWidth={1.75} />,
              },
            ]}
          />
          <RadioTiles
            legend={t('setup.rounds.legend')}
            name="rounds"
            value={roundCount}
            onChange={setRoundCount}
            compact
            options={ROUND_COUNTS.map((count) => ({
              value: count,
              title: String(count),
              badge: (
                <>
                  <span className="sr-only">{t('setup.rounds.unit')}</span>
                  <Pips count={count} />
                </>
              ),
            }))}
          />
          <section aria-labelledby="music-heading" className="space-y-4">
            <h2 id="music-heading" className="text-base font-semibold">
              {t('setup.music.heading')}
            </h2>
            <MusicPicker value={music} onChange={setMusic} />
          </section>
        </div>

        <aside
          aria-labelledby="summary-heading"
          className="space-y-5 rounded-2xl bg-surface p-5 lg:sticky lg:top-20"
        >
          <div className="flex items-center justify-between gap-4">
            <div className="space-y-2">
              <h2 id="summary-heading" className="text-base font-semibold">
                {t('setup.summary.heading')}
              </h2>
              <Badge tone="neutral">
                {t('setup.summary.practice')}
              </Badge>
            </div>
            <VinylDisc accent fast={create.isPending} className="w-20 shrink-0 text-foreground" />
          </div>
          <dl className="space-y-3">
            <SummaryRow
              term={t('setup.mode.legend')}
              detail={t('setup.mode.practice.title')}
            />
            <SummaryRow
              term={t('setup.rounds.legend')}
              detail={t('setup.rounds.option', { n: roundCount })}
            />
            <SummaryRow term={t('setup.music.heading')} detail={musicSummary} />
          </dl>
          <div className="space-y-2">
            <Button
              className="w-full"
              disabled={!canStart}
              aria-describedby={isPlaylistUnselected ? 'playlist-required-hint' : undefined}
              onClick={start}
            >
              <Play aria-hidden="true" className="size-4 fill-current" />
              {create.isPending ? t('setup.starting') : t('setup.start')}
            </Button>
            {isPlaylistUnselected ? (
              <p id="playlist-required-hint" className="text-center text-xs text-muted-foreground">
                {t('setup.playlistRequired')}
              </p>
            ) : null}
          </div>
          {failure ? <FailureNotice failure={failure} roundCount={roundCount} /> : null}
        </aside>
      </div>
    </div>
  )
}

/** One dot per round, so 5, 10 and 15 can be told apart at a glance. */
function Pips({ count }: { count: number }) {
  return (
    <span aria-hidden="true" className="mx-auto flex max-w-24 flex-wrap justify-center gap-1 pt-1">
      {Array.from({ length: count }, (_, i) => (
        <i key={i} className="size-1.5 rounded-full bg-current opacity-70" />
      ))}
    </span>
  )
}

function SummaryRow({ term, detail }: { term: string; detail: string }) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-sm text-muted-foreground">{term}</dt>
      <dd className="text-right font-semibold">{detail}</dd>
    </div>
  )
}

function FailureNotice({
  failure,
  roundCount,
}: {
  failure: CreateSoloMatchFailure
  roundCount: RoundCount
}) {
  const { t } = useTranslation('solo')
  return (
    <div role="alert" className="space-y-3 rounded-xl bg-negative-soft p-4 text-sm text-negative">
      {failure.reason === 'in-progress' ? (
        <>
          <p>{t('setup.errors.inProgress')}</p>
          <Button asChild variant="outline" size="sm">
            <Link to={`/solo/${failure.matchId}`}>{t('setup.errors.resume')}</Link>
          </Button>
        </>
      ) : failure.reason === 'not-enough-tracks' ? (
        <p>
          {t('setup.errors.notEnoughTracks', { n: roundCount, missing: failure.missingTracks })}
        </p>
      ) : failure.reason === 'playlist-not-found' ? (
        <p>{t('setup.errors.playlistNotFound')}</p>
      ) : failure.reason === 'rate-limited' ? (
        <p>{t('setup.errors.rateLimited')}</p>
      ) : (
        <p>{t('setup.errors.unknown')}</p>
      )}
    </div>
  )
}
