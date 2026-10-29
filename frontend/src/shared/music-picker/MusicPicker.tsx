import { useMemo, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import { Badge } from '@/shared/ui/Badge'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Input } from '@/shared/ui/Input'
import { Skeleton } from '@/shared/ui/Skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/Tabs'
import { useCatalogFilters } from './api/useCatalogFilters'
import { usePlaylists } from './api/usePlaylists'
import { VinylDisc } from './VinylDisc'
import { MAX_PER_GROUP, type MusicFilter, type MusicSelection } from './types'

const PANEL_ENTRANCE =
  'animate-in fade-in slide-in-from-bottom-2 duration-300 fill-mode-both motion-reduce:animate-none'

interface MusicPickerProps {
  value: MusicSelection
  onChange: (next: MusicSelection) => void
  /** Shows the playlist tab as "coming soon" for screens whose request cannot carry a playlist yet. */
  playlistsLocked?: boolean
  className?: string
}

/**
 * Chooses the tracks of a match: by genre, country, decade and artist (values inside one group
 * are OR-ed, groups are AND-ed), or by a curated playlist. Controlled; shared by solo, room
 * creation and quick match, so it knows nothing about any of them.
 */
export function MusicPicker({
  value,
  onChange,
  playlistsLocked = false,
  className,
}: MusicPickerProps) {
  const { t } = useTranslation('solo')
  const source = playlistsLocked ? 'filter' : value.source
  return (
    <Tabs
      value={source}
      onValueChange={(next) => {
        if (next === 'filter' || next === 'playlist') onChange({ ...value, source: next })
      }}
      className={className}
    >
      <TabsList aria-label={t('picker.sourceLabel')}>
        <TabsTrigger value="filter">{t('picker.byFilter')}</TabsTrigger>
        <TabsTrigger value="playlist" disabled={playlistsLocked}>
          {t('picker.byPlaylist')}
          {playlistsLocked ? <Badge className="ml-2">{t('picker.soon')}</Badge> : null}
        </TabsTrigger>
      </TabsList>
      <TabsContent value="filter" className={PANEL_ENTRANCE}>
        <FilterPanel
          filter={value.filter}
          onChange={(filter) => {
            onChange({ ...value, filter })
          }}
        />
      </TabsContent>
      <TabsContent value="playlist" className={PANEL_ENTRANCE}>
        <PlaylistPanel
          playlistId={value.playlistId}
          onChange={(playlistId) => {
            onChange({ ...value, playlistId })
          }}
        />
      </TabsContent>
    </Tabs>
  )
}

function toggled<T>(list: T[], item: T): T[] {
  return list.includes(item) ? list.filter((x) => x !== item) : [...list, item]
}

function normalize(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/đ/gi, 'd')
    .toLowerCase()
}

function PanelSkeleton() {
  return (
    <div aria-busy="true" className="space-y-5">
      {[0, 1, 2].map((row) => (
        <div key={row} className="space-y-2">
          <Skeleton className="h-4 w-24" />
          <div className="flex flex-wrap gap-2">
            {[0, 1, 2, 3].map((chip) => (
              <Skeleton key={chip} className="h-10 w-24 rounded-full" />
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}

function FilterPanel({
  filter,
  onChange,
}: {
  filter: MusicFilter
  onChange: (next: MusicFilter) => void
}) {
  const { t, i18n } = useTranslation('solo')
  const filters = useCatalogFilters()
  const [artistQuery, setArtistQuery] = useState('')
  const vi = i18n.resolvedLanguage === 'vi'

  const regionNames = useMemo(() => {
    try {
      return new Intl.DisplayNames([vi ? 'vi' : 'en'], { type: 'region' })
    } catch {
      return null
    }
  }, [vi])

  if (filters.isPending) return <PanelSkeleton />
  if (filters.isError) {
    return (
      <ErrorState
        onRetry={() => {
          void filters.refetch()
        }}
      />
    )
  }

  const { genres, countries, decades, artists } = filters.data
  if (genres.length + countries.length + decades.length + artists.length === 0) {
    return <EmptyState title={t('picker.empty')} />
  }

  const query = normalize(artistQuery.trim())
  const shownArtists = artists.filter(
    (a) => filter.artistIds.includes(a.id) || normalize(a.name).includes(query),
  )

  return (
    <div className="space-y-6">
      <p className="text-sm text-muted-foreground">{t('picker.filterHint')}</p>
      <ChipGroup
        legend={t('picker.genres')}
        selected={filter.genreCodes}
        options={genres.map((g) => ({ value: g.code, label: vi ? g.nameVi : g.nameEn }))}
        onToggle={(code) => {
          onChange({ ...filter, genreCodes: toggled(filter.genreCodes, code) })
        }}
      />
      <ChipGroup
        legend={t('picker.countries')}
        selected={filter.countryCodes}
        options={countries.map((c) => ({ value: c, label: regionNames?.of(c) ?? c }))}
        onToggle={(code) => {
          onChange({ ...filter, countryCodes: toggled(filter.countryCodes, code) })
        }}
      />
      <ChipGroup
        legend={t('picker.decades')}
        selected={filter.decades}
        options={[...decades]
          .sort((a, b) => a - b)
          .map((d) => ({ value: d, label: t('picker.decade', { decade: d }) }))}
        onToggle={(decade) => {
          onChange({ ...filter, decades: toggled(filter.decades, decade) })
        }}
      />
      <ChipGroup
        legend={t('picker.artists')}
        selected={filter.artistIds}
        options={shownArtists.map((a) => ({ value: a.id, label: a.name }))}
        onToggle={(id) => {
          onChange({ ...filter, artistIds: toggled(filter.artistIds, id) })
        }}
        scroll
        header={
          <Input
            type="search"
            aria-label={t('picker.artistSearch')}
            placeholder={t('picker.artistSearch')}
            value={artistQuery}
            onChange={(event) => {
              setArtistQuery(event.target.value)
            }}
          />
        }
        emptyText={t('picker.noArtist')}
      />
    </div>
  )
}

interface ChipGroupProps<T extends string | number> {
  legend: string
  options: { value: T; label: string }[]
  selected: T[]
  onToggle: (value: T) => void
  header?: ReactNode
  scroll?: boolean
  emptyText?: string
}

function ChipGroup<T extends string | number>({
  legend,
  options,
  selected,
  onToggle,
  header,
  scroll = false,
  emptyText,
}: ChipGroupProps<T>) {
  const { t } = useTranslation('solo')
  if (options.length === 0 && !header) return null
  const full = selected.length >= MAX_PER_GROUP
  return (
    <fieldset className="min-w-0 space-y-3">
      <legend className="text-base font-semibold">
        {legend}
        {selected.length > 0 ? (
          <span className="num ml-2 text-sm font-normal text-muted-foreground">
            {t('picker.selectedCount', { n: selected.length })}
          </span>
        ) : null}
      </legend>
      {header}
      {options.length === 0 ? (
        <p className="text-sm text-muted-foreground">{emptyText}</p>
      ) : (
        <div className={cn('flex flex-wrap gap-2', scroll && 'max-h-52 overflow-y-auto')}>
          {options.map((option) => {
            const on = selected.includes(option.value)
            return (
              <button
                key={option.value}
                type="button"
                aria-pressed={on}
                disabled={full && !on}
                onClick={() => {
                  onToggle(option.value)
                }}
                className={cn(
                  'mv-chip h-10 rounded-full px-4 text-sm font-medium transition-[background-color,transform] duration-150 hover:-translate-y-0.5 active:scale-[0.97] disabled:opacity-40 disabled:hover:translate-y-0 motion-reduce:transition-none motion-reduce:hover:translate-y-0 motion-reduce:active:scale-100',
                  on
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-raised text-foreground hover:bg-foreground/10',
                )}
              >
                {option.label}
              </button>
            )
          })}
        </div>
      )}
      {full ? (
        <p className="text-sm text-muted-foreground">{t('picker.limit', { n: MAX_PER_GROUP })}</p>
      ) : null}
    </fieldset>
  )
}

function PlaylistPanel({
  playlistId,
  onChange,
}: {
  playlistId: string | null
  onChange: (next: string | null) => void
}) {
  const { t, i18n } = useTranslation('solo')
  const playlists = usePlaylists()
  const vi = i18n.resolvedLanguage === 'vi'

  if (playlists.isPending) {
    return (
      <div aria-busy="true" className="space-y-2">
        {[0, 1, 2].map((row) => (
          <Skeleton key={row} className="h-16 rounded-2xl" />
        ))}
      </div>
    )
  }
  if (playlists.isError) {
    return (
      <ErrorState
        onRetry={() => {
          void playlists.refetch()
        }}
      />
    )
  }
  if (playlists.data.length === 0) return <EmptyState title={t('picker.noPlaylist')} />

  return (
    <ul className="space-y-2">
      {playlists.data.map((playlist) => {
        const on = playlist.id === playlistId
        return (
          <li key={playlist.id}>
            <button
              type="button"
              aria-pressed={on}
              onClick={() => {
                onChange(on ? null : playlist.id)
              }}
              className={cn(
                'flex min-h-16 w-full items-center justify-between gap-4 rounded-2xl px-5 py-3 text-left transition-colors motion-reduce:transition-none',
                on
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-raised text-foreground hover:bg-foreground/10',
              )}
            >
              <span className="flex min-w-0 items-center gap-4">
                <VinylDisc spinning={on} className="size-10 shrink-0" />
                <span className="text-base font-semibold">
                  {vi ? playlist.nameVi : playlist.nameEn}
                </span>
              </span>
              <span className="num shrink-0 text-sm">
                {t('picker.trackCount', { n: playlist.trackCount })}
              </span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}
