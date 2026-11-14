import { UserX } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import type { RoomPlayerSummary } from '@/shared/realtime/messages'
import { Badge } from '@/shared/ui/Badge'
import { Button } from '@/shared/ui/Button'
import { PlayerDisc } from './PlayerDisc'

interface PlayerListProps {
  players: RoomPlayerSummary[]
  maxPlayers: number
  me: string | null
  /** Rooms with a host show who is ready; matchmaking rooms have no ready button (BR-12). */
  showReady: boolean
  /** Given only to the host: asks to remove that player. */
  onKick?: (player: RoomPlayerSummary) => void
}

function Status({ player, showReady }: { player: RoomPlayerSummary; showReady: boolean }) {
  const { t } = useTranslation('room')
  if (!player.isConnected)
    return <span className="font-semibold text-warning">{t('players.offline')}</span>
  if (!showReady || player.isHost) return null
  return player.isReady ? (
    <span className="font-semibold text-positive">{t('players.ready')}</span>
  ) : (
    <span className="text-muted-foreground">{t('players.notReady')}</span>
  )
}

/** Everyone in the room as a record on the deck, then empty seats up to the cap. */
export function PlayerList({ players, maxPlayers, me, showReady, onKick }: PlayerListProps) {
  const { t, i18n } = useTranslation('room')
  const emptySeats = Math.max(0, maxPlayers - players.length)
  return (
    <ul className="space-y-2">
      {players.map((player, index) => (
        <li
          key={player.userId}
          style={{ animationDelay: `${String(index * 60)}ms` }}
          className={cn(
            'motion-rise flex items-center gap-4 rounded-2xl px-4 py-3 sm:gap-5 sm:px-5 sm:py-4',
            player.userId === me ? 'bg-raised' : 'bg-surface',
          )}
        >
          <PlayerDisc
            name={player.nickname}
            src={player.avatarUrl ?? undefined}
            avatarId={player.avatarId}
            spinning={player.isConnected && (player.isReady || player.isHost)}
            away={!player.isConnected}
          />
          <div className="min-w-0 flex-1 space-y-1.5">
            <p className="flex items-baseline gap-2">
              <span className="truncate font-display text-2xl leading-tight font-extrabold tracking-[-0.02em] sm:text-3xl">
                {player.nickname}
              </span>
              {player.userId === me ? (
                <span className="shrink-0 text-sm text-muted-foreground">{t('players.you')}</span>
              ) : null}
            </p>
            <p className="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-base">
              {player.isHost ? <Badge tone="positive">{t('players.host')}</Badge> : null}
              {player.isGuest ? <Badge>{t('players.guest')}</Badge> : null}
              <Status player={player} showReady={showReady} />
              {player.score > 0 ? (
                <span className="num text-muted-foreground">
                  {t('players.score', { n: player.score.toLocaleString(i18n.resolvedLanguage) })}
                </span>
              ) : null}
            </p>
          </div>
          {onKick && player.userId !== me ? (
            <Button
              variant="ghost"
              size="icon"
              aria-label={t('players.kick', { name: player.nickname })}
              onClick={() => {
                onKick(player)
              }}
            >
              <UserX aria-hidden="true" className="size-5" strokeWidth={1.75} />
            </Button>
          ) : null}
        </li>
      ))}
      {Array.from({ length: emptySeats }, (_, index) => (
        <li
          key={`seat-${String(index)}`}
          className="flex items-center gap-4 px-4 py-2 sm:gap-5 sm:px-5"
        >
          <span
            aria-hidden="true"
            className="size-20 shrink-0 rounded-full bg-raised/60 sm:size-24"
          />
          <span className="text-lg text-muted-foreground">{t('players.emptySeat')}</span>
        </li>
      ))}
    </ul>
  )
}
