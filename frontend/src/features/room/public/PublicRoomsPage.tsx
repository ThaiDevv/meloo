import { Plus, Swords, Users } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Badge } from '@/shared/ui/Badge'
import { Button } from '@/shared/ui/Button'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Skeleton } from '@/shared/ui/Skeleton'
import { usePublicRooms, type PublicRoom } from './api/publicRoomsApi'

function RoomRow({ room }: { room: PublicRoom }) {
  const { t } = useTranslation('room')
  const full = room.playerCount >= room.maxPlayers
  return (
    <li className="flex flex-wrap items-center gap-x-6 gap-y-3 rounded-2xl bg-surface p-5">
      <div className="min-w-0 flex-1 space-y-1">
        <p className="truncate text-lg font-bold">
          {room.playlistName ?? t('settings.music.wholeCatalog')}
        </p>
        <p className="text-sm text-muted-foreground">
          {t('public.host', { name: room.hostNickname })} ·{' '}
          {t('create.rounds.option', { n: room.roundCount })}
        </p>
      </div>
      <p className="num flex items-center gap-2 font-semibold">
        <Users aria-hidden="true" className="size-4" />
        <span aria-label={t('public.players', { n: room.playerCount, max: room.maxPlayers })}>
          {room.playerCount}/{room.maxPlayers}
        </span>
        {full ? <Badge tone="warning">{t('public.full')}</Badge> : null}
      </p>
      {full ? (
        <Button disabled>{t('public.join')}</Button>
      ) : (
        <Button asChild>
          <Link
            to={`/r/${room.code}`}
            aria-label={t('public.joinNamed', { name: room.hostNickname })}
          >
            {t('public.join')}
          </Link>
        </Button>
      )}
    </li>
  )
}

function RoomListSkeleton() {
  const { t } = useTranslation('room')
  return (
    <div aria-busy="true" role="status" aria-label={t('public.loading')} className="space-y-3">
      {Array.from({ length: 3 }, (_, i) => (
        <Skeleton key={i} className="h-20 w-full rounded-2xl" />
      ))}
    </div>
  )
}

/** /rooms: public rooms still waiting in their lobby, refreshed by itself, and the quick match entry. */
export function PublicRoomsPage() {
  const { t } = useTranslation('room')
  const rooms = usePublicRooms()
  // Only rooms people can still enter before a match: the list also holds the ones already playing.
  const waiting = rooms.data?.filter((room) => room.status === 'LOBBY') ?? []

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-10 lg:py-14">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="max-w-xl space-y-3">
          <h1 className="font-display text-[clamp(2.5rem,8vw,4.5rem)] leading-none font-extrabold tracking-[-0.03em] text-balance">
            {t('public.title')}
          </h1>
          <p className="text-lg">{t('public.intro')}</p>
        </div>
        <div className="flex flex-wrap gap-3">
          <Button asChild>
            <Link to="/quick-match">
              <Swords aria-hidden="true" className="size-5" />
              {t('public.quickMatch')}
            </Link>
          </Button>
          <Button asChild variant="outline">
            <Link to="/rooms/new">
              <Plus aria-hidden="true" className="size-5" />
              {t('public.create')}
            </Link>
          </Button>
        </div>
      </header>

      <section aria-labelledby="public-list-heading" className="mt-10 space-y-4">
        <h2 id="public-list-heading" className="font-display text-2xl font-extrabold">
          {t('public.waiting')}
        </h2>
        {rooms.isPending ? (
          <RoomListSkeleton />
        ) : rooms.isError && !rooms.data ? (
          <ErrorState
            onRetry={() => {
              void rooms.refetch()
            }}
          />
        ) : waiting.length === 0 ? (
          <EmptyState
            title={t('public.empty.title')}
            description={t('public.empty.description')}
            action={
              <Button asChild variant="outline">
                <Link to="/rooms/new">{t('public.create')}</Link>
              </Button>
            }
          />
        ) : (
          <ul className="space-y-3">
            {waiting.map((room) => (
              <RoomRow key={room.roomId} room={room} />
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
