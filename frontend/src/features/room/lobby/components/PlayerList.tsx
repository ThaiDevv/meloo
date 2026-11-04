import { UserX } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { RoomPlayerSummary } from '@/shared/realtime/messages'
import { Avatar } from '@/shared/ui/Avatar'
import { Badge } from '@/shared/ui/Badge'
import { Button } from '@/shared/ui/Button'

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
  if (!player.isConnected) return <Badge tone="warning">{t('players.offline')}</Badge>
  if (!showReady || player.isHost) return null
  return player.isReady ? (
    <Badge tone="positive">{t('players.ready')}</Badge>
  ) : (
    <Badge>{t('players.notReady')}</Badge>
  )
}

/** Everyone in the room, then dashed seats up to the cap so the room's size is visible. */
export function PlayerList({ players, maxPlayers, me, showReady, onKick }: PlayerListProps) {
  const { t } = useTranslation('room')
  const emptySeats = Math.max(0, maxPlayers - players.length)
  return (
    <ul className="divide-y divide-border">
      {players.map((player) => (
        <li key={player.userId} className="flex min-h-14 items-center gap-3 py-2">
          <Avatar name={player.nickname} src={player.avatarUrl ?? undefined} />
          <div className="min-w-0 flex-1">
            <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <span className="truncate font-semibold">{player.nickname}</span>
              {player.userId === me ? (
                <span className="text-sm text-muted-foreground">{t('players.you')}</span>
              ) : null}
              {player.isHost ? <Badge tone="positive">{t('players.host')}</Badge> : null}
              {player.isGuest ? <Badge>{t('players.guest')}</Badge> : null}
            </p>
          </div>
          <Status player={player} showReady={showReady} />
          {onKick && player.userId !== me ? (
            <Button
              variant="ghost"
              size="icon"
              aria-label={t('players.kick', { name: player.nickname })}
              onClick={() => {
                onKick(player)
              }}
            >
              <UserX aria-hidden="true" className="size-[18px]" strokeWidth={1.75} />
            </Button>
          ) : null}
        </li>
      ))}
      {Array.from({ length: emptySeats }, (_, index) => (
        <li
          key={`seat-${String(index)}`}
          className="flex min-h-14 items-center gap-3 py-2 text-muted-foreground"
        >
          <span
            aria-hidden="true"
            className="size-10 shrink-0 rounded-full border border-dashed border-control"
          />
          <span className="text-sm">{t('players.emptySeat')}</span>
        </li>
      ))}
    </ul>
  )
}
