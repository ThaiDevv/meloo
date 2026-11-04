import type { RoomPlayerSummary, RoomResponse } from '@/shared/realtime/messages'

export const MIN_PLAYERS_TO_START = 2
export const MAX_PLAYERS_LIMIT = 5

export type RoomRole = 'host' | 'member' | 'automatic'

/** BR-13: a matchmaking room has no host; nobody edits, kicks or starts, the countdown does. */
export function hasHost(room: RoomResponse): boolean {
  return room.hostId !== null
}

export function roleOf(room: RoomResponse, userId: string | null): RoomRole {
  if (!hasHost(room)) return 'automatic'
  return userId !== null && room.hostId === userId ? 'host' : 'member'
}

/**
 * BR-12: the host may start with at least two players and everyone ready. The host counts as ready
 * (they press "start"); a disconnected player cannot be waited for, so they are not counted.
 * Only a hint for the button: the server still decides and answers with the reason.
 */
export function canStart(room: RoomResponse): boolean {
  const here = room.players.filter((player) => player.isConnected)
  return (
    here.length >= MIN_PLAYERS_TO_START && here.every((player) => player.isReady || player.isHost)
  )
}

/** Who is still holding the start up, for the hint under the button. */
export function notReady(room: RoomResponse): RoomPlayerSummary[] {
  return room.players.filter((player) => player.isConnected && !player.isReady && !player.isHost)
}

/** A host cannot lower the cap below the players already inside. */
export function minimumMaxPlayers(room: RoomResponse): number {
  return Math.max(MIN_PLAYERS_TO_START, room.playerCount)
}
