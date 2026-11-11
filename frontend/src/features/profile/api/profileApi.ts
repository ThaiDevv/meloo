import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import type { components } from '@/shared/api/schema.gen'

type Schemas = components['schemas']

export type UserProfile = Schemas['UserProfile']
export type PlayerStats = Schemas['PlayerStats']
export type MatchHistoryPage = Schemas['MatchHistoryPage']
export type MatchHistoryItem = Schemas['MatchHistoryItem']
export type MatchHistoryDetail = Schemas['MatchHistoryDetail']
export type MatchHistoryRound = Schemas['MatchHistoryRound']
export type MatchHistoryPlayer = Schemas['MatchHistoryPlayer']

/** Matches per history page; the contract allows 1..50 and defaults to 10. */
export const HISTORY_PAGE_SIZE = 10

/** A failed call, with its HTTP status so the screen can tell "not found" from "try again". */
export class ProfileApiError extends Error {
  readonly status: number

  constructor(operation: string, status: number) {
    super(`${operation} failed: ${String(status)}`)
    this.status = status
  }
}

export const profileKeys = {
  all: ['profile'] as const,
  me: () => [...profileKeys.all, 'me'] as const,
  stats: () => [...profileKeys.all, 'stats'] as const,
  matches: (page: number) => [...profileKeys.all, 'matches', page] as const,
  match: (matchId: string) => [...profileKeys.all, 'match', matchId] as const,
}

export function useMyProfile() {
  return useQuery({
    queryKey: profileKeys.me(),
    queryFn: async (): Promise<UserProfile> => {
      const { data, response } = await apiClient.GET('/api/v1/me/profile')
      if (!data) throw new ProfileApiError('getMyProfile', response.status)
      return data
    },
  })
}

export function useMyStats() {
  return useQuery({
    queryKey: profileKeys.stats(),
    queryFn: async (): Promise<PlayerStats> => {
      const { data, response } = await apiClient.GET('/api/v1/me/stats')
      if (!data) throw new ProfileApiError('getMyStats', response.status)
      return data
    },
  })
}

/** One page of finished or abandoned matches, newest first. Keeps the old page on screen while the next loads. */
export function useMyMatches(page: number) {
  return useQuery({
    queryKey: profileKeys.matches(page),
    queryFn: async (): Promise<MatchHistoryPage> => {
      const { data, response } = await apiClient.GET('/api/v1/me/matches', {
        params: { query: { page, size: HISTORY_PAGE_SIZE } },
      })
      if (!data) throw new ProfileApiError('listMyMatches', response.status)
      return data
    },
    placeholderData: keepPreviousData,
  })
}

/**
 * One match of the caller, or `null` when it does not exist or is not theirs (404/403): that is an
 * answer to show, not a failure to retry.
 */
export function useMyMatchDetail(matchId: string) {
  return useQuery({
    queryKey: profileKeys.match(matchId),
    queryFn: async (): Promise<MatchHistoryDetail | null> => {
      const { data, response } = await apiClient.GET('/api/v1/me/matches/{matchId}', {
        params: { path: { matchId } },
      })
      if (data) return data
      if (response.status === 404 || response.status === 403) return null
      throw new ProfileApiError('getMyMatchDetail', response.status)
    },
  })
}
