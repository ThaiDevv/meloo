import { useMutation } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import type { components } from '@/shared/api/schema.gen'
import type { RoomPlayerSummary, RoomResponse } from '@/shared/realtime/messages'

type Schemas = components['schemas']

export type CreateRoomBody = Schemas['CreateRoomRequest']

export type RoomFailure =
  | { reason: 'full' | 'kicked' | 'locked' | 'not-found' | 'playlist-not-found' }
  | { reason: 'not-enough-tracks'; missingTracks: number }
  | { reason: 'unauthorized' | 'rate-limited' | 'rejected' | 'unknown' }

export class RoomRequestError extends Error {
  readonly failure: RoomFailure

  constructor(failure: RoomFailure) {
    super(`room request failed: ${failure.reason}`)
    this.failure = failure
  }
}

function field(error: unknown, name: string): unknown {
  if (typeof error !== 'object' || error === null || !(name in error)) return undefined
  return Object.entries(error).find(([key]) => key === name)?.[1]
}

/** Reads the RFC 9457 problem of a failed create/join into the few cases the screens tell apart. */
export function failureOf(status: number, error: unknown): RoomRequestError['failure'] {
  const code = field(error, 'code')
  if (code === 'ROOM_FULL') return { reason: 'full' }
  if (code === 'ROOM_KICKED') return { reason: 'kicked' }
  if (code === 'ACCOUNT_LOCKED') return { reason: 'locked' }
  if (code === 'PLAYLIST_NOT_FOUND') return { reason: 'playlist-not-found' }
  if (code === 'ROOM_NOT_FOUND') return { reason: 'not-found' }
  if (code === 'NOT_ENOUGH_TRACKS') {
    const missing = field(error, 'missingTracks')
    return { reason: 'not-enough-tracks', missingTracks: typeof missing === 'number' ? missing : 0 }
  }
  if (status === 401) return { reason: 'unauthorized' }
  if (status === 429) return { reason: 'rate-limited' }
  if (status === 400) return { reason: 'rejected' }
  if (status === 409) return { reason: 'full' }
  if (status === 404) return { reason: 'not-found' }
  return { reason: 'unknown' }
}

function toPlayer(player: Schemas['RoomPlayerSummary']): RoomPlayerSummary {
  return { ...player, avatarUrl: player.avatarUrl ?? null }
}

/** The REST room leaves out null fields; the realtime model spells them out. */
export function toRoom(room: Schemas['RoomResponse']): RoomResponse {
  return {
    ...room,
    code: room.code ?? null,
    hostId: room.hostId ?? null,
    playlistId: room.playlistId ?? null,
    filter: room.filter ?? null,
    players: room.players.map(toPlayer),
  }
}

/** POST /rooms. The creator is already in the room and its host when it succeeds. */
export function useCreateRoom() {
  return useMutation({
    mutationFn: async (body: CreateRoomBody) => {
      const { data, error, response } = await apiClient.POST('/api/v1/rooms', { body })
      if (!data) throw new RoomRequestError(failureOf(response.status, error))
      return toRoom(data)
    },
  })
}

/** POST /rooms/join. In a room whose match is running the player waits in the lobby (BR-15). */
export function useJoinRoom() {
  return useMutation({
    mutationFn: async (code: string) => {
      const { data, error, response } = await apiClient.POST('/api/v1/rooms/join', {
        body: { code },
      })
      if (!data) throw new RoomRequestError(failureOf(response.status, error))
      return toRoom(data)
    },
  })
}
