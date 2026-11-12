import { useMutation, useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import type { components } from '@/shared/api/schema.gen'
import { RoomRequestError, failureOf, toRoom, type RoomFailure } from '../../lobby/api/roomApi'

type Schemas = components['schemas']

export type PublicRoom = Schemas['PublicRoomItem']
export type QuickMatchBody = Schemas['QuickMatchRequest']

/** How often the list of waiting rooms asks again (AC1: it refreshes by itself). */
export const PUBLIC_ROOMS_REFRESH_MS = 5_000

/** GET /rooms/public: the public rooms people made, polled while the page is open. */
export function usePublicRooms() {
  return useQuery({
    queryKey: ['rooms', 'public'],
    queryFn: async () => {
      const { data, response } = await apiClient.GET('/api/v1/rooms/public', {
        params: { query: { size: 50 } },
      })
      if (!data) throw new Error(`listPublicRooms failed: ${String(response.status)}`)
      return data.items
    },
    refetchInterval: PUBLIC_ROOMS_REFRESH_MS,
  })
}

export type QuickMatchFailure = RoomFailure | { reason: 'requires-account' }

function quickMatchFailure(status: number, error: unknown): QuickMatchFailure {
  const code =
    typeof error === 'object' && error !== null && 'code' in error ? error.code : undefined
  if (code === 'QUICK_MATCH_REQUIRES_ACCOUNT') return { reason: 'requires-account' }
  return failureOf(status, error)
}

export class QuickMatchError extends Error {
  readonly failure: QuickMatchFailure

  constructor(failure: QuickMatchFailure) {
    super(`quick match failed: ${failure.reason}`)
    this.failure = failure
  }
}

export function failureOfError(error: unknown): QuickMatchFailure {
  if (error instanceof QuickMatchError) return error.failure
  if (error instanceof RoomRequestError) return error.failure
  return { reason: 'unknown' }
}

/** POST /rooms/quick-match: joins a matchmaking room (hostless, no code) or opens a new one. */
export function useQuickMatch() {
  return useMutation({
    mutationFn: async (body: QuickMatchBody) => {
      const { data, error, response } = await apiClient.POST('/api/v1/rooms/quick-match', {
        body,
      })
      if (!data) throw new QuickMatchError(quickMatchFailure(response.status, error))
      return toRoom(data)
    },
  })
}
