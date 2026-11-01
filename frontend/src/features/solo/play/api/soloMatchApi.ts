import { useMutation, useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import type { components } from '@/shared/api/schema.gen'

type Schemas = components['schemas']

export type SoloMatch = Schemas['SoloMatch']
export type MatchResult = Schemas['MatchResult']
export type RoundState = Schemas['RoundState']
export type RoundEnd = Schemas['RoundEnd']
export type GuessResponse = Schemas['GuessResponse']
export type SoloFilter = Schemas['SoloFilter']
export type CreateSoloMatchRequest = Schemas['CreateSoloMatchRequest']

/** A failed call, with the HTTP status and the problem `code` when the server sent one. */
export class SoloApiError extends Error {
  readonly status: number
  readonly code: string | undefined

  constructor(operation: string, status: number, problem: unknown) {
    const code =
      typeof problem === 'object' &&
      problem !== null &&
      'code' in problem &&
      typeof problem.code === 'string'
        ? problem.code
        : undefined
    super(`${operation} failed: ${String(status)}${code ? ` ${code}` : ''}`)
    this.status = status
    this.code = code
  }
}

/** What `/solo/:matchId` points at, decided from the server alone. */
export type MatchLoad =
  | { kind: 'active'; match: SoloMatch }
  | { kind: 'finished'; result: MatchResult }
  | { kind: 'other'; activeMatchId: string }
  | { kind: 'missing' }
  | { kind: 'signed-out' }

const isSignedOut = (status: number) => status === 401 || status === 403

/**
 * The caller's match in progress if it is this one (BR-23: resumes at the same round and level),
 * otherwise this match's result if it is over, otherwise whatever else is true.
 */
export async function loadSoloMatch(matchId: string): Promise<MatchLoad> {
  const active = await apiClient.GET('/api/v1/solo/matches/active')
  if (active.data?.matchId === matchId) return { kind: 'active', match: active.data }
  if (isSignedOut(active.response.status)) return { kind: 'signed-out' }
  if (!active.data && active.response.status !== 404) {
    throw new SoloApiError('getActiveSoloMatch', active.response.status, active.error)
  }

  const finished = await apiClient.GET('/api/v1/solo/matches/{matchId}/result', {
    params: { path: { matchId } },
  })
  if (finished.data) return { kind: 'finished', result: finished.data }
  const status = finished.response.status
  if (isSignedOut(status)) return { kind: 'signed-out' }
  // 400: not a match id at all. 404: not this user's match. 409: in progress, but not as theirs.
  if (status === 400 || status === 404 || status === 409) {
    return active.data ? { kind: 'other', activeMatchId: active.data.matchId } : { kind: 'missing' }
  }
  throw new SoloApiError('getMatchResult', status, finished.error)
}

export function useSoloMatchLoad(matchId: string) {
  return useQuery({
    queryKey: ['solo', 'match', matchId],
    queryFn: () => loadSoloMatch(matchId),
    // The screen keeps its own copy once play starts; a refetch must not rewind it.
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
    retry: false,
  })
}

/** Catalog-wide suggestions for the guess box (BR-05): they say nothing about the answer. */
export function useTrackSearch(query: string) {
  const q = query.trim()
  const search = useQuery({
    queryKey: ['catalog', 'tracks', 'search', q],
    queryFn: async () => {
      const { data, error, response } = await apiClient.GET('/api/v1/tracks/search', {
        params: { query: { q, size: 8 } },
      })
      if (!data) throw new SoloApiError('searchTracks', response.status, error)
      return data.items
    },
    enabled: q.length > 0,
    staleTime: 60_000,
  })
  return {
    suggestions: q.length > 0 ? (search.data ?? []) : [],
    loading: q.length > 0 && search.isFetching,
  }
}

/** The calls of one match. Every score and answer comes back from the server; none is computed here. */
export function useSoloMatchActions(matchId: string) {
  const path = (roundNo: number) => ({ path: { matchId, roundNo } })

  const guess = useMutation({
    mutationFn: async ({ roundNo, trackId }: { roundNo: number; trackId: string }) => {
      const { data, error, response } = await apiClient.POST(
        '/api/v1/solo/matches/{matchId}/rounds/{roundNo}/guesses',
        { params: path(roundNo), body: { trackId } },
      )
      if (!data) throw new SoloApiError('submitGuess', response.status, error)
      return data
    },
  })

  const listenMore = useMutation({
    mutationFn: async ({ roundNo, expectedLevel }: { roundNo: number; expectedLevel: number }) => {
      const { data, error, response } = await apiClient.POST(
        '/api/v1/solo/matches/{matchId}/rounds/{roundNo}/listen-more',
        { params: { ...path(roundNo), query: { expectedLevel } } },
      )
      if (!data) throw new SoloApiError('listenMore', response.status, error)
      return data
    },
  })

  const skip = useMutation({
    mutationFn: async ({ roundNo }: { roundNo: number }) => {
      const { data, error, response } = await apiClient.POST(
        '/api/v1/solo/matches/{matchId}/rounds/{roundNo}/skip',
        { params: path(roundNo) },
      )
      if (!data) throw new SoloApiError('skipRound', response.status, error)
      return data
    },
  })

  const currentRound = useMutation({
    mutationFn: async () => {
      const { data, error, response } = await apiClient.GET(
        '/api/v1/solo/matches/{matchId}/rounds/current',
        { params: { path: { matchId } } },
      )
      if (!data) throw new SoloApiError('getCurrentRound', response.status, error)
      return data
    },
  })

  const result = useMutation({
    mutationFn: async () => {
      const { data, error, response } = await apiClient.GET(
        '/api/v1/solo/matches/{matchId}/result',
        { params: { path: { matchId } } },
      )
      if (!data) throw new SoloApiError('getMatchResult', response.status, error)
      return data
    },
  })

  const abandon = useMutation({
    mutationFn: async () => {
      const { data, error, response } = await apiClient.POST(
        '/api/v1/solo/matches/{matchId}/abandon',
        { params: { path: { matchId } } },
      )
      if (!data) throw new SoloApiError('abandonSoloMatch', response.status, error)
      return data
    },
  })

  const busy =
    guess.isPending ||
    listenMore.isPending ||
    skip.isPending ||
    currentRound.isPending ||
    result.isPending ||
    abandon.isPending

  return { guess, listenMore, skip, currentRound, result, abandon, busy }
}
