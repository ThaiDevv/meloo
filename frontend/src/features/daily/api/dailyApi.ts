import { useMutation, useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import { getAccessToken } from '@/shared/api/authStore'
import type { components } from '@/shared/api/schema.gen'
import { useDailyScope } from '@/shared/daily'
import { noteServerTime } from '../lib/clock'

type Schemas = components['schemas']

export type DailyToday = Schemas['DailyTodayResponse']
export type DailyRoundState = Schemas['DailyRoundState']
export type DailyResult = Schemas['DailyResult']
export type DailyGuessResponse = Schemas['DailyGuessResponse']

export const dailyTodayKey = (scope: string) => ['daily', scope, 'today'] as const

/** A failed call, with the HTTP status and the problem `code` when the server sent one. */
export class DailyApiError extends Error {
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

/** A 403 denies this operation, not the session. A failed refresh may also leave a valid session. */
export const isSignedOut = (error: unknown) =>
  error instanceof DailyApiError && error.status === 401 && getAccessToken() === null

export async function fetchDailyToday(): Promise<DailyToday> {
  const { data, error, response } = await apiClient.GET('/api/v1/daily/today')
  if (!data) throw new DailyApiError('getDailyToday', response.status, error)
  noteServerTime(data.serverTime)
  return data
}

/** Today's state for this player: not started, in progress (with the open round) or finished (with the result). */
export function useDailyToday() {
  const scope = useDailyScope()
  return useQuery({
    queryKey: dailyTodayKey(scope),
    // The page keeps its own copy while it is open (a refetch must not rewind it), and nothing
    // survives leaving it: coming back asks the server again, so the level and the clock are never
    // an old snapshot (BR-25).
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
    retry: false,
    queryFn: fetchDailyToday,
  })
}

/** The finished result of a day, with the five level slots the share text is built from (BR-25). */
export function useDailyResult(date: string) {
  const scope = useDailyScope()
  return useQuery({
    queryKey: ['daily', scope, 'result', date],
    staleTime: Infinity,
    gcTime: 0,
    retry: false,
    queryFn: async () => {
      const { data, error, response } = await apiClient.GET('/api/v1/daily/result', {
        params: { query: { date } },
      })
      if (!data) throw new DailyApiError('getDailyResult', response.status, error)
      return data
    },
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
      if (!data) throw new DailyApiError('searchTracks', response.status, error)
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

/** The calls of the one daily attempt. Scores, streaks and the answer all come back from the server. */
export function useDailyActions() {
  const start = useMutation({
    mutationFn: async () => {
      const { data, error, response } = await apiClient.POST('/api/v1/daily/today/start')
      if (!data) throw new DailyApiError('startDailyAttempt', response.status, error)
      noteServerTime(data.serverTime)
      return data
    },
  })

  const guess = useMutation({
    mutationFn: async (trackId: string) => {
      const { data, error, response } = await apiClient.POST('/api/v1/daily/today/guesses', {
        body: { trackId },
      })
      if (!data) throw new DailyApiError('submitDailyGuess', response.status, error)
      if (data.round) noteServerTime(data.round.serverTime)
      return data
    },
  })

  const listenMore = useMutation({
    mutationFn: async (expectedLevel: number) => {
      const { data, error, response } = await apiClient.POST('/api/v1/daily/today/listen-more', {
        params: { query: { expectedLevel } },
      })
      if (!data) throw new DailyApiError('listenMoreDaily', response.status, error)
      noteServerTime(data.serverTime)
      return data
    },
  })

  const skip = useMutation({
    mutationFn: async () => {
      const { data, error, response } = await apiClient.POST('/api/v1/daily/today/skip')
      if (!data) throw new DailyApiError('skipDaily', response.status, error)
      return data
    },
  })

  const busy = start.isPending || guess.isPending || listenMore.isPending || skip.isPending
  return { start, guess, listenMore, skip, busy }
}
