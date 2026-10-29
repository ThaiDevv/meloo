import { useMutation } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import type { components } from '@/shared/api/schema.gen'

type CreateSoloMatchRequest = components['schemas']['CreateSoloMatchRequest']

export type CreateSoloMatchFailure =
  | { reason: 'in-progress'; matchId: string }
  | { reason: 'not-enough-tracks'; missingTracks: number }
  | { reason: 'unauthorized' | 'rate-limited' | 'rejected' | 'unknown' }

export class CreateSoloMatchError extends Error {
  readonly failure: CreateSoloMatchFailure

  constructor(failure: CreateSoloMatchFailure) {
    super(`createSoloMatch failed: ${failure.reason}`)
    this.failure = failure
  }
}

function failureOf(status: number, error: unknown): CreateSoloMatchFailure {
  if (status === 409 && typeof error === 'object' && error !== null && 'matchId' in error) {
    if (typeof error.matchId === 'string') return { reason: 'in-progress', matchId: error.matchId }
  }
  if (status === 422 && typeof error === 'object' && error !== null && 'missingTracks' in error) {
    if (typeof error.missingTracks === 'number') {
      return { reason: 'not-enough-tracks', missingTracks: error.missingTracks }
    }
  }
  if (status === 401 || status === 403) return { reason: 'unauthorized' }
  if (status === 429) return { reason: 'rate-limited' }
  if (status === 400) return { reason: 'rejected' }
  return { reason: 'unknown' }
}

/** POST /solo/matches. The first round is already open when it succeeds. */
export function useCreateSoloMatch() {
  return useMutation({
    mutationFn: async (body: CreateSoloMatchRequest) => {
      const { data, error, response } = await apiClient.POST('/api/v1/solo/matches', { body })
      if (!data) throw new CreateSoloMatchError(failureOf(response.status, error))
      return data
    },
  })
}
