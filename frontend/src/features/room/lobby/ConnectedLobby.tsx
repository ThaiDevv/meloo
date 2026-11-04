import { useRoomStore } from '@/shared/realtime'
import type { RoomResponse } from '@/shared/realtime/messages'
import { useAuthStore } from '@/shared/api/authStore'
import { RoomProblem } from './components/RoomProblem'
import { LiveLobby } from './LiveLobby'

/**
 * Shows the lobby, or the reason the player was sent out of it. Removing the lobby also drops its
 * socket, so a kicked player stops receiving the room's events at once.
 */
export function ConnectedLobby({ initialRoom }: { initialRoom: RoomResponse }) {
  const me = useAuthStore((state) => state.user?.id ?? null)
  const kicked = useRoomStore((state) => me !== null && state.kickedUserIds.includes(me))
  if (kicked) return <RoomProblem kind="kicked" />
  return <LiveLobby initialRoom={initialRoom} />
}
